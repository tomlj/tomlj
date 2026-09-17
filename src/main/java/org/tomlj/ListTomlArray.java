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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

class ListTomlArray extends ElementContainer<Entry.Indexed> implements MutableTomlArray {

  // The entries of this array, in document order; the index into this list is the array index. This is a separate
  // index over the same entries that elements() holds, kept for lookup by position.
  private final List<Entry.Indexed> entries = new ArrayList<>();
  private final boolean isTableArray;

  // Final, unlike a table's: an array only ever comes from a literal written in the document, at a position known
  // as soon as it is created, whereas a table can also be created implicitly by a dotted key and defined later.
  // Nullable: an array created through the editing API has no input position.
  private final @Nullable TomlPosition position;

  /**
   * Create an array for an array written in a document, or through the editing API.
   *
   * @param isTableArray {@code true} if this array holds the tables of a {@code [[x]]} header.
   * @param position The position of the array's opening {@code [}, or of its first {@code [[x]]} header if it is an
   *        array of tables, or {@code null} if the array was created through the editing API.
   */
  ListTomlArray(boolean isTableArray, @Nullable TomlPosition position) {
    this.isTableArray = isTableArray;
    this.position = position;
  }

  boolean isTableArray() {
    return isTableArray;
  }

  @Override
  @Nullable
  public TomlPosition position() {
    return position;
  }

  @Override
  public int size() {
    return entries.size();
  }

  @Override
  public boolean isEmpty() {
    return entries.isEmpty();
  }

  @Override
  public Entry.Indexed entry(int index) {
    return entries.get(index);
  }

  /**
   * Append a value to this array's sequence, and index it by position.
   *
   * @param value The value.
   * @param position The input position.
   * @return This array.
   */
  ListTomlArray appendParsed(Object value, TomlPosition position) {
    return appendParsed(value, position, Collections.emptyList());
  }

  /**
   * Append a value to this array's sequence, with its comments attached, and index it by position.
   *
   * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link LinkedTomlTable} /
   *        {@link ListTomlArray}, which is already a {@link Value}.
   * @param position The input position.
   * @param comments The comments attached to the value in the array.
   * @return This array.
   */
  ListTomlArray appendParsed(Object value, TomlPosition position, List<TomlComment> comments) {
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }
    if (!TomlType.typeFor(value).isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + value.getClass().getSimpleName());
    }
    append(new Entry.Indexed(Value.of(value, position), comments));
    return this;
  }

  /**
   * Append an entry to this array's sequence, and index it.
   *
   * @param entry The entry.
   * @return The entry appended.
   */
  private Entry.Indexed append(Entry.Indexed entry) {
    add(entry);
    entries.add(entry);
    return entry;
  }

  /**
   * Append an entry added through the editing API: no position, and marked as added.
   *
   * @param value The value, already wrapped; see {@link Value#of}.
   * @param comments The comments attached to the entry.
   * @return The entry appended.
   */
  private Entry.Indexed appendEdited(Value value, List<TomlComment> comments) {
    Entry.Indexed entry = append(new Entry.Indexed(value, comments));
    entry.valueModified = true;
    return entry;
  }

  @Override
  public List<Object> toList() {
    return entries.stream().map(entry -> entry.value().get()).collect(Collectors.toList());
  }

  @Override
  public ListTomlArray add(Object value) {
    Object normalized = TomlValues.normalize(value);
    appendEdited(Value.of(normalized, null), Collections.emptyList());
    return this;
  }

  @Override
  public ListTomlArray set(int index, Object value) {
    Entry.Indexed existing = entries.get(index);
    Object normalized = TomlValues.normalize(value);
    existing.replace(Value.of(normalized, null));
    return this;
  }

  @Override
  public Object remove(int index) {
    Entry.Indexed removed = entries.remove(index);
    boolean removedFromElements = removeElement(removed);
    assert removedFromElements : "removed entry is not among elements()";
    return removed.value().get();
  }

  @Override
  public void clear() {
    entries.clear();
    removeAllEntries();
  }

  @Override
  public boolean isModified(int index) {
    return entries.get(index).isModified();
  }

  @Override
  public ListTomlArray addComment(TomlComment comment) {
    addEditedComment(comment.requireUnattached().withoutPosition());
    return this;
  }

  @Override
  public boolean removeComment(TomlComment comment) {
    return removeElement(comment);
  }

  /**
   * Create a deep copy of this array for the editing API; see {@link #copyFrom(TomlArray, boolean)}. Keeps
   * {@link #isTableArray}, since it describes how the array is written, not where its entries came from.
   *
   * @return A copy of this array.
   */
  ListTomlArray copy() {
    return copyFrom(this, isTableArray);
  }

  /**
   * Build an array from another implementation of {@link TomlArray}; see {@link #copyFrom(TomlArray, boolean)}. The
   * public interface does not expose whether a foreign array came from {@code [[x]]} headers, so the copy is not one.
   *
   * @param array The array to copy.
   * @return A new array with the same entries.
   */
  static ListTomlArray copyFrom(TomlArray array) {
    return copyFrom(array, false);
  }

  /**
   * Build an array with the same entries as another, walked through its public interface: the same entries in the same
   * order, each with no position and marked as added, with each entry's attached comments and the unattached comments
   * in their places, copied without their positions. A nested table or array is copied recursively, through
   * {@link TomlValues#normalize}; every other value is shared.
   *
   * @param array The array to copy.
   * @param isTableArray Whether the copy holds the tables of a {@code [[x]]} header.
   * @return A new array with the same entries.
   */
  private static ListTomlArray copyFrom(TomlArray array, boolean isTableArray) {
    ListTomlArray copy = new ListTomlArray(isTableArray, null);
    for (TomlElement element : array.elements()) {
      if (element instanceof TomlComment) {
        copy.addParsedComment(((TomlComment) element).withoutPosition());
      } else {
        TomlEntry original = (TomlEntry) element;
        // A fresh wrapper even for a scalar: the entry's position is its value's, and a copy has none.
        Value value = Value.of(TomlValues.normalize(original.value().get()), null);
        copy.appendEdited(value, TomlComment.copyWithoutPositions(original.comments()));
      }
    }
    return copy;
  }
}
