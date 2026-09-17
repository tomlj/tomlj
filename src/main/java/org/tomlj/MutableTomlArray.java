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
   * @throws IllegalArgumentException If a value cannot be converted to a TOML value.
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
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value.
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
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value.
   */
  MutableTomlArray set(int index, Object value);

  /**
   * Remove the value at an index.
   *
   * <p>
   * The entry goes, with the comments attached to it, and every later entry moves down by one. The unattached comments
   * among this array's elements stay where they are.
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
   * Any {@link Iterable} is accepted here, not only a {@link java.util.Collection} as when one is set as a value:
   * passing one is how a caller says, explicitly, that it should become an array.
   *
   * @param values The values, each converted as {@link #add(Object)} converts one.
   * @return A new array with one entry per value.
   * @throws NullPointerException If a value is {@code null}.
   * @throws IllegalArgumentException If a value cannot be converted to a TOML value.
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
  default MutableTomlTable getTable(int index) {
    return (MutableTomlTable) TomlArray.super.getTable(index);
  }

  @Override
  default MutableTomlArray getArray(int index) {
    return (MutableTomlArray) TomlArray.super.getArray(index);
  }
}
