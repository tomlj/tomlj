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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * A value written in a document: under a key in a table, or in an array.
 *
 * <p>
 * A table or array is itself the value, so a {@link TomlTable} or {@link TomlArray} read from a document is also a
 * {@code TomlValue}. The unattached comments written inside it are among its {@link TomlTable#elements()} or
 * {@link TomlArray#elements()}. A value never carries comments of its own: the comments written around it belong to the
 * entry that holds it, read through {@link TomlEntry#comments()}.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface TomlValue extends TomlElement {

  /**
   * Parse the text of one value, written as it is after the {@code =} of a {@code key = value} line, in the latest
   * version of TOML.
   *
   * @param text The value, e.g. {@code 0xFF}, {@code 'C:\path'} or <code>{ a = 1 }</code>.
   * @return The value, which keeps the notation it was written in; see {@link #parse(String, TomlVersion)}.
   * @throws NullPointerException If {@code text} is {@code null}.
   * @throws IllegalArgumentException If {@code text} is not one TOML value.
   */
  static TomlValue parse(String text) {
    return parse(text, TomlVersion.LATEST);
  }

  /**
   * Parse the text of one value, written as it is after the {@code =} of a {@code key = value} line.
   *
   * <p>
   * The value keeps the notation it was written in. Stored in a table or an array through the editing API, it is
   * written with the text it was parsed from, as a value read from a document is, unless the document is written
   * keeping nothing ({@link TomlWriteOptions.Keep#NOTHING}); an inline table is written as an inline table, and an
   * array of tables as an array, rather than under {@code [x]} or {@code [[x]]} headers.
   *
   * <p>
   * A comment or a line break before or after the value is rejected. A comment inside an inline table or an array is
   * part of the value and is kept.
   *
   * @param text The value, e.g. {@code 0xFF}, {@code 'C:\path'} or <code>{ a = 1 }</code>.
   * @param version The version of TOML the value is written in.
   * @return The value.
   * @throws NullPointerException If {@code text} or {@code version} is {@code null}.
   * @throws IllegalArgumentException If {@code text} is not one TOML value.
   */
  static TomlValue parse(String text, TomlVersion version) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(version);
    return Parser.parseValue(text, version);
  }

  /**
   * Get the value.
   *
   * @return The value: a {@code String}, {@code Long}, {@code Double}, {@code Boolean}, date/time, {@link TomlTable} or
   *         {@link TomlArray}.
   */
  Object get();

  /**
   * {@code true} if this value is a string.
   *
   * @return {@code true} if this value is a string.
   */
  default boolean isString() {
    return get() instanceof String;
  }

  /**
   * Get this value as a string.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a string.
   */
  default String getString() {
    Object value = get();
    if (!(value instanceof String)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (String) value;
  }

  /**
   * {@code true} if this value is a long.
   *
   * @return {@code true} if this value is a long.
   */
  default boolean isLong() {
    return get() instanceof Long;
  }

  /**
   * Get this value as a long.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a long.
   */
  default long getLong() {
    Object value = get();
    if (!(value instanceof Long)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (Long) value;
  }

  /**
   * {@code true} if this value is a double.
   *
   * @return {@code true} if this value is a double.
   */
  default boolean isDouble() {
    return get() instanceof Double;
  }

  /**
   * Get this value as a double.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a double.
   */
  default double getDouble() {
    Object value = get();
    if (!(value instanceof Double)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (Double) value;
  }

  /**
   * {@code true} if this value is a boolean.
   *
   * @return {@code true} if this value is a boolean.
   */
  default boolean isBoolean() {
    return get() instanceof Boolean;
  }

  /**
   * Get this value as a boolean.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a boolean.
   */
  default boolean getBoolean() {
    Object value = get();
    if (!(value instanceof Boolean)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (Boolean) value;
  }

  /**
   * {@code true} if this value is an offset date-time.
   *
   * @return {@code true} if this value is an offset date-time.
   */
  default boolean isOffsetDateTime() {
    return get() instanceof OffsetDateTime;
  }

  /**
   * Get this value as an offset date-time.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not an offset date-time.
   */
  default OffsetDateTime getOffsetDateTime() {
    Object value = get();
    if (!(value instanceof OffsetDateTime)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (OffsetDateTime) value;
  }

  /**
   * {@code true} if this value is a local date-time.
   *
   * @return {@code true} if this value is a local date-time.
   */
  default boolean isLocalDateTime() {
    return get() instanceof LocalDateTime;
  }

  /**
   * Get this value as a local date-time.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a local date-time.
   */
  default LocalDateTime getLocalDateTime() {
    Object value = get();
    if (!(value instanceof LocalDateTime)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (LocalDateTime) value;
  }

  /**
   * {@code true} if this value is a local date.
   *
   * @return {@code true} if this value is a local date.
   */
  default boolean isLocalDate() {
    return get() instanceof LocalDate;
  }

  /**
   * Get this value as a local date.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a local date.
   */
  default LocalDate getLocalDate() {
    Object value = get();
    if (!(value instanceof LocalDate)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (LocalDate) value;
  }

  /**
   * {@code true} if this value is a local time.
   *
   * @return {@code true} if this value is a local time.
   */
  default boolean isLocalTime() {
    return get() instanceof LocalTime;
  }

  /**
   * Get this value as a local time.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a local time.
   */
  default LocalTime getLocalTime() {
    Object value = get();
    if (!(value instanceof LocalTime)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (LocalTime) value;
  }

  /**
   * {@code true} if this value is an array.
   *
   * @return {@code true} if this value is an array.
   */
  default boolean isArray() {
    return get() instanceof TomlArray;
  }

  /**
   * Get this value as an array.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not an array.
   */
  default TomlArray getArray() {
    Object value = get();
    if (!(value instanceof TomlArray)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (TomlArray) value;
  }

  /**
   * {@code true} if this value is a table.
   *
   * @return {@code true} if this value is a table.
   */
  default boolean isTable() {
    return get() instanceof TomlTable;
  }

  /**
   * Get this value as a table.
   *
   * @return The value.
   * @throws TomlInvalidTypeException If the value is not a table.
   */
  default TomlTable getTable() {
    Object value = get();
    if (!(value instanceof TomlTable)) {
      throw new TomlInvalidTypeException("value is a " + TomlType.typeNameFor(value));
    }
    return (TomlTable) value;
  }
}
