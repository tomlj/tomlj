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
import java.util.*;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * An array of TOML values.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
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
   * Get a value at a specified index.
   *
   * <p>
   * This is a shortcut for {@link #entry(int)}, returning its value.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default Object get(int index) {
    return entry(index).value().get();
  }

  /**
   * {@code true} if the value at an index is a string.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a string.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isString(int index) {
    return get(index) instanceof String;
  }

  /**
   * Get a string at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a string.
   */
  default String getString(int index) {
    Object value = get(index);
    if (!(value instanceof String)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (String) value;
  }

  /**
   * {@code true} if the value at an index is a long.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a long.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isLong(int index) {
    return get(index) instanceof Long;
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
   * {@code true} if the value at an index is a double.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a double.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isDouble(int index) {
    return get(index) instanceof Double;
  }

  /**
   * Get a double at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a double.
   */
  default double getDouble(int index) {
    Object value = get(index);
    if (!(value instanceof Double)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (Double) value;
  }

  /**
   * {@code true} if the value at an index is a boolean.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a boolean.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isBoolean(int index) {
    return get(index) instanceof Boolean;
  }

  /**
   * Get a boolean at a specified index.
   *
   * @param index The array index.
   * @return The value.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws TomlInvalidTypeException If the value is not a boolean.
   */
  default boolean getBoolean(int index) {
    Object value = get(index);
    if (!(value instanceof Boolean)) {
      throw new TomlInvalidTypeException("key at index " + index + " is a " + TomlType.typeNameFor(value));
    }
    return (Boolean) value;
  }

  /**
   * {@code true} if the value at an index is an offset date-time.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is an offset date-time.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isOffsetDateTime(int index) {
    return get(index) instanceof OffsetDateTime;
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
   * {@code true} if the value at an index is a local date-time.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a local date-time.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isLocalDateTime(int index) {
    return get(index) instanceof LocalDateTime;
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
   * {@code true} if the value at an index is a local date.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a local date.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isLocalDate(int index) {
    return get(index) instanceof LocalDate;
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
   * {@code true} if the value at an index is a local time.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a local time.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isLocalTime(int index) {
    return get(index) instanceof LocalTime;
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
   * {@code true} if the value at an index is an array.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is an array.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isArray(int index) {
    return get(index) instanceof TomlArray;
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
   * {@code true} if the value at an index is a table.
   *
   * @param index The array index.
   * @return {@code true} if the value at the index is a table.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default boolean isTable(int index) {
    return get(index) instanceof TomlTable;
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
   * Get the position where a value is defined in the TOML document.
   *
   * <p>
   * This is a shortcut for {@link #entry(int)}, returning its position.
   *
   * @param index The array index.
   * @return The input position, or {@code null} if the entry was not read from a document.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  @Nullable
  default TomlPosition inputPositionOf(int index) {
    return entry(index).position();
  }

  /**
   * Get the comments attached to a value.
   *
   * <p>
   * Returns the comments in document order: the run directly above the value, if any, then the comment on its line, if
   * any, so at most two, each with its {@link TomlComment#placement()}.
   *
   * <p>
   * In an array of tables, the comments on each {@code [[x]]} header are attached to the table it opens:
   * {@code comments(0)} for the first header, and so on.
   *
   * <p>
   * This is a shortcut for {@link #entry(int)}, returning its comments.
   *
   * @param index The array index.
   * @return The attached comments, in document order. Unmodifiable.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  default List<TomlComment> comments(int index) {
    return entry(index).comments();
  }

  /**
   * Get the entry at an index.
   *
   * <p>
   * The entry is the {@link TomlEntry} that {@link #elements()} holds for the index. {@link #get(int)},
   * {@link #inputPositionOf(int)} and {@link #comments(int)} are shortcuts reading its value, position and comments.
   *
   * @param index The array index.
   * @return The entry.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   */
  TomlEntry entry(int index);

  /**
   * Get the elements written in this array, in document order.
   *
   * <p>
   * Each element is a {@link TomlEntry}, an entry of this array, or an unattached {@link TomlComment}. The entries hold
   * the values {@link #get(int)} returns, in the same order. A comment is unattached when it is neither directly above
   * a value nor on its line: a run separated by a blank line from the value below it, a run before the closing bracket,
   * or a comment on a line with no value.
   *
   * @return The elements, in document order. Unmodifiable.
   */
  List<TomlElement> elements();

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
    return toJson(JsonOptions.setFrom(options));
  }

  /**
   * Return a representation of this array using JSON.
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
   * Append a JSON representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, JsonOptions... options) throws IOException {
    toJson(appendable, JsonOptions.setFrom(options));
  }

  /**
   * Append a JSON representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @param options Options for the JSON encoder.
   * @throws IOException If an IO error occurs.
   */
  default void toJson(Appendable appendable, EnumSet<JsonOptions> options) throws IOException {
    JsonSerializer.toJson(this, appendable, options);
  }

  /**
   * Return a representation of this array using TOML.
   *
   * @return A TOML representation of this array.
   */
  default String toToml() {
    StringBuilder builder = new StringBuilder();
    try {
      toToml(builder);
    } catch (IOException e) {
      // not reachable
      throw new UncheckedIOException(e);
    }
    return builder.toString();
  }

  /**
   * Append a TOML representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @throws IOException If an IO error occurs.
   */
  default void toToml(Appendable appendable) throws IOException {
    TomlSerializer.toToml(this, appendable);
  }
}
