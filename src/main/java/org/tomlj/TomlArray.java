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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An array of TOML values.
 *
 * <p>
 * An array that can be edited is a {@link MutableTomlArray}; the arrays of a parse result are.
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
   * Get the comment attached to a value at a placement.
   *
   * <p>
   * This is a shortcut for {@link #entry(int)}, returning {@link TomlEntry#comment(TomlComment.Placement)}.
   *
   * @param index The array index.
   * @param placement {@link TomlComment.Placement#ABOVE} for the run directly above the value, or
   *        {@link TomlComment.Placement#AFTER} for the comment on its line.
   * @return The comment at that placement, or {@code null} if there is none.
   * @throws IndexOutOfBoundsException If the index is out of bounds.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}.
   */
  @Nullable
  default TomlComment comment(int index, TomlComment.Placement placement) {
    return entry(index).comment(placement);
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
   * Return a representation of this array using TOML, written with the default options.
   *
   * <p>
   * An array is always written in the default style, whether or not it was parsed, keeping the literal form each of its
   * values was parsed with.
   *
   * @return A TOML representation of this array.
   * @see TomlWriteOptions#defaults()
   * @see TomlWriteOptions
   */
  default String toToml() {
    return toToml(TomlWriteOptions.defaults());
  }

  /**
   * Return a representation of this array using TOML.
   *
   * <p>
   * An array is always written in the default style, whether or not it was parsed; see {@link #toToml()}.
   *
   * @param options The options to write with.
   * @return A TOML representation of this array.
   * @throws IllegalArgumentException If the version the options write for cannot write this array: TOML 1.0.0 and an
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
   * Append a TOML representation of this array to the appendable output, written with the default options.
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
   * Append a TOML representation of this array to the appendable output.
   *
   * <p>
   * Written as {@link #toToml(TomlWriteOptions)} writes it.
   *
   * @param appendable The appendable output.
   * @param options The options to write with.
   * @throws IOException If an IO error occurs.
   * @throws IllegalArgumentException If the version the options write for cannot write this array: TOML 1.0.0 and an
   *         inline table holding a comment, or text copied from a document that only TOML 1.1.0 allows; see
   *         {@link TomlWriteOptions#withVersion(TomlVersion)}.
   * @see TomlWriteOptions
   */
  default void toToml(Appendable appendable, TomlWriteOptions options) throws IOException {
    Serializer.toToml(this, appendable, options);
  }

  /**
   * Bind this array to a Java type, with the default options.
   *
   * @param type The type to bind to.
   * @param <T> The type to bind to.
   * @return A new instance of the type, holding the values of this array.
   * @throws TomlBindException If any value of this array cannot be bound.
   * @throws IllegalArgumentException If the type, or a type it holds, cannot be bound to.
   * @see #as(Class, TomlBindOptions)
   */
  default <T> T as(Class<T> type) {
    return as(type, TomlBindOptions.defaults());
  }

  /**
   * Bind this array to a Java type.
   *
   * <pre>{@code
   * record Server(String host, int port) {}
   *
   * Server[] servers = result.getArray("servers").as(Server[].class, TomlBindOptions.defaults());
   * }</pre>
   *
   * <p>
   * The value of each key or element is bound to the type declared for it, as follows:
   * <ul>
   * <li>A string to {@code String}, an enum constant, or {@code char} for a string of one character. An enum constant
   * is matched by its name, or else by its name ignoring case and with {@code -} and space read as {@code _}.</li>
   * <li>An integer to {@code long}, {@code int}, {@code short}, {@code byte}, {@code BigInteger}, or to a floating
   * point type if it can be represented exactly. A value out of range for the type is an error.</li>
   * <li>A float to {@code double}, {@code float} or {@code BigDecimal}.</li>
   * <li>A boolean to {@code boolean}.</li>
   * <li>An offset date-time to {@code OffsetDateTime}, {@code ZonedDateTime} or {@code Instant}, and a local date-time,
   * date or time to {@code LocalDateTime}, {@code LocalDate} or {@code LocalTime}.</li>
   * <li>An array to a {@code List}, {@code Set}, {@code Collection}, {@code Iterable}, a concrete collection class, or
   * a Java array.</li>
   * <li>A table to a record, a class, or a {@code Map} with {@code String} keys.</li>
   * <li>Any value to {@code Object}, unchanged, and a table or array to {@link TomlTable} or {@link TomlArray}, or to a
   * copy of it for {@link MutableTomlTable} or {@link MutableTomlArray}.</li>
   * <li>Any value to {@code Optional<T>}, by binding it to {@code T}.</li>
   * <li>Any value to a type that has a converter in the options, by that converter.</li>
   * </ul>
   *
   * <p>
   * A table is bound to a record through its canonical constructor, with a key for each component. It is bound to a
   * class by creating an instance with its constructor without parameters, then setting a field for each key. Every
   * field of the class and its superclasses is bound except static, transient and final fields. The key of a field or
   * component is its name, converted by the options' key naming, or the value of its {@link TomlName} annotation.
   *
   * <p>
   * A key the table does not have is bound as follows:
   * <ul>
   * <li>An {@code Optional} is empty.</li>
   * <li>A field of a class keeps the value it was given when the instance was created.</li>
   * <li>A record component, or a field of a class that is {@code null} once the instance is created, is {@code null} if
   * it is nullable, and an error if it is never {@code null}. A primitive is never {@code null}; otherwise, an
   * annotation named {@code NonNull}, {@code NotNull} or {@code Nonnull} marks a field or component as never
   * {@code null}, and one named {@code Nullable} marks it as nullable, from any package, as long as the annotation is
   * kept at runtime. Without either, it is never {@code null} if its class, an enclosing class, its package or its
   * module is annotated as JSpecify's {@code NullMarked}, and nullable otherwise.</li>
   * </ul>
   * A key in the table that names no field or component is an error, unless the options ignore unknown keys.
   *
   * <p>
   * Binding does not stop at the first error. Every value is bound, and the errors, each with the path and position of
   * the value, are thrown together in a {@link TomlBindException}. An exception thrown by a record's constructor or a
   * converter is reported as an error at the position of the value being bound.
   *
   * <p>
   * TomlJ binds to private fields and classes by reflection. In a named module, the package holding them must be opened
   * to the module {@code org.tomlj}.
   *
   * @param type The type to bind to.
   * @param options The options to bind with.
   * @param <T> The type to bind to.
   * @return A new instance of the type, holding the values of this array.
   * @throws TomlBindException If any value of this array cannot be bound.
   * @throws IllegalArgumentException If the type, or a type it holds, cannot be bound to.
   */
  @SuppressWarnings("unchecked")
  default <T> T as(Class<T> type, TomlBindOptions options) {
    return (T) ObjectBinder.bind(this, type, options);
  }

  /**
   * Bind this array to a generic Java type, with the default options.
   *
   * @param type The type to bind to.
   * @param <T> The type to bind to.
   * @return A new instance of the type, holding the values of this array.
   * @throws TomlBindException If any value of this array cannot be bound.
   * @throws IllegalArgumentException If the type, or a type it holds, cannot be bound to.
   * @see #as(Class, TomlBindOptions)
   */
  default <T> T as(GenericType<T> type) {
    return as(type, TomlBindOptions.defaults());
  }

  /**
   * Bind this array to a generic Java type.
   *
   * <p>
   * This binds as {@link #as(Class, TomlBindOptions)} does, to a type such as {@code List<Server>} that a {@code Class}
   * cannot name.
   *
   * @param type The type to bind to.
   * @param options The options to bind with.
   * @param <T> The type to bind to.
   * @return A new instance of the type, holding the values of this array.
   * @throws TomlBindException If any value of this array cannot be bound.
   * @throws IllegalArgumentException If the type, or a type it holds, cannot be bound to.
   */
  @SuppressWarnings("unchecked")
  default <T> T as(GenericType<T> type, TomlBindOptions options) {
    return (T) ObjectBinder.bind(this, type.type(), options);
  }
}
