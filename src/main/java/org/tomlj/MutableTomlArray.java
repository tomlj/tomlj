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

import java.util.Arrays;
import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * A {@link TomlArray} whose values can be edited in place.
 *
 * <p>
 * Accepts the values {@link MutableTomlTable} accepts, converted the same way. A {@link TomlTable} or {@link TomlArray}
 * stored as a value is stored as a deep copy, so later changes to the original are not seen; the stored copy is edited
 * through the getters that return it. {@code null} throws a {@link NullPointerException}.
 *
 * <p>
 * A key or a {@link String} value must not contain an unpaired surrogate, an {@link java.time.OffsetDateTime}'s offset
 * must be a whole number of minutes, and a {@link java.time.LocalDate}, {@link java.time.LocalDateTime} or
 * {@link java.time.OffsetDateTime} must have a year between 0 and 9999, since none of these can be written as TOML.
 *
 * <p>
 * An entry's attached comments are edited through its {@link MutableTomlEntry} obtained from {@link #entry}, or through
 * the shortcuts here. An unattached comment is added after the last element with {@link #addComment} and removed with
 * {@link #removeComment}.
 *
 * <p>
 * An array read from {@code [[x]]} headers accepts any value; nothing requires its entries to stay tables.
 *
 * <p>
 * Not safe for use from multiple threads without external synchronization.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface MutableTomlArray extends TomlArray {

  /**
   * Create a new, empty array.
   *
   * @return A new, empty array.
   */
  static MutableTomlArray create() {
    return new ListTomlArray(false, null);
  }

  /**
   * Create an array from a sequence of values.
   *
   * @param values The values, each converted as {@link #add(Object)} converts one.
   * @return A new array with one entry per value.
   * @throws NullPointerException If a value is {@code null}.
   * @throws IllegalArgumentException If a value cannot be converted to a TOML value, or cannot be written as TOML.
   */
  static MutableTomlArray of(Object... values) {
    MutableTomlArray array = create();
    for (Object value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Append a value to this array.
   *
   * <p>
   * The new entry has no position and no comments. A table or array is stored as a deep copy, made as
   * {@link #copyOf(TomlArray)} makes one, so it has no positions.
   *
   * @param value The value to add.
   * @return This array.
   * @throws NullPointerException If {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value, or cannot be written as
   *         TOML.
   */
  MutableTomlArray add(Object value);

  /**
   * Replace the value at an index.
   *
   * <p>
   * The entry keeps its place in the array and its attached comments. Its position is its value's, and a value set here
   * has none, so {@link #inputPositionOf(int)} is {@code null} for the index afterwards. A table or array is stored as
   * a deep copy, made as {@link #copyOf(TomlArray)} makes one.
   *
   * @param index The array index.
   * @param value The replacement value.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value, or cannot be written as
   *         TOML.
   */
  MutableTomlArray set(int index, Object value);

  /**
   * Remove the value at an index.
   *
   * <p>
   * The entry is removed, with the comments attached to it, and every later entry moves down by one. The unattached
   * comments among this array's elements stay where they are.
   *
   * @param index The array index.
   * @return The value that was removed.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  Object remove(int index);

  /**
   * Remove every entry from this array.
   *
   * <p>
   * The unattached comments among this array's elements stay where they are. Clearing an array that is already empty is
   * not a modification.
   */
  void clear();

  /**
   * Whether this array was changed through this interface.
   *
   * <p>
   * A change is an entry added, replaced or removed, in this array or in any table or array nested within it. An array
   * read from a document, or newly created, reports {@code false}; nothing resets this once it is {@code true}.
   *
   * @return {@code true} if this array was changed through this interface.
   */
  boolean isModified();

  /**
   * Whether an entry of this array was changed through this interface.
   *
   * <p>
   * A change is the entry added or replaced, or a change within the table or array it holds.
   *
   * @param index The array index.
   * @return {@code true} if the entry was changed.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  boolean isModified(int index);

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(String...)} does.
   *
   * @param index The array index.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it; a line holding a newline is
   *        split there.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlArray setCommentAbove(int index, String... lines) {
    return setCommentAbove(index, Arrays.asList(lines));
  }

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(List)} does.
   *
   * @param index The array index.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it; a line holding a newline is
   *        split there.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code lines}, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlArray setCommentAbove(int index, List<String> lines) {
    entry(index).setCommentAbove(lines);
    return this;
  }

  /**
   * Set the comment written on an entry's line, as {@link MutableTomlEntry#setCommentAfter(String)} does.
   *
   * @param index The array index.
   * @param text The comment text, as {@link TomlComment#lines()} would return its one line.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code text} is {@code null}.
   * @throws IllegalArgumentException If {@code text} cannot be written as a TOML comment.
   */
  default MutableTomlArray setCommentAfter(int index, String text) {
    entry(index).setCommentAfter(text);
    return this;
  }

  /**
   * Set an attached comment on an entry from its text, as
   * {@link MutableTomlEntry#setComment(String, TomlComment.Placement)} does.
   *
   * @param index The array index.
   * @param text The comment's text, as {@link TomlComment#text()} would return it.
   * @param placement Where the comment sits relative to the entry, {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code text} or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}, or a line of
   *         {@code text} cannot be written as a TOML comment.
   */
  default MutableTomlArray setComment(int index, String text, TomlComment.Placement placement) {
    entry(index).setComment(text, placement);
    return this;
  }

  /**
   * Set an attached comment on an entry, as {@link MutableTomlEntry#setComment(TomlComment)} does.
   *
   * @param index The array index.
   * @param comment The comment, with {@link TomlComment#placement()} either {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is unattached.
   */
  default MutableTomlArray setComment(int index, TomlComment comment) {
    entry(index).setComment(comment);
    return this;
  }

  /**
   * Remove the run of comment lines written above an entry, as {@link MutableTomlEntry#removeCommentAbove()} does.
   *
   * @param index The array index.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default MutableTomlArray removeCommentAbove(int index) {
    entry(index).removeCommentAbove();
    return this;
  }

  /**
   * Remove the comment written on an entry's line, as {@link MutableTomlEntry#removeCommentAfter()} does.
   *
   * @param index The array index.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default MutableTomlArray removeCommentAfter(int index) {
    entry(index).removeCommentAfter();
    return this;
  }

  /**
   * Remove the attached comment at a placement, as {@link MutableTomlEntry#removeComment(TomlComment.Placement)} does.
   *
   * @param index The array index.
   * @param placement Which attached comment to remove, {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}.
   */
  default MutableTomlArray removeComment(int index, TomlComment.Placement placement) {
    entry(index).removeComment(placement);
    return this;
  }

  /**
   * Add an unattached comment, after the elements already written, as {@link #addComment(List)} does.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it; a line holding a newline is
   *        split there.
   * @return This array.
   * @throws NullPointerException If a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlArray addComment(String... lines) {
    return addComment(Arrays.asList(lines));
  }

  /**
   * Add an unattached comment, after the elements already written.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it; a line holding a newline is
   *        split there.
   * @return This array.
   * @throws NullPointerException If {@code lines}, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlArray addComment(List<String> lines) {
    return addComment(TomlComment.ofLines(lines, TomlComment.Placement.UNATTACHED));
  }

  /**
   * Add a comment, after the elements already written.
   *
   * <p>
   * The comment's position is ignored: the copy stored here has none. This is how an unattached comment is copied from
   * one table or array to another, across documents too.
   *
   * @param comment The comment to add, with no placement.
   * @return This array.
   * @throws NullPointerException If {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is attached.
   */
  MutableTomlArray addComment(TomlComment comment);

  /**
   * Remove an unattached comment, by identity.
   *
   * @param comment The comment to remove.
   * @return {@code true} if {@code comment} was among this array's {@link #elements()}, and was removed. An attached
   *         comment is never among them, so removing one here always returns {@code false}.
   */
  boolean removeComment(TomlComment comment);

  /**
   * Create a deep copy of an array.
   *
   * <p>
   * The copy is independent of {@code array}: a nested table or array is copied recursively, and later changes to
   * either are not seen by the other. Every entry of the copy has no input position and reports as modified, since none
   * of it was read from a document; the copy itself reports {@link #isModified()} {@code false} while it has no
   * entries, like {@link #create()}. The comments attached to each entry, and the unattached comments among the array's
   * elements, are kept.
   *
   * @param array The array to copy.
   * @return A new, independent array with the same entries.
   * @throws IllegalArgumentException If a value in {@code array} cannot be written as TOML.
   */
  static MutableTomlArray copyOf(TomlArray array) {
    requireNonNull(array);
    if (array instanceof ListTomlArray) {
      return ((ListTomlArray) array).copy();
    }
    return ListTomlArray.copyFrom(array);
  }

  /**
   * Create an array from an {@link Iterable}.
   *
   * <p>
   * Any {@link Iterable} is accepted here, not only a {@link java.util.Collection} as when one is set as a value: an
   * {@link Iterable} that is not a {@link java.util.Collection} is rejected as a value, and is converted to an array by
   * passing it here.
   *
   * @param values The values, each converted as {@link #add(Object)} converts one.
   * @return A new array with one entry per value.
   * @throws NullPointerException If a value is {@code null}.
   * @throws IllegalArgumentException If a value cannot be converted to a TOML value, or cannot be written as TOML.
   */
  static MutableTomlArray copyOf(Iterable<?> values) {
    requireNonNull(values);
    MutableTomlArray array = create();
    for (Object value : values) {
      array.add(value);
    }
    return array;
  }

  @Override
  MutableTomlEntry entry(int index);

  @Override
  default MutableTomlTable getTable(int index) {
    return (MutableTomlTable) TomlArray.super.getTable(index);
  }

  @Override
  default MutableTomlArray getArray(int index) {
    return (MutableTomlArray) TomlArray.super.getArray(index);
  }
}
