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
import java.util.Map;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * A {@link TomlTable} whose values can be edited in place.
 *
 * <p>
 * A value set through this interface is any value a {@link TomlTable} can hold. An {@code Integer}, {@code Short} or
 * {@code Byte} is widened to a {@code Long} and a {@code Float} to a {@code Double}; a {@code Map} with {@code String}
 * keys becomes a table and a {@code Collection} an array, their values converted the same way. {@code null} throws a
 * {@link NullPointerException} and anything else an {@link IllegalArgumentException}.
 *
 * <p>
 * A {@link TomlTable} or {@link TomlArray} stored as a value is stored as a deep copy, so later changes to the original
 * are not seen; the stored copy is edited through the getters that return it.
 *
 * <p>
 * Not safe for use from multiple threads without external synchronization.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface MutableTomlTable extends TomlTable {

  /**
   * Create a new, empty table.
   *
   * @return A new, empty table.
   */
  static MutableTomlTable create() {
    return new LinkedTomlTable();
  }

  /**
   * Get the table at a key, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address"}).
   * @return The table.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If an element of the path exists and is not a table.
   */
  default MutableTomlTable getOrCreateTable(String dottedKey) {
    requireNonNull(dottedKey);
    return getOrCreateTable(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the table at a path, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * <p>
   * A table created here is an entry with no position and no comments.
   *
   * @param path The key path.
   * @return The table, or this table if {@code path} is empty.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws TomlInvalidTypeException If an element of the path exists and is not a table.
   */
  MutableTomlTable getOrCreateTable(List<String> path);

  /**
   * Get the array at a key, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.addresses"}).
   * @return The array.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value exists and is not an array, or an element of the path preceding the
   *         final key exists and is not a table.
   */
  default MutableTomlArray getOrCreateArray(String dottedKey) {
    requireNonNull(dottedKey);
    return getOrCreateArray(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the array at a path, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * <p>
   * An array or table created here is an entry with no position and no comments.
   *
   * @param path The key path.
   * @return The array.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws TomlInvalidTypeException If the value exists and is not an array, or an element of the path preceding the
   *         final key exists and is not a table.
   */
  MutableTomlArray getOrCreateArray(List<String> path);

  /**
   * Set a value in this table, replacing any value already there, as {@link #set(List, Object)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey} or {@code value} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, or {@code value} cannot be converted to a TOML value.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  default MutableTomlTable set(String dottedKey, Object value) {
    requireNonNull(dottedKey);
    return set(Parser.parseDottedKey(dottedKey), value);
  }

  /**
   * Set a value in this table, replacing any value already there.
   *
   * <p>
   * Any intermediate table on the path that does not exist is created. Replacing a value keeps the entry: its position,
   * its place in iteration order and its attached comments. Adding one creates an entry with no position and no
   * comments. A table or array is stored as a deep copy, made as {@link #copyOf(TomlTable)} makes one, so it has no
   * positions.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before any intermediate table is created.
   *
   * @param path The key path.
   * @param value The value to set.
   * @return This table.
   * @throws IllegalArgumentException If {@code path} is empty, or {@code value} cannot be converted to a TOML value.
   * @throws NullPointerException If a path element, or {@code value}, is {@code null}.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  MutableTomlTable set(List<String> path, Object value);

  /**
   * Remove a value from this table, as {@link #remove(List)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value that was removed, or {@code null} if the key was not set.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  @Nullable
  default Object remove(String dottedKey) {
    requireNonNull(dottedKey);
    return remove(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Remove a value from this table.
   *
   * <p>
   * The entry goes, with the comments attached to it. The unattached comments among this table's elements stay where
   * they are.
   *
   * @param path The key path.
   * @return The value that was removed, or {@code null} if the key was not set, or if an element of the path preceding
   *         the final key is missing.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  @Nullable
  Object remove(List<String> path);

  /**
   * Remove every entry from this table.
   *
   * <p>
   * The unattached comments among this table's elements stay where they are. Clearing a table that is already empty is
   * not a modification.
   */
  void clear();

  /**
   * Whether this table was changed through this interface.
   *
   * <p>
   * A change is an entry added, replaced or removed, in this table or in any table or array nested within it. A table
   * read from a document, or newly created, reports {@code false}; nothing resets this once it is {@code true}.
   *
   * @return {@code true} if this table was changed through this interface.
   */
  boolean isModified();

  /**
   * Whether an entry of this table was changed through this interface, as {@link #isModified(List)} reports.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the entry was changed; {@code false} if the key is not set.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isModified(String dottedKey) {
    requireNonNull(dottedKey);
    return isModified(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Whether an entry of this table was changed through this interface.
   *
   * <p>
   * A change is the entry added or replaced, or a change within the table or array it holds.
   *
   * @param path The key path.
   * @return {@code true} if the entry was changed; {@code false} if the key is not set. Equivalent to
   *         {@link #isModified()} if {@code path} is empty.
   */
  boolean isModified(List<String> path);

  /**
   * Create a deep copy of a table.
   *
   * <p>
   * The copy is independent of {@code table}: a nested table or array is copied recursively, and later changes to
   * either are not seen by the other. Every entry of the copy has no input position and reports as modified, since none
   * of it was read from a document; the copy itself reports {@link #isModified()} {@code false} while it has no
   * entries, like {@link #create()}. The comments attached to each entry, and the unattached comments among the table's
   * elements, are kept.
   *
   * @param table The table to copy.
   * @return A new, independent table with the same entries.
   */
  static MutableTomlTable copyOf(TomlTable table) {
    requireNonNull(table);
    if (table instanceof LinkedTomlTable) {
      return ((LinkedTomlTable) table).copy();
    }
    return LinkedTomlTable.copyFrom(table);
  }

  /**
   * Create a table from a {@link Map}.
   *
   * <p>
   * Each key of {@code map} is one literal key, not a dotted key. Each value is converted as any value set through this
   * interface is.
   *
   * @param map The map to copy.
   * @return A new table with one entry per entry of {@code map}.
   * @throws NullPointerException If a value in {@code map} is {@code null}.
   * @throws IllegalArgumentException If a value in {@code map} cannot be converted to a TOML value.
   */
  static MutableTomlTable copyOf(Map<String, ?> map) {
    requireNonNull(map);
    MutableTomlTable table = create();
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      table.set(Collections.singletonList(entry.getKey()), entry.getValue());
    }
    return table;
  }

  @Override
  @Nullable
  default MutableTomlTable getTable(String dottedKey) {
    requireNonNull(dottedKey);
    return getTable(Parser.parseDottedKey(dottedKey));
  }

  @Override
  @Nullable
  default MutableTomlTable getTable(List<String> path) {
    TomlTable value = TomlTable.super.getTable(path);
    return (value != null) ? (MutableTomlTable) value : null;
  }

  @Override
  @Nullable
  default MutableTomlArray getArray(String dottedKey) {
    requireNonNull(dottedKey);
    return getArray(Parser.parseDottedKey(dottedKey));
  }

  @Override
  @Nullable
  default MutableTomlArray getArray(List<String> path) {
    TomlArray value = TomlTable.super.getArray(path);
    return (value != null) ? (MutableTomlArray) value : null;
  }
}
