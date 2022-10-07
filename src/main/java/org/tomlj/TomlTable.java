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
   * @param path The key path.
   * @return The value, or {@code null} if no value was set in the TOML document.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  Object get(List<String> path);

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
   * @param path The key path.
   * @return The input position, or {@code null} if the key was not set in the TOML document.
   * @throws TomlInvalidTypeException If any element of the path preceding the final key is not a table.
   */
  @Nullable
  TomlPosition inputPositionOf(List<String> path);

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
   * Get the elements of this array as a {@link Map}.
   *
   * <p>
   * Note that this does not do a deep conversion. If this array contains tables or arrays, they will be of type
   * {@link TomlTable} or {@link TomlArray} respectively.
   *
   * @return The elements of this array as a {@link Map}.
   */
  Map<String, Object> toMap();

  /**
   * Return a representation of this table using JSON.
   *
   * @param options Options for the JSON encoder.
   * @return A JSON representation of this table.
   */
  default String toJson(JsonOptions... options) {
    return toJson(new HashSet<>(Arrays.asList(options)));
  }

  /**
   * Return a representation of this table using JSON.
   *
   * @param options Options for the JSON encoder.
   * @return A JSON representation of this table.
   */
  default String toJson(Set<JsonOptions> options) {
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
    toJson(appendable, new HashSet<>(Arrays.asList(options)));
  }

  /**
   * Append a JSON representation of this table to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, Set<JsonOptions> options) throws IOException {
    JsonSerializer.toJson(this, appendable, options);
  }
}
