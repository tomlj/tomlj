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
import static org.tomlj.EmptyTomlArray.EMPTY_ARRAY;
import static org.tomlj.EmptyTomlTable.EMPTY_TABLE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An interface for accessing data stored in Tom's Obvious, Minimal Language (TOML).
 *
 * <p>
 * Values can be addressed in two ways. Methods that take a {@code String} interpret it as a <em>dotted key</em> using
 * TOML key syntax, exactly as it would appear in a document: {@code "server.port"} names the {@code port} entry within
 * the {@code server} table, and any key containing characters other than {@code A-Z}, {@code a-z}, {@code 0-9},
 * {@code _} and {@code -} must be quoted, e.g. {@code "\"@key\".value"}. Methods that take a {@code List<String>}
 * interpret each element as a literal key, with no quoting or escaping required.
 *
 * <p>
 * Consequently, the raw key names returned by {@link #keySet()} and {@link #entrySet()} can be used with the
 * {@code List<String>} methods (e.g. {@code get(Collections.singletonList(key))}), but not directly with the
 * {@code String} methods unless the key is a bare key. The keys returned by {@link #dottedKeySet()} and
 * {@link #dottedEntrySet()} are already quoted where necessary and can be passed to the {@code String} methods.
 * {@link Toml#joinKeyPath(List)} converts a key path into a dotted key.
 *
 * <p>
 * The comments of a document are kept, and are read from the table or array they were written in; see
 * {@link TomlComment}.
 *
 * <p>
 * A table that can be edited is a {@link MutableTomlTable}; a parse result is one.
 */
public interface TomlTable {

  /**
   * Return the number of entries in tis table.
   *
   * @return The number of entries in tis table.
   */
  int size();

  /**
   * {@code true} if there are no entries in this table.
   *
   * @return {@code true} if there are no entries in this table.
   */
  boolean isEmpty();

  /**
   * Check if a key was set in the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.port"}).
   * @return {@code true} if the key was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean contains(String dottedKey) {
    requireNonNull(dottedKey);
    return contains(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a key was set in the TOML document.
   *
   * @param path The key path.
   * @return {@code true} if the key was set in the TOML document.
   */
  default boolean contains(List<String> path) {
    try {
      return get(path) != null;
    } catch (TomlInvalidTypeException e) {
      return false;
    }
  }

  /**
   * Get the keys of this table.
   *
   * <p>
   * The returned set contains only immediate keys to this table, and not dotted keys or key paths. For a complete view
   * of keys available in the TOML document, use {@link #dottedKeySet()} or {@link #keyPathSet()}.
   *
   * <p>
   * The keys are returned as raw names, without quoting. To look up a value by one of these keys, use a
   * {@code List<String>} method such as {@link #get(List)}, or quote it with {@link Toml#joinKeyPath(List)} before
   * passing it to a {@code String} method such as {@link #get(String)}.
   *
   * @return A set containing the keys of this table.
   */
  Set<String> keySet();

  /**
   * Get all the dotted keys of this table.
   *
   * <p>
   * Paths to intermediary and empty tables are not returned. To include these, use {@link #dottedKeySet(boolean)}.
   *
   * @return A set containing all the dotted keys of this table.
   */
  default Set<String> dottedKeySet() {
    return keyPathSet().stream().map(Toml::joinKeyPath).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Get all the dotted keys of this table.
   *
   * @param includeTables If {@code true}, also include paths to intermediary and empty tables.
   * @return A set containing all the dotted keys of this table.
   */
  default Set<String> dottedKeySet(boolean includeTables) {
    return keyPathSet(includeTables)
        .stream()
        .map(Toml::joinKeyPath)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Get all the paths in this table.
   *
   * <p>
   * Paths to intermediary and empty tables are not returned. To include these, use {@link #keyPathSet(boolean)}.
   *
   * @return A set containing all the key paths of this table.
   */
  default Set<List<String>> keyPathSet() {
    return keyPathSet(false);
  }

  /**
   * Get all the paths in this table.
   *
   * @param includeTables If {@code true}, also include paths to intermediary and empty tables.
   * @return A set containing all the key paths of this table.
   */
  Set<List<String>> keyPathSet(boolean includeTables);

  /**
   * Get the entries of this table.
   *
   * <p>
   * The returned set contains only immediate entries of this table, and not entries with dotted keys or key paths. For
   * a complete view of all entries available in the TOML document, use {@link #dottedEntrySet()} or
   * {@link #entryPathSet()}.
   *
   * <p>
   * The entry keys are raw names, without quoting. See {@link #keySet()} for how to use them in lookups.
   *
   * @return A set containing the immediate entries of this table.
   */
  Set<Map.Entry<String, Object>> entrySet();

  /**
   * Get all the dotted entries of this table.
   *
   * <p>
   * Paths to intermediary and empty tables are not returned. To include these, use {@link #dottedEntrySet(boolean)}.
   *
   * @return A set containing all the entries of this table.
   */
  default Set<Map.Entry<String, Object>> dottedEntrySet() {
    return entryPathSet()
        .stream()
        .map(e -> new AbstractMap.SimpleEntry<>(Toml.joinKeyPath(e.getKey()), e.getValue()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Get all the dotted entries of this table.
   *
   * @param includeTables If {@code true}, also include paths to intermediary and empty tables.
   * @return A set containing all the entries of this table.
   */
  default Set<Map.Entry<String, Object>> dottedEntrySet(boolean includeTables) {
    return entryPathSet(includeTables)
        .stream()
        .map(e -> new AbstractMap.SimpleEntry<>(Toml.joinKeyPath(e.getKey()), e.getValue()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Get all the entries in this table.
   *
   * <p>
   * Paths to intermediary and empty tables are not returned. To include these, use {@link #entryPathSet(boolean)}.
   *
   * @return A set containing all the entries of this table.
   */
  default Set<Map.Entry<List<String>, Object>> entryPathSet() {
    return entryPathSet(false);
  }

  /**
   * Get all the entries in this table.
   *
   * @param includeTables If {@code true}, also include entries in intermediary and empty tables.
   * @return A set containing all the entries of this table.
   */
  Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables);

  /**
   * Get a value from the TOML document.
   *
   * <p>
   * The key is parsed using TOML key syntax, so keys containing characters other than {@code A-Z}, {@code a-z},
   * {@code 0-9}, {@code _} and {@code -} must be quoted (e.g. {@code "\"@key\""}). To look up a raw key name without
   * quoting, such as one returned by {@link #keySet()}, use {@link #get(List)} instead.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default Object get(String dottedKey) {
    requireNonNull(dottedKey);
    return get(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a value from the TOML document.
   *
   * <p>
   * This is a shortcut for {@link #entry(List)}, returning its value.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default Object get(List<String> path) {
    if (path.isEmpty()) {
      return this;
    }
    TomlKeyValue entry = entry(path);
    return (entry != null) ? entry.value().get() : null;
  }

  /**
   * Check if a value in the TOML document is a string.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.hostname"}).
   * @return {@code true} if the value can be obtained as a string.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isString(String dottedKey) {
    requireNonNull(dottedKey);
    return isString(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a string.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a string.
   */
  default boolean isString(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof String;
  }

  /**
   * Get a string from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.hostname"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a string, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default String getString(String dottedKey) {
    requireNonNull(dottedKey);
    return getString(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a string from the TOML document.
   *
   * @param path A dotted key (e.g. {@code "server.address.hostname"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a string, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default String getString(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (String) value;
  }

  /**
   * Get a string from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.hostname"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a string, or any element of the path preceding the
   *         final key is not a table.
   */
  default String getString(String dottedKey, Supplier<String> defaultValue) {
    requireNonNull(dottedKey);
    return getString(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a string from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a string, or any element of the path preceding the
   *         final key is not a table.
   */
  default String getString(List<String> path, Supplier<String> defaultValue) {
    requireNonNull(defaultValue);
    String value = getString(path);
    if (value != null) {
      return value;
    }
    return defaultValue.get();
  }

  /**
   * Check if a value in the TOML document is a long.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a long.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isLong(String dottedKey) {
    requireNonNull(dottedKey);
    return isLong(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a long.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a long.
   */
  default boolean isLong(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof Long;
  }

  /**
   * Get a long from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a long, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default Long getLong(String dottedKey) {
    requireNonNull(dottedKey);
    return getLong(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a long from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a long, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default Long getLong(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Long)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (Long) value;
  }

  /**
   * Get a long from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a long, or any element of the path preceding the
   *         final key is not a table.
   */
  default long getLong(String dottedKey, LongSupplier defaultValue) {
    requireNonNull(dottedKey);
    return getLong(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a long from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a long, or any element of the path preceding the
   *         final key is not a table.
   */
  default long getLong(List<String> path, LongSupplier defaultValue) {
    requireNonNull(defaultValue);
    Long value = getLong(path);
    if (value != null) {
      return value;
    }
    return defaultValue.getAsLong();
  }

  /**
   * Check if a value in the TOML document is a double.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a double.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isDouble(String dottedKey) {
    requireNonNull(dottedKey);
    return isDouble(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a double.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a double.
   */
  default boolean isDouble(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof Double;
  }

  /**
   * Get a double from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a double, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default Double getDouble(String dottedKey) {
    requireNonNull(dottedKey);
    return getDouble(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a double from the TOML document.
   *
   * @param path A dotted key.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a double, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default Double getDouble(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Double)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (Double) value;
  }

  /**
   * Get a double from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a double, or any element of the path preceding the
   *         final key is not a table.
   */
  default double getDouble(String dottedKey, DoubleSupplier defaultValue) {
    requireNonNull(dottedKey);
    return getDouble(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a double from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a double, or any element of the path preceding the
   *         final key is not a table.
   */
  default double getDouble(List<String> path, DoubleSupplier defaultValue) {
    requireNonNull(defaultValue);
    Double value = getDouble(path);
    if (value != null) {
      return value;
    }
    return defaultValue.getAsDouble();
  }

  /**
   * Check if a value in the TOML document is a boolean.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a boolean.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isBoolean(String dottedKey) {
    requireNonNull(dottedKey);
    return isBoolean(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a boolean.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a boolean.
   */
  default boolean isBoolean(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof Boolean;
  }

  /**
   * Get a boolean from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a boolean, or any element of the path preceding
   *         the final key is not a table.
   */
  @Nullable
  default Boolean getBoolean(String dottedKey) {
    requireNonNull(dottedKey);
    return getBoolean(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a boolean from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a boolean, or any element of the path preceding
   *         the final key is not a table.
   */
  @Nullable
  default Boolean getBoolean(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Boolean)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (Boolean) value;
  }

  /**
   * Get a boolean from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a boolean, or any element of the path preceding
   *         the final key is not a table.
   */
  default boolean getBoolean(String dottedKey, BooleanSupplier defaultValue) {
    requireNonNull(dottedKey);
    return getBoolean(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a boolean from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a boolean, or any element of the path preceding
   *         the final key is not a table.
   */
  default boolean getBoolean(List<String> path, BooleanSupplier defaultValue) {
    requireNonNull(defaultValue);
    Boolean value = getBoolean(path);
    if (value != null) {
      return value;
    }
    return defaultValue.getAsBoolean();
  }

  /**
   * Check if a value in the TOML document is an {@link OffsetDateTime}.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as an {@link OffsetDateTime}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isOffsetDateTime(String dottedKey) {
    requireNonNull(dottedKey);
    return isOffsetDateTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is an {@link OffsetDateTime}.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as an {@link OffsetDateTime}.
   */
  default boolean isOffsetDateTime(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof OffsetDateTime;
  }

  /**
   * Get an offset date time from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not an {@link OffsetDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  @Nullable
  default OffsetDateTime getOffsetDateTime(String dottedKey) {
    requireNonNull(dottedKey);
    return getOffsetDateTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get an offset date time from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not an {@link OffsetDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  @Nullable
  default OffsetDateTime getOffsetDateTime(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof OffsetDateTime)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (OffsetDateTime) value;
  }

  /**
   * Get an offset date time from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not an {@link OffsetDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  default OffsetDateTime getOffsetDateTime(String dottedKey, Supplier<OffsetDateTime> defaultValue) {
    requireNonNull(dottedKey);
    return getOffsetDateTime(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get an offset date time from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not an {@link OffsetDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  default OffsetDateTime getOffsetDateTime(List<String> path, Supplier<OffsetDateTime> defaultValue) {
    requireNonNull(defaultValue);
    OffsetDateTime value = getOffsetDateTime(path);
    if (value != null) {
      return value;
    }
    return defaultValue.get();
  }

  /**
   * Check if a value in the TOML document is a {@link LocalDateTime}.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a {@link LocalDateTime}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isLocalDateTime(String dottedKey) {
    requireNonNull(dottedKey);
    return isLocalDateTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a {@link LocalDateTime}.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a {@link LocalDateTime}.
   */
  default boolean isLocalDateTime(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof LocalDateTime;
  }

  /**
   * Get a local date time from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  @Nullable
  default LocalDateTime getLocalDateTime(String dottedKey) {
    requireNonNull(dottedKey);
    return getLocalDateTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a local date time from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  @Nullable
  default LocalDateTime getLocalDateTime(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof LocalDateTime)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (LocalDateTime) value;
  }

  /**
   * Get a local date time from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  default LocalDateTime getLocalDateTime(String dottedKey, Supplier<LocalDateTime> defaultValue) {
    requireNonNull(dottedKey);
    return getLocalDateTime(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a local date time from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDateTime}, or any element of the
   *         path preceding the final key is not a table.
   */
  default LocalDateTime getLocalDateTime(List<String> path, Supplier<LocalDateTime> defaultValue) {
    requireNonNull(defaultValue);
    LocalDateTime value = getLocalDateTime(path);
    if (value != null) {
      return value;
    }
    return defaultValue.get();
  }

  /**
   * Check if a value in the TOML document is a {@link LocalDate}.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a {@link LocalDate}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isLocalDate(String dottedKey) {
    requireNonNull(dottedKey);
    return isLocalDate(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a {@link LocalDate}.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a {@link LocalDate}.
   */
  default boolean isLocalDate(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof LocalDate;
  }

  /**
   * Get a local date from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDate}, or any element of the path
   *         preceding the final key is not a table.
   */
  @Nullable
  default LocalDate getLocalDate(String dottedKey) {
    requireNonNull(dottedKey);
    return getLocalDate(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a local date from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDate}, or any element of the path
   *         preceding the final key is not a table.
   */
  @Nullable
  default LocalDate getLocalDate(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof LocalDate)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (LocalDate) value;
  }

  /**
   * Get a local date from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDate}, or any element of the path
   *         preceding the final key is not a table.
   */
  default LocalDate getLocalDate(String dottedKey, Supplier<LocalDate> defaultValue) {
    requireNonNull(dottedKey);
    return getLocalDate(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a local date from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalDate}, or any element of the path
   *         preceding the final key is not a table.
   */
  default LocalDate getLocalDate(List<String> path, Supplier<LocalDate> defaultValue) {
    requireNonNull(defaultValue);
    LocalDate value = getLocalDate(path);
    if (value != null) {
      return value;
    }
    return defaultValue.get();
  }

  /**
   * Check if a value in the TOML document is a {@link LocalTime}.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the value can be obtained as a {@link LocalTime}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isLocalTime(String dottedKey) {
    requireNonNull(dottedKey);
    return isLocalTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a {@link LocalTime}.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a {@link LocalTime}.
   */
  default boolean isLocalTime(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof LocalTime;
  }

  /**
   * Get a local time from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalTime}, or any element of the path
   *         preceding the final key is not a table.
   */
  @Nullable
  default LocalTime getLocalTime(String dottedKey) {
    requireNonNull(dottedKey);
    return getLocalTime(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a local time from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalTime}, or any element of the path
   *         preceding the final key is not a table.
   */
  @Nullable
  default LocalTime getLocalTime(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof LocalTime)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (LocalTime) value;
  }

  /**
   * Get a local time from the TOML document, or return a default.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalTime}, or any element of the path
   *         preceding the final key is not a table.
   */
  default LocalTime getLocalTime(String dottedKey, Supplier<LocalTime> defaultValue) {
    requireNonNull(dottedKey);
    return getLocalTime(Parser.parseDottedKey(dottedKey), defaultValue);
  }

  /**
   * Get a local time from the TOML document, or return a default.
   *
   * @param path The key path.
   * @param defaultValue A supplier for the default value.
   * @return The value, or the default.
   * @throws TomlInvalidTypeException If the value is present but not a {@link LocalTime}, or any element of the path
   *         preceding the final key is not a table.
   */
  default LocalTime getLocalTime(List<String> path, Supplier<LocalTime> defaultValue) {
    requireNonNull(defaultValue);
    LocalTime value = getLocalTime(path);
    if (value != null) {
      return value;
    }
    return defaultValue.get();
  }

  /**
   * Check if a value in the TOML document is an array.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.addresses"}).
   * @return {@code true} if the value can be obtained as an array.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isArray(String dottedKey) {
    requireNonNull(dottedKey);
    return isArray(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is an array.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as an array.
   */
  default boolean isArray(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof TomlArray;
  }

  /**
   * Get an array from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.addresses"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not an array, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default TomlArray getArray(String dottedKey) {
    requireNonNull(dottedKey);
    return getArray(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get an array from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not an array, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default TomlArray getArray(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof TomlArray)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (TomlArray) value;
  }

  /**
   * Get an array from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.addresses"}).
   * @return The value, or an empty array if no array was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not an array, or any element of the path preceding the
   *         final key is not a table.
   */
  default TomlArray getArrayOrEmpty(String dottedKey) {
    requireNonNull(dottedKey);
    return getArrayOrEmpty(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get an array from the TOML document.
   *
   * @param path The key path.
   * @return The value, or an empty array if no array was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not an array, or any element of the path preceding the
   *         final key is not a table.
   */
  default TomlArray getArrayOrEmpty(List<String> path) {
    TomlArray value = getArray(path);
    if (value != null) {
      return value;
    }
    return EMPTY_ARRAY;
  }

  /**
   * Check if a value in the TOML document is a table.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address"}).
   * @return {@code true} if the value can be obtained as a table.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isTable(String dottedKey) {
    requireNonNull(dottedKey);
    return isTable(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Check if a value in the TOML document is a table.
   *
   * @param path The key path.
   * @return {@code true} if the value can be obtained as a table.
   */
  default boolean isTable(List<String> path) {
    Object value;
    try {
      value = get(path);
    } catch (TomlInvalidTypeException e) {
      return false;
    }
    return value instanceof TomlTable;
  }

  /**
   * Get a table from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address"}).
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a table, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default TomlTable getTable(String dottedKey) {
    requireNonNull(dottedKey);
    return getTable(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a table from the TOML document.
   *
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a table, or any element of the path preceding the
   *         final key is not a table.
   */
  @Nullable
  default TomlTable getTable(List<String> path) {
    Object value = get(path);
    if (value == null) {
      return null;
    }
    if (!(value instanceof TomlTable)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(value));
    }
    return (TomlTable) value;
  }

  /**
   * Get a table from the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value, or an empty table if no value was set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If the value is present but not a table, or any element of the path preceding the
   *         final key is not a table.
   */
  default TomlTable getTableOrEmpty(String dottedKey) {
    requireNonNull(dottedKey);
    return getTableOrEmpty(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get a table from the TOML document.
   *
   * @param path The key path.
   * @return The value, or an empty table if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If the value is present but not a table, or any element of the path preceding the
   *         final key is not a table.
   */
  default TomlTable getTableOrEmpty(List<String> path) {
    TomlTable value = getTable(path);
    if (value != null) {
      return value;
    }
    return EMPTY_TABLE;
  }

  /**
   * Get the position where a key is defined in the TOML document.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The input position, or {@code null} if the key was not set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default TomlPosition inputPositionOf(String dottedKey) {
    requireNonNull(dottedKey);
    return inputPositionOf(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the position where a key is defined in the TOML document.
   *
   * <p>
   * For a non-empty path, this is the position of {@link #entry(List)}.
   *
   * @param path The key path.
   * @return The input position, or {@code null} if the key was not set in the TOML document.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  TomlPosition inputPositionOf(List<String> path);

  /**
   * Get the comments attached to a key.
   *
   * <p>
   * Returns the comments in document order: the run directly above the key, if any, then the comment on its line, if
   * any, so at most two, each with its {@link TomlComment#placement()}.
   *
   * <p>
   * Returns an empty list if the key was not set in the document or has no comments; use {@link #contains(String)} to
   * tell those apart.
   *
   * <p>
   * The comments on a {@code [[x]]} header are attached to the table it opens, so {@code comments("x")} is empty and
   * they are read with {@code getArray("x").comments(0)} and so on.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The attached comments, in document order. Unmodifiable.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  default List<TomlComment> comments(String dottedKey) {
    requireNonNull(dottedKey);
    return comments(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the comments attached to a key.
   *
   * <p>
   * Returns the comments in document order: the run directly above the key, if any, then the comment on its line, if
   * any, so at most two, each with its {@link TomlComment#placement()}.
   *
   * <p>
   * Returns an empty list if the key was not set in the document or has no comments; use {@link #contains(List)} to
   * tell those apart.
   *
   * <p>
   * The comments on a {@code [[x]]} header are attached to the table it opens, so {@code comments("x")} is empty and
   * they are read with {@code getArray("x").comments(0)} and so on.
   *
   * <p>
   * This is a shortcut for {@link #entry(List)}, returning its comments.
   *
   * @param path The key path.
   * @return The attached comments, in document order. Unmodifiable.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  default List<TomlComment> comments(List<String> path) {
    if (path.isEmpty()) {
      return Collections.emptyList();
    }
    TomlKeyValue entry = entry(path);
    return (entry != null) ? entry.comments() : Collections.emptyList();
  }

  /**
   * Get the comment attached to a key at a placement.
   *
   * <p>
   * Returns {@code null} if the key was not set in the document or has no comment at that placement; use
   * {@link #contains(String)} to tell those apart.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param placement {@link TomlComment.Placement#ABOVE} for the run directly above the key, or
   *        {@link TomlComment.Placement#AFTER} for the comment on its line.
   * @return The comment at that placement, or {@code null} if there is none.
   * @throws NullPointerException If {@code dottedKey} or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, or {@code placement} is
   *         {@link TomlComment.Placement#UNATTACHED}.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default TomlComment comment(String dottedKey, TomlComment.Placement placement) {
    requireNonNull(dottedKey);
    return comment(Parser.parseDottedKey(dottedKey), placement);
  }

  /**
   * Get the comment attached to a key at a placement.
   *
   * <p>
   * Returns {@code null} if the key was not set in the document or has no comment at that placement; use
   * {@link #contains(List)} to tell those apart.
   *
   * <p>
   * This is a shortcut for {@link #entry(List)}, returning {@link TomlEntry#comment(TomlComment.Placement)}.
   *
   * @param path The key path.
   * @param placement {@link TomlComment.Placement#ABOVE} for the run directly above the key, or
   *        {@link TomlComment.Placement#AFTER} for the comment on its line.
   * @return The comment at that placement, or {@code null} if there is none.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default TomlComment comment(List<String> path, TomlComment.Placement placement) {
    TomlComment.requireAttached(placement);
    if (path.isEmpty()) {
      return null;
    }
    TomlKeyValue entry = entry(path);
    return (entry != null) ? entry.comment(placement) : null;
  }

  /**
   * Get the entry for a key.
   *
   * <p>
   * The key is parsed using TOML key syntax, so keys containing characters other than {@code A-Z}, {@code a-z},
   * {@code 0-9}, {@code _} and {@code -} must be quoted (e.g. {@code "\"@key\""}). To look up a raw key name without
   * quoting, such as one returned by {@link #keySet()}, use {@link #entry(List)} instead.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The entry, or {@code null} if the key was not set in the TOML document.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  default TomlKeyValue entry(String dottedKey) {
    requireNonNull(dottedKey);
    return entry(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the entry for a key.
   *
   * <p>
   * The entry is the {@link TomlKeyValue} that {@link #elements()} holds for the key, in the table the path leads to.
   * {@link #get(List)}, {@link #inputPositionOf(List)} and {@link #comments(List)} are shortcuts that read the value,
   * position and comments of this entry. An empty path names this table itself, which is not an entry, so it returns
   * {@code null}.
   *
   * @param path The key path.
   * @return The entry, or {@code null} if the key was not set in the TOML document.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  TomlKeyValue entry(List<String> path);

  /**
   * Get the elements written in this table, in document order.
   *
   * <p>
   * Each element is a {@link TomlKeyValue}, an entry of this table, or an unattached {@link TomlComment}. The entries
   * are those {@link #entrySet()} holds. A comment is unattached when it is neither directly above an entry nor on its
   * line: a run separated by a blank line from the entry below it, a run at the end of a section or of the document,
   * or, in an inline table, a comment on a line with no entry.
   *
   * @return The elements, in document order. Unmodifiable.
   */
  List<TomlElement> elements();

  /**
   * Get the entries of this table as a {@link Map}.
   *
   * <p>
   * Note that this does not do a deep conversion. If this table contains tables or arrays, they will be of type
   * {@link TomlTable} or {@link TomlArray} respectively.
   *
   * @return The entries of this table as a {@link Map}.
   */
  Map<String, Object> toMap();

  /**
   * Return a representation of this table using JSON.
   *
   * @param options Options for the JSON encoder.
   * @return A JSON representation of this table.
   */
  default String toJson(JsonOptions... options) {
    return toJson(JsonOptions.setFrom(options));
  }

  /**
   * Return a representation of this table using JSON.
   *
   * @param options Options for the JSON encoder.
   * @return A JSON representation of this table.
   */
  default String toJson(EnumSet<JsonOptions> options) {
    StringBuilder builder = new StringBuilder();
    try {
      toJson(builder, options);
    } catch (IOException e) {
      // not reachable
      throw new UncheckedIOException(e);
    }
    return builder.toString();
  }

  /**
   * Append a JSON representation of this table to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, JsonOptions... options) throws IOException {
    toJson(appendable, JsonOptions.setFrom(options));
  }

  /**
   * Append a JSON representation of this table to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, EnumSet<JsonOptions> options) throws IOException {
    JsonSerializer.toJson(this, appendable, options);
  }

  /**
   * Return a representation of this table using TOML, written with the default options.
   *
   * <p>
   * A {@link TomlParseResult} is written keeping its layout: the text it was parsed from is written back, with only
   * what the editing API changed written anew; any other table, including a table of a parse result rather than the
   * result itself, is written in the default style, keeping the literal form each of its values was parsed with.
   *
   * @return A TOML representation of this table.
   * @see TomlWriteOptions#defaults()
   * @see TomlWriteOptions
   */
  default String toToml() {
    return toToml(TomlWriteOptions.defaults());
  }

  /**
   * Return a representation of this table using TOML.
   *
   * <p>
   * A {@link TomlParseResult} keeps as much of its existing structure and format as {@code options} ask for; see
   * {@link TomlWriteOptions.Keep}. Any other table is written in the default style, keeping the literal form of each
   * value unless the options ask for {@link TomlWriteOptions.Keep#NOTHING}. See {@link #toToml()}.
   *
   * @param options The options to write with.
   * @return A TOML representation of this table.
   * @throws IllegalArgumentException If the version the options write for cannot write this table: TOML 1.0.0 and an
   *         inline table holding a comment, or text copied from a document that only TOML 1.1.0 allows; see
   *         {@link TomlWriteOptions#withVersion(TomlVersion)}.
   * @see TomlWriteOptions
   */
  default String toToml(TomlWriteOptions options) {
    StringBuilder builder = new StringBuilder();
    try {
      toToml(builder, options);
    } catch (IOException e) {
      // not reachable
      throw new UncheckedIOException(e);
    }
    return builder.toString();
  }

  /**
   * Append a TOML representation of this table to the appendable output, written with the default options.
   *
   * <p>
   * Written as {@link #toToml()} writes it.
   *
   * @param appendable The appendable output.
   * @throws IOException If an IO error occurs.
   * @see TomlWriteOptions#defaults()
   * @see TomlWriteOptions
   */
  default void toToml(Appendable appendable) throws IOException {
    toToml(appendable, TomlWriteOptions.defaults());
  }

  /**
   * Append a TOML representation of this table to the appendable output.
   *
   * <p>
   * Written as {@link #toToml(TomlWriteOptions)} writes it.
   *
   * @param appendable The appendable output.
   * @param options The options to write with.
   * @throws IOException If an IO error occurs.
   * @throws IllegalArgumentException If the version the options write for cannot write this table: TOML 1.0.0 and an
   *         inline table holding a comment, or text copied from a document that only TOML 1.1.0 allows; see
   *         {@link TomlWriteOptions#withVersion(TomlVersion)}.
   * @see TomlWriteOptions
   */
  default void toToml(Appendable appendable, TomlWriteOptions options) throws IOException {
    Serializer.toToml(this, appendable, options);
  }
}
