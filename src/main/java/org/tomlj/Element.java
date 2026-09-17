/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import java.util.Collections;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * One node of a parsed TOML document.
 *
 * <p>
 * A document is a tree: the root table holds a sequence of entries and unattached comments, and any entry that is
 * itself a table or array ({@link ElementContainer}) holds a sequence of its own. Every node of the tree is an
 * {@code Element}: an {@link Entry} in a container's sequence, or an unattached {@link Comment}.
 */
abstract class Element {

  /**
   * Where this element was written in the document.
   *
   * @return The position, or {@code null} if this element has none yet: a table created implicitly by a dotted key,
   *         before a header defines it.
   */
  @Nullable
  abstract TomlPosition position();

  /**
   * An unattached comment: one in a table or array that belongs to no entry.
   */
  static final class Comment extends Element {

    final TomlComment comment;

    Comment(TomlComment comment) {
      this.comment = comment;
    }

    @Override
    @Nullable
    TomlPosition position() {
      return comment.position();
    }
  }

  /**
   * What a table or array holds besides unattached comments: a key/value pair for a table, or a value for an array.
   *
   * <p>
   * {@link ElementContainer#comments()} is the unattached comments of a container; an entry's own comments, the run
   * above it and the comment after it, are {@link #attachedComments()}, so the two never share a name.
   */
  abstract static class Entry extends Element {

    /**
     * The comments attached to this entry, in document order: the run above it, then the comment after it.
     *
     * @return The attached comments, in document order. Unmodifiable.
     */
    abstract List<TomlComment> attachedComments();
  }

  /**
   * A key/value pair written in a table.
   */
  static final class KeyValue extends Entry {

    // The single key in its table, not a dotted path.
    final String key;
    final Value value;

    // Not final: a table header such as [a] can define a table that an earlier dotted key created implicitly, and
    // this pair then takes over the header's position and attached comments, in place, so it keeps its spot in the
    // table's sequence rather than being replaced by a new element.
    private TomlPosition position;
    private List<TomlComment> attachedComments;

    KeyValue(String key, Value value, TomlPosition position, List<TomlComment> attachedComments) {
      this.key = key;
      this.value = value;
      this.position = position;
      this.attachedComments = attachedComments;
    }

    @Override
    TomlPosition position() {
      return position;
    }

    @Override
    List<TomlComment> attachedComments() {
      return attachedComments;
    }

    /**
     * Redefine this pair's position and attached comments, in place, keeping its spot in the table's sequence.
     *
     * @param position The new position.
     * @param attachedComments The new attached comments.
     */
    void define(TomlPosition position, List<TomlComment> attachedComments) {
      this.position = position;
      this.attachedComments = attachedComments;
    }
  }

  /**
   * Anything a key or an array slot holds: a scalar, a table or an array.
   */
  abstract static class Value extends Entry {

    // The comments attached to this value in an array; empty for a value under a key in a table, whose comments
    // belong to the KeyValue (see MutableTomlTable#put). Not final: a nested table or array is built by the visitor
    // before the comments around it in the enclosing array are known, so they are attached afterwards; see #attach.
    private List<TomlComment> attachedComments = Collections.emptyList();

    @Override
    List<TomlComment> attachedComments() {
      return attachedComments;
    }

    /**
     * Attach the comments on this value in an array. Called once, from {@link #of(Object, TomlPosition, List)}, before
     * the array places the value.
     *
     * @param attachedComments The comments.
     */
    private void attach(List<TomlComment> attachedComments) {
      this.attachedComments = attachedComments;
    }

    /**
     * The value as the public API exposes it: the wrapped scalar, or the table or array itself.
     *
     * @return The value.
     */
    abstract Object get();

    /**
     * Wrap a value read from the document as the element a table or array can place in its sequence.
     *
     * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link MutableTomlTable} /
     *        {@link MutableTomlArray}, which is already a {@link Value}.
     * @param position The position of the value in the document.
     * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it.
     */
    static Value of(Object value, TomlPosition position) {
      return (value instanceof Value) ? (Value) value : new Scalar(value, position);
    }

    /**
     * Wrap a value read from an array as an element the array can place in its sequence, with its comments attached.
     *
     * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link MutableTomlTable} /
     *        {@link MutableTomlArray}, which is already a {@link Value}.
     * @param position The position of the value in the document.
     * @param attachedComments The comments attached to the value in the array.
     * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it, with
     *         the comments attached either way.
     */
    static Value of(Object value, TomlPosition position, List<TomlComment> attachedComments) {
      Value element = of(value, position);
      element.attach(attachedComments);
      return element;
    }
  }

  /**
   * A scalar value: a string, integer, float, boolean or date/time.
   */
  static final class Scalar extends Value {

    private final Object value;
    private final TomlPosition position;

    Scalar(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }

    @Override
    Object get() {
      return value;
    }

    @Override
    TomlPosition position() {
      return position;
    }
  }
}
