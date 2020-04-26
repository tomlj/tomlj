/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * An array of TOML values.
 */
public interface TomlArray {

  /**
   * @return The size of the array.
   */
  int size();

  /**
   * @return {@code true} if the array is empty.
   */
  boolean isEmpty();

  /**
   * @return {@code true} if the array contains strings.
   */
  boolean containsStrings();

  /**
   * @return {@code true} if the array contains longs.
   */
  boolean containsLongs();

  /**
   * @return {@code true} if the array contains doubles.
   */
  boolean containsDoubles();

  /**
   * @return {@code true} if the array contains booleans.
   */
  boolean containsBooleans();

  /**
   * @return {@code true} if the array contains {@link OffsetDateTime}s.
   */
  boolean containsOffsetDateTimes();

  /**
   * @return {@code true} if the array contains {@link LocalDateTime}s.
   */
  boolean containsLocalDateTimes();

  /**
   * @return {@code true} if the array contains {@link LocalDate}s.
   */
  boolean containsLocalDates();

  /**
   * @return {@code true} if the array contains {@link LocalTime}s.
   */
  boolean containsLocalTimes();

  /**
   * @return {@code true} if the array contains arrays.
   */
  boolean containsArrays();

  /**
   * @return {@code true} if the array contains tables.
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
   * @param optionalIndent Optional integer argument to indent JSON. 
   *        If provided &  >0, JSON will indent with number of spaces mentioned.
   *        If provided & <=0, JSON will be serialized (no indents).
   *        If not provided, JSON will indent with default of 2 spaces.
   * @return A JSON representation of this array.
   */
	default String toJson(Integer... optionalIndent) throws Exception {
		StringBuilder builder = new StringBuilder();
		try {
			toJson(builder, optionalIndent);
		} catch (JSONException e) {
			throw new JSONException(e);
		}
		JSONPath.validateJSON(builder.toString());
		return builder.toString();
	}

	/**
	 * Return a Map contaning Key = JSONPath Value = actual TOML value
	 * 
	 * @return A Map with JSONPath representation of this Array.
	 * @throws Exception
	 */
	default Map<String, Object> toJsonPath() throws Exception {
		try {
			return JSONPath.setJsonPaths(toJson());
		} catch (Exception e) {
			throw new JSONException(e);
		}
	}
  
  /**
   * Append a JSON representation of this array to the appendable output.
   *
   * @param appendable The appendable output.
   * @param optionalIndent Optional integer argument to indent JSON. 
   *        If provided &  >0, JSON will indent with number of spaces mentioned.
   *        If provided & <=0, JSON will be serialized (no indents).
   *        If not provided, JSON will indent with default of 2 spaces.
   * @throws IOException If an IO error occurs.
   */
	default void toJson(Appendable appendable, Integer... optionalIndent) throws IOException {
		JsonSerializer.toJson(this, appendable, optionalIndent);
	}
}