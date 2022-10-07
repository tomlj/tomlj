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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An array of TOML values.
 */
public interface TomlArray {

  /**
   * The size of the array.
   *
   * @return The size of the array.
   */
  int size();

  /**
   * {@code true} if the array is empty.
   *
   * @return {@code true} if the array is empty.
   */
  boolean isEmpty();

  /**
   * {@code true} if the array contains strings.
   *
   * @return {@code true} if the array contains strings.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsStrings();

  /**
   * {@code true} if the array contains longs.
   *
   * @return {@code true} if the array contains longs.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsLongs();

  /**
   * {@code true} if the array contains doubles.
   *
   * @return {@code true} if the array contains doubles.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsDoubles();

  /**
   * {@code true} if the array contains booleans.
   *
   * @return {@code true} if the array contains booleans.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsBooleans();

  /**
   * {@code true} if the array contains {@link OffsetDateTime}s.
   *
   * @return {@code true} if the array contains {@link OffsetDateTime}s.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsOffsetDateTimes();

  /**
   * {@code true} if the array contains {@link LocalDateTime}s.
   *
   * @return {@code true} if the array contains {@link LocalDateTime}s.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsLocalDateTimes();

  /**
   * {@code true} if the array contains {@link LocalDate}s.
   *
   * @return {@code true} if the array contains {@link LocalDate}s.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsLocalDates();

  /**
   * {@code true} if the array contains {@link LocalTime}s.
   *
   * @return {@code true} if the array contains {@link LocalTime}s.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsLocalTimes();

  /**
   * {@code true} if the array contains arrays.
   *
   * @return {@code true} if the array contains arrays.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsArrays();

  /**
   * {@code true} if the array contains tables.
   *
   * @return {@code true} if the array contains tables.
   * @deprecated Future releases will support heterogeneous arrays and this method will be removed.
   */
  boolean containsTables();

  /**
   * Get a value at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  Object get(int index);

  /**
   * Get the position where a value is defined in the TOML document.
   *
   * @param index The array index.
   * @return The input position.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  TomlPosition inputPositionOf(int index);

  /**
   * Get a string at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a long.
   */
  default String getString(int index) {
    Object value = get(index);
    if (!(value instanceof String)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (String) value;
  }

  /**
   * Get a long at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a long.
   */
  default long getLong(int index) {
    Object value = get(index);
    if (!(value instanceof Long)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (Long) value;
  }

  /**
   * Get a double at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a long.
   */
  default double getDouble(int index) {
    Object value = get(index);
    if (!(value instanceof Double)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (Double) value;
  }

  /**
   * Get a boolean at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a long.
   */
  default boolean getBoolean(int index) {
    Object value = get(index);
    if (!(value instanceof Boolean)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (Boolean) value;
  }

  /**
   * Get an offset date time at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not an {@link OffsetDateTime}.
   */
  default OffsetDateTime getOffsetDateTime(int index) {
    Object value = get(index);
    if (!(value instanceof OffsetDateTime)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (OffsetDateTime) value;
  }

  /**
   * Get a local date time at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not an {@link LocalDateTime}.
   */
  default LocalDateTime getLocalDateTime(int index) {
    Object value = get(index);
    if (!(value instanceof LocalDateTime)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (LocalDateTime) value;
  }

  /**
   * Get a local date at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not an {@link LocalDate}.
   */
  default LocalDate getLocalDate(int index) {
    Object value = get(index);
    if (!(value instanceof LocalDate)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (LocalDate) value;
  }

  /**
   * Get a local time at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not an {@link LocalTime}.
   */
  default LocalTime getLocalTime(int index) {
    Object value = get(index);
    if (!(value instanceof LocalTime)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (LocalTime) value;
  }

  /**
   * Get an array at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not an array.
   */
  default TomlArray getArray(int index) {
    Object value = get(index);
    if (!(value instanceof TomlArray)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (TomlArray) value;
  }

  /**
   * Get a table at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a table.
   */
  default TomlTable getTable(int index) {
    Object value = get(index);
    if (!(value instanceof TomlTable)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (TomlTable) value;
  }

  /**
   * Get the elements of this array as a {@link List}.
   *
   * <p>
   * Note that this does not do a deep conversion. If this array contains tables or arrays, they will be of type
   * {@link TomlTable} or {@link TomlArray} respectively.
   *
   * @return The elements of this array as a {@link List}.
   */
  List<Object> toList();

  /**
   * Return a representation of this array using JSON.
   *
   * @param options Options for the JSON encoder.
   * @return A JSON representation of this table.
   */
  default String toJson(JsonOptions... options) {
    return toJson(new HashSet<>(Arrays.asList(options)));
  }

  /**
   * Return a representation of this array using JSON.
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
   * Append a JSON representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, JsonOptions... options) throws IOException {
    toJson(appendable, new HashSet<>(Arrays.asList(options)));
  }

  /**
   * Append a JSON representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, Set<JsonOptions> options) throws IOException {
    JsonSerializer.toJson(this, appendable, options);
  }
}
