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

/**
 * An entry of a parsed table or array: what its sequence holds besides unattached comments.
 *
 * <p>
 * A document is a tree: the root table holds a sequence of entries and unattached comments, and any entry that is
 * itself a table or array ({@link ElementContainer}) holds a sequence of its own. The entries are a {@link KeyValue}
 * for a table and a {@link Value} for an array; the unattached comments are the {@link TomlComment} objects themselves.
 * Both are {@link TomlElement}s, which is what {@link ElementContainer#elements()} lists.
 *
 * <p>
 * A container's unattached comments are among its {@link ElementContainer#elements()}; an entry's own comments, the run
 * above it and the comment after it, are its {@code comments()}. A pair is not a value, so the two kinds of entry share
 * no public type: a pair is a {@link TomlKeyValue} and an array's value a {@link TomlValue}.
 */
abstract class Entry implements TomlElement {

  /**
   * A key/value pair written in a table.
   */
  static final class KeyValue extends Entry implements TomlKeyValue {

    // The single key in its table, not a dotted path.
    final String key;
    final Value value;

    // Not final: a table header such as [a] can define a table that an earlier dotted key created implicitly, and
    // this pair then takes over the header's position and attached comments, in place, so it keeps its spot in the
    // table's sequence rather than being replaced by a new entry.
    private TomlPosition position;
    private List<TomlComment> attachedComments;

    KeyValue(String key, Value value, TomlPosition position, List<TomlComment> attachedComments) {
      this.key = key;
      this.value = value;
      this.position = position;
      this.attachedComments = attachedComments;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public TomlValue value() {
      return value;
    }

    @Override
    public TomlPosition position() {
      return position;
    }

    @Override
    public List<TomlComment> comments() {
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
  abstract static class Value extends Entry implements TomlValue {

    // The comments attached to this value in an array; empty for a value under a key in a table, whose comments
    // belong to the KeyValue (see LinkedTomlTable#put). Not final: a nested table or array is built by the visitor
    // before the comments around it in the enclosing array are known, so they are attached afterwards; see #attach.
    private List<TomlComment> attachedComments = Collections.emptyList();

    @Override
    public List<TomlComment> comments() {
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
    @Override
    public abstract Object get();

    /**
     * Wrap a value read from the document as the entry a table or array can place in its sequence.
     *
     * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link LinkedTomlTable} /
     *        {@link ListTomlArray}, which is already a {@link Value}.
     * @param position The position of the value in the document.
     * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it.
     */
    static Value of(Object value, TomlPosition position) {
      return (value instanceof Value) ? (Value) value : new Scalar(value, position);
    }

    /**
     * Wrap a value read from an array as the entry the array can place in its sequence, with its comments attached.
     *
     * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link LinkedTomlTable} /
     *        {@link ListTomlArray}, which is already a {@link Value}.
     * @param position The position of the value in the document.
     * @param attachedComments The comments attached to the value in the array.
     * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it, with
     *         the comments attached either way.
     */
    static Value of(Object value, TomlPosition position, List<TomlComment> attachedComments) {
      Value entry = of(value, position);
      entry.attach(attachedComments);
      return entry;
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
    public Object get() {
      return value;
    }

    @Override
    public TomlPosition position() {
      return position;
    }
  }
}
