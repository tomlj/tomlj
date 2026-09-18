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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An entry of a parsed table or array: what its sequence holds besides unattached comments.
 *
 * <p>
 * A document is a tree: the root table holds a sequence of entries and unattached comments, and any entry whose value
 * is itself a table or array ({@link ElementContainer}) holds a sequence of its own. A table's entries are
 * {@link KeyValue}s, an array's entries {@link Indexed}s; the unattached comments are the {@link TomlComment} objects
 * themselves. Both are {@link TomlElement}s, which is what {@link ElementContainer#elements()} lists.
 *
 * <p>
 * A container's unattached comments are among its {@link ElementContainer#elements()}; an entry's own comments, the run
 * above it and the comment after it, are its {@code comments()}. The value hierarchy ({@link Value},
 * {@link Value.Scalar}, {@link ElementContainer}) is the data an entry holds; a value never carries comments of its
 * own.
 *
 * <p>
 * The editing API records where a change happened, and never resets it: {@link #valueModified} when this entry was
 * added, or its value replaced, through the API, and {@link #commentsModified} when an attached comment was set or
 * removed. {@link #isModified()} folds both, and any modification nested in the value itself, into whether this entry
 * was modified.
 */
abstract class Entry implements MutableTomlEntry {

  // Not final: the editing API's set() and its array equivalent replace the value of an existing entry in place,
  // keeping its position, its place in the container's sequence and its attached comments; see #replace.
  Value value;

  // Not final: a table header such as [a] can define a table that an earlier dotted key created implicitly, and the
  // pair then takes over the header's attached comments, in place; see KeyValue#define. Also replaced, in place, by
  // the editing API's comment setters and removers.
  private List<TomlComment> attachedComments;

  // Whether this entry was added, or had its value replaced, through the editing API, rather than read from a parsed
  // document.
  boolean valueModified;

  // Whether an attached comment of this entry was set or removed through the editing API.
  private boolean commentsModified;

  // Where this entry was written in the document it was read from: a LINE span for a key/value pair written as a line
  // of a section, an ELEMENT span for an element of an array or an entry of an inline table. Null for an entry added
  // through the editing API, and for one read from a document parsed with no source kept. A copy of an entry shares
  // the span, which names its own source.
  @Nullable
  SourceSpan span;

  Entry(Value value, List<TomlComment> attachedComments) {
    this.value = value;
    this.attachedComments = attachedComments;
  }

  @Override
  public TomlValue value() {
    return value;
  }

  @Override
  public List<TomlComment> comments() {
    return attachedComments;
  }

  /**
   * Replace this entry's value, keeping its position, its place in the container's sequence and its attached comments.
   *
   * @param newValue The replacement value.
   */
  void replace(Value newValue) {
    value = newValue;
    valueModified = true;
  }

  @Override
  public Object setValue(Object newValue) {
    Object normalized = TomlValues.normalize(newValue);
    Object previous = value.get();
    replace(Value.of(normalized, null));
    return previous;
  }

  @Override
  public MutableTomlEntry setComment(String text, TomlComment.Placement placement) {
    requireNonNull(text);
    TomlComment.requireAttached(placement);
    return updateAttachedComment(placement, TomlComment.ofLines(Collections.singletonList(text), placement));
  }

  @Override
  public MutableTomlEntry setComment(TomlComment comment) {
    TomlComment.Placement placement = comment.placement();
    if (placement == TomlComment.Placement.UNATTACHED) {
      throw new IllegalArgumentException("comment must have a placement of ABOVE or AFTER");
    }
    return updateAttachedComment(placement, comment.withoutPosition());
  }

  @Override
  public MutableTomlEntry removeCommentAbove() {
    return removeComment(TomlComment.Placement.ABOVE);
  }

  @Override
  public MutableTomlEntry removeCommentAfter() {
    return removeComment(TomlComment.Placement.AFTER);
  }

  @Override
  public MutableTomlEntry removeComment(TomlComment.Placement placement) {
    TomlComment.requireAttached(placement);
    return (comment(placement) == null) ? this : updateAttachedComment(placement, null);
  }

  // Replace the attached comment at a placement with comment, or remove it for null, keeping the other one.
  private MutableTomlEntry updateAttachedComment(TomlComment.Placement placement, @Nullable TomlComment comment) {
    TomlComment above = (placement == TomlComment.Placement.ABOVE) ? comment : comment(TomlComment.Placement.ABOVE);
    TomlComment after = (placement == TomlComment.Placement.AFTER) ? comment : comment(TomlComment.Placement.AFTER);
    attachedComments = TomlComment.withoutNulls(above, after);
    commentsModified = true;
    return this;
  }

  /**
   * Whether this entry was changed through the editing API, either directly or by a change nested within its value.
   *
   * @return {@code true} if this entry was added, had its value replaced, had an attached comment set or removed, or
   *         holds a table or array that was itself changed, through the editing API.
   */
  @Override
  public boolean isModified() {
    return valueModified
        || commentsModified
        || (value instanceof ElementContainer && ((ElementContainer<?>) value).isModified());
  }

  /**
   * A key/value pair written in a table.
   */
  static final class KeyValue extends Entry implements MutableTomlKeyValue {

    // The single key in its table, not a dotted path.
    final String key;

    // Not final: a table header such as [a] can define a table that an earlier dotted key created implicitly, and
    // this pair then takes over the header's position and attached comments, in place, so it keeps its spot in the
    // table's sequence rather than being replaced by a new entry. Nullable: an entry added through the editing API has
    // no input position, and a table created implicitly by a dotted key has none until a later header defines it.
    private @Nullable TomlPosition position;

    KeyValue(String key, Value value, @Nullable TomlPosition position, List<TomlComment> attachedComments) {
      super(value, attachedComments);
      this.key = key;
      this.position = position;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    @Nullable
    public TomlPosition position() {
      return position;
    }

    /**
     * Redefine this pair's position and attached comments, in place, keeping its spot in the table's sequence.
     *
     * @param position The new position.
     * @param attachedComments The new attached comments.
     */
    void define(TomlPosition position, List<TomlComment> attachedComments) {
      this.position = position;
      super.attachedComments = attachedComments;
    }
  }

  /**
   * An entry of an array: an indexed value.
   */
  static final class Indexed extends Entry {

    Indexed(Value value, List<TomlComment> attachedComments) {
      super(value, attachedComments);
    }

    /**
     * The position of this entry: where its value is written, since an array entry is written where its value is,
     * including the {@code [[x]]} header of a table in an array of tables.
     *
     * @return The position, or {@code null} if this entry's value has none; see {@link Value#position()}.
     */
    @Override
    @Nullable
    public TomlPosition position() {
      return value.position();
    }
  }
}
