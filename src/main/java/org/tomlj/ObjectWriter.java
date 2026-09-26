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

import static org.tomlj.JavaTypes.rawClass;
import static org.tomlj.JavaTypes.resolve;
import static org.tomlj.JavaTypes.typeArgument;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Writes Java objects as TOML tables and arrays by reflection: the reverse of {@link ObjectBinder}.
 *
 * <p>
 * A {@link Writer} is made for each Java type the first time it is written, and kept in the options it was made with.
 * Making a writer checks that the type can be written, and throws {@code IllegalArgumentException} if it cannot, before
 * any value is looked at. A value that cannot be written, such as an integer too large for TOML, also throws
 * {@code IllegalArgumentException}, naming the path of the value.
 *
 * <p>
 * A writer turns a value into what the editing API accepts: a {@code Map} for a table, a {@code List} for an array, and
 * a {@code String}, {@code Long}, {@code Double}, {@code Boolean}, date or time, {@link TomlTable} or {@link TomlArray}
 * for the rest, or whatever a converter returns. The editing API converts these as it stores them, once each.
 */
final class ObjectWriter {
  private ObjectWriter() {}

  /**
   * Returned by {@link Writer#update} when the value in the document is left as it is: either it already holds the
   * value, or it is a table or array that was updated in place.
   */
  private static final Object KEEP = new Object();

  /**
   * Returned by {@link ScalarWriter}'s binding when the value in a document cannot be bound to the writer's type.
   */
  private static final Object NOT_BOUND = new Object();

  @SuppressWarnings("ReferenceEquality")
  private static boolean isKept(@Nullable Object updated) {
    return updated == KEEP;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isBound(@Nullable Object bound) {
    return bound != NOT_BOUND;
  }

  static MutableTomlTable toTable(Object value, TomlBindOptions options) {
    Object written = write(value, options);
    if (written instanceof TomlTable) {
      return MutableTomlTable.copyOf((TomlTable) written);
    }
    if (!(written instanceof Map)) {
      throw new IllegalArgumentException(
          "Cannot write a "
              + value.getClass().getName()
              + " as a table; only a record, class or map is written as one");
    }
    @SuppressWarnings("unchecked")
    Map<String, ?> map = (Map<String, ?>) written;
    return MutableTomlTable.copyOf(map);
  }

  static MutableTomlArray toArray(Object value, TomlBindOptions options) {
    Object written = write(value, options);
    if (written instanceof TomlArray) {
      return MutableTomlArray.copyOf((TomlArray) written);
    }
    if (!(written instanceof List)) {
      throw new IllegalArgumentException(
          "Cannot write a "
              + value.getClass().getName()
              + " as an array; only a collection or Java array is written as one");
    }
    return MutableTomlArray.copyOf((List<?>) written);
  }

  private static @Nullable Object write(Object value, TomlBindOptions options) {
    return writerFor(runtimeType(value, options), options).write(value, Path.ROOT, new Context(options));
  }

  static void update(MutableTomlTable table, Object value, TomlBindOptions options) {
    Writer writer = writerFor(runtimeType(value, options), options);
    if (!(writer instanceof TableWriter)) {
      throw new IllegalArgumentException(
          "Cannot write a "
              + value.getClass().getName()
              + " as a table; only a record, class or map is written as one");
    }
    ((TableWriter) writer).updateTable(table, value, Path.ROOT, new Context(options));
  }

  static void update(MutableTomlArray array, Object value, TomlBindOptions options) {
    Writer writer = writerFor(runtimeType(value, options), options);
    if (!(writer instanceof ArrayWriter)) {
      throw new IllegalArgumentException(
          "Cannot write a "
              + value.getClass().getName()
              + " as an array; only a collection or Java array is written as one");
    }
    ((ArrayWriter) writer).updateArray(array, value, Path.ROOT, new Context(options));
  }

  static Writer writerFor(Type type, TomlBindOptions options) {
    Writer writer = options.writers().get(type);
    if (writer != null) {
      return writer;
    }
    Map<Type, Writer> made = new HashMap<>();
    writer = make(type, options, made);
    for (Map.Entry<Type, Writer> entry : made.entrySet()) {
      options.writers().putIfAbsent(entry.getKey(), entry.getValue());
    }
    // Kept for every type, not only records and classes, so that each is made once, not each time it is looked up
    options.writers().putIfAbsent(type, writer);
    return writer;
  }

  /**
   * Writes a Java value as a TOML value.
   */
  abstract static class Writer {
    /**
     * Write a value.
     *
     * @param value The value, not {@code null}.
     * @return A value for the editing API to store, or {@code null} if there is no value to store, as for an empty
     *         {@code Optional}.
     * @throws IllegalArgumentException If the value cannot be written.
     */
    @Nullable
    abstract Object write(Object value, Path path, Context context);

    /**
     * Update a value in a document to hold a Java value.
     *
     * @param existing The value in the document.
     * @param value The value, not {@code null}.
     * @return {@link #KEEP} if {@code existing} is left as it is, a value for the editing API to store in its place, or
     *         {@code null} if there is no value to store, and {@code existing} is to be removed.
     * @throws IllegalArgumentException If the value cannot be written.
     */
    @Nullable
    Object update(Object existing, Object value, Path path, Context context) {
      return write(value, path, context);
    }

    /**
     * Whether a value in a document already holds a Java value, so that updating it would leave it as it is.
     *
     * @param existing The value in the document, or {@code null} if there is none.
     * @param value The value, not {@code null}.
     * @return {@code true} if updating {@code existing} to hold {@code value} would change nothing.
     */
    abstract boolean holds(@Nullable Object existing, Object value, Context context);
  }

  /**
   * The path of a value within the object being written, as {@link TomlBindError#path()} gives it.
   */
  static final class Path {
    static final Path ROOT = new Path(null, null, -1);

    @Nullable
    private final Path parent;
    @Nullable
    private final String key;
    private final int index;

    private Path(@Nullable Path parent, @Nullable String key, int index) {
      this.parent = parent;
      this.key = key;
      this.index = index;
    }

    Path key(String key) {
      return new Path(this, key, -1);
    }

    Path index(int index) {
      return new Path(this, null, index);
    }

    @Override
    public String toString() {
      if (parent == null) {
        return "";
      }
      String parentPath = parent.toString();
      return key != null ? ObjectBinder.keyPath(parentPath, key) : ObjectBinder.indexPath(parentPath, index);
    }

    IllegalArgumentException error(String message) {
      String path = toString();
      return new IllegalArgumentException(
          path.isEmpty() ? "Cannot write the value: " + message : "Cannot write " + path + ": " + message);
    }

    IllegalArgumentException error(String message, Throwable cause) {
      IllegalArgumentException e = error(message);
      e.initCause(cause);
      return e;
    }
  }

  static final class Context {
    final TomlBindOptions options;
    private final Set<Object> writing = Collections.newSetFromMap(new IdentityHashMap<>());

    Context(TomlBindOptions options) {
      this.options = options;
    }

    /**
     * Mark an object as being written, so that an object holding itself is found rather than written forever.
     */
    void enter(Object value, Path path) {
      if (!writing.add(value)) {
        throw path.error("the value holds itself, and TOML cannot hold a value within itself");
      }
    }

    void exit(Object value) {
      writing.remove(value);
    }
  }

  private static @Nullable Object writeValue(Writer writer, @Nullable Object value, Path path, Context context) {
    return value == null ? null : writer.write(value, path, context);
  }

  private static @Nullable Object updateValue(
      Writer writer,
      @Nullable Object existing,
      @Nullable Object value,
      Path path,
      Context context) {
    if (value == null) {
      return null;
    }
    if (existing == null) {
      return writer.write(value, path, context);
    }
    return writer.update(existing, value, path, context);
  }

  /**
   * The value to store in place of a scalar, in the notation the scalar was written in if that notation can hold it, as
   * {@link TomlValue#inNotationOf(TomlValue, long)} and {@link TomlValue#inNotationOf(TomlValue, String)} give it.
   *
   * @param existing The entry whose value is replaced, or {@code null} if there is none.
   * @param updated The value to store, as a writer gives it.
   * @return {@code updated} in the notation of the value it replaces, or {@code updated} itself if that value was not
   *         written in one of these notations, or it cannot hold {@code updated}.
   */
  private static Object inNotationOf(@Nullable TomlEntry existing, Object updated) {
    if (existing == null) {
      return updated;
    }
    TomlValue inNotation = null;
    if (updated instanceof String) {
      inNotation = TomlValues.inNotationOf(existing.value(), (String) updated);
    } else if (updated instanceof Long
        || updated instanceof Integer
        || updated instanceof Short
        || updated instanceof Byte) {
      inNotation = TomlValues.inNotationOf(existing.value(), ((Number) updated).longValue());
    }
    return (inNotation != null) ? inNotation : updated;
  }

  private static boolean holdsValue(Writer writer, @Nullable Object existing, @Nullable Object value, Context context) {
    if (value == null) {
      return existing == null;
    }
    return writer.holds(existing, value, context);
  }

  /**
   * The type to write a value as when nothing more is declared for it than {@code Object}: the nearest class or
   * interface it has that has a converter, so a converter for {@code Path} writes any implementation of it; or else
   * {@link TomlTable} or {@link TomlArray} for any table or array, whatever its class; or else its class, or the enum
   * class for an enum constant with a body of its own.
   */
  private static Class<?> runtimeType(Object value, TomlBindOptions options) {
    Class<?> type = value.getClass();
    List<Class<?>> supertypes = new ArrayList<>();
    supertypes.add(type);
    for (int i = 0; i < supertypes.size(); i++) {
      Class<?> supertype = supertypes.get(i);
      if (options.converterFor(supertype) != null) {
        return supertype;
      }
      if (supertype.getSuperclass() != null) {
        supertypes.add(supertype.getSuperclass());
      }
      supertypes.addAll(Arrays.asList(supertype.getInterfaces()));
    }
    if (value instanceof TomlTable) {
      return TomlTable.class;
    }
    if (value instanceof TomlArray) {
      return TomlArray.class;
    }
    if (!type.isEnum() && Enum.class.isAssignableFrom(type)) {
      return type.getSuperclass();
    }
    return type;
  }

  private static Writer make(Type type, TomlBindOptions options, Map<Type, Writer> made) {
    Writer existing = options.writers().get(type);
    if (existing == null) {
      existing = made.get(type);
    }
    if (existing != null) {
      return existing;
    }
    if (type instanceof TypeVariable) {
      return make(((TypeVariable<?>) type).getBounds()[0], options, made);
    }
    if (type instanceof WildcardType) {
      return make(resolve(type, Collections.<TypeVariable<?>, Type>emptyMap()), options, made);
    }
    Class<?> raw = rawClass(type);

    if (options.converterFor(raw) != null) {
      Function<Object, ?> converter = options.writeConverterFor(raw);
      if (converter == null) {
        throw new IllegalArgumentException(
            "Cannot write "
                + type.getTypeName()
                + ": its converter in TomlBindOptions only reads it; register one that also writes it");
      }
      return new ScalarWriter(type, options, converted(converter));
    }
    if (raw == Object.class) {
      return new RuntimeTypeWriter();
    }
    Function<Object, Object> scalar = scalar(raw);
    if (scalar != null) {
      return new ScalarWriter(type, options, scalar);
    }
    if (TomlTable.class.isAssignableFrom(raw)) {
      return new TomlTableWriter();
    }
    if (TomlArray.class.isAssignableFrom(raw)) {
      return new TomlArrayWriter();
    }
    if (raw == Optional.class) {
      return new OptionalWriter(make(typeArgument(type, Optional.class, 0), options, made));
    }
    if (raw.isEnum()) {
      Map<Object, String> names = ObjectBinder.enumNames(raw);
      return new ScalarWriter(type, options, names::get);
    }
    if (raw.isArray()) {
      Type componentType = type instanceof GenericArrayType ? ((GenericArrayType) type).getGenericComponentType()
          : raw.getComponentType();
      return new ArrayWriter(make(componentType, options, made));
    }
    if (Collection.class.isAssignableFrom(raw) || raw == Iterable.class) {
      Type elementType = typeArgument(type, raw == Iterable.class ? Iterable.class : Collection.class, 0);
      return new ArrayWriter(make(elementType, options, made));
    }
    if (Map.class.isAssignableFrom(raw)) {
      if (!ObjectBinder.hasStringKeys(type)) {
        throw new IllegalArgumentException(
            "Cannot write " + type.getTypeName() + ": the keys of a map must be strings");
      }
      return new MapWriter(make(typeArgument(type, Map.class, 1), options, made));
    }
    ObjectBinder.checkTypeArgumentsBounded(raw, made.keySet(), "Cannot write");
    checkWritableClass(raw, type);
    ObjectTableWriter writer = new ObjectTableWriter();
    made.put(type, writer);
    writer.init(type, options, made);
    return writer;
  }

  @Nullable
  private static Function<Object, Object> scalar(Class<?> raw) {
    if (raw == String.class
        || raw == boolean.class
        || raw == Boolean.class
        || raw == long.class
        || raw == Long.class
        || raw == int.class
        || raw == Integer.class
        || raw == short.class
        || raw == Short.class
        || raw == byte.class
        || raw == Byte.class
        || raw == double.class
        || raw == Double.class
        || raw == OffsetDateTime.class
        || raw == LocalDateTime.class
        || raw == LocalDate.class
        || raw == LocalTime.class) {
      return value -> value;
    }
    if (raw == float.class || raw == Float.class) {
      // The float's shortest decimal, so 0.1f is written as 0.1 rather than as the double nearest to it
      return value -> Double.parseDouble(value.toString());
    }
    if (raw == char.class || raw == Character.class) {
      return String::valueOf;
    }
    if (raw == BigInteger.class) {
      return value -> {
        BigInteger i = (BigInteger) value;
        if (i.bitLength() > 63) {
          throw new IllegalArgumentException(i + " is out of range for a TOML integer");
        }
        return i.longValue();
      };
    }
    if (raw == BigDecimal.class) {
      return value -> {
        BigDecimal d = (BigDecimal) value;
        double asDouble = d.doubleValue();
        if (!Double.isFinite(asDouble) || new BigDecimal(Double.toString(asDouble)).compareTo(d) != 0) {
          throw new IllegalArgumentException(d + " cannot be represented exactly as a TOML float");
        }
        return asDouble;
      };
    }
    if (raw == Instant.class) {
      return value -> OffsetDateTime.ofInstant((Instant) value, ZoneOffset.UTC);
    }
    if (raw == ZonedDateTime.class) {
      return value -> ((ZonedDateTime) value).toOffsetDateTime();
    }
    return null;
  }

  private static Function<Object, Object> converted(Function<Object, ?> converter) {
    return value -> {
      Object converted = converter.apply(value);
      if (converted == null) {
        throw new IllegalArgumentException("the converter returned null");
      }
      return converted;
    };
  }

  private static void checkWritableClass(Class<?> raw, Type type) {
    String reason = ObjectBinder.notReflectedReason(raw, "write");
    if (reason != null) {
      throw new IllegalArgumentException(
          "Cannot write " + type.getTypeName() + ": " + reason + "; register a converter for it in TomlBindOptions");
    }
  }

  /**
   * Writes a value that is stored whole: a string, number, boolean, date or time, or a value written by a converter.
   *
   * <p>
   * A value in a document is left as it is if it binds to a value equal to the one being written, so updating a
   * document keeps the way each unchanged value was written: {@code 0x1F}, {@code 'literal'}, {@code "on-success"} for
   * {@code ON_SUCCESS}, or {@code 0.1} for the float nearest to it.
   */
  private static final class ScalarWriter extends Writer {
    private final Type type;
    private final TomlBindOptions options;
    private final Function<Object, Object> toToml;

    ScalarWriter(Type type, TomlBindOptions options, Function<Object, Object> toToml) {
      this.type = type;
      this.options = options;
      this.toToml = toToml;
    }

    @Override
    Object write(Object value, Path path, Context context) {
      Object written;
      try {
        written = toToml.apply(value);
        // Checked here, where the path is known, rather than when the editing API stores it
        TomlValues.normalize(written);
      } catch (RuntimeException e) {
        throw path.error(describe(e), e);
      }
      return written;
    }

    @Override
    @Nullable
    Object update(Object existing, Object value, Path path, Context context) {
      Object bound = bind(existing);
      if (isBound(bound) && Objects.equals(bound, value)) {
        return KEEP;
      }
      Object written = write(value, path, context);
      return (isBound(bound) && writesAs(bound, written)) ? KEEP : written;
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      if (existing == null) {
        return false;
      }
      Object bound = bind(existing);
      if (!isBound(bound)) {
        return false;
      }
      if (Objects.equals(bound, value)) {
        return true;
      }
      Object written;
      try {
        written = toToml.apply(value);
      } catch (RuntimeException e) {
        // Writing the value reports it, with its path
        return false;
      }
      return writesAs(bound, written);
    }

    /**
     * The value in a document bound to this writer's type, or {@link #NOT_BOUND} if it cannot be.
     */
    @Nullable
    private Object bind(Object existing) {
      ObjectBinder.Context bindContext = new ObjectBinder.Context(options);
      Object bound =
          ObjectBinder.binderFor(type, options).bind(existing, ObjectBinder.Location.root(existing), bindContext);
      return bindContext.errors.isEmpty() ? bound : NOT_BOUND;
    }

    /**
     * Whether a value bound from a document writes as {@code written}. Values that are not equal can write the same,
     * such as {@code BigDecimal} 1.50 and 1.5, a {@code ZonedDateTime} in a region and one at that region's offset, or
     * two values of a converted class that does not define {@code equals}.
     */
    private boolean writesAs(@Nullable Object bound, Object written) {
      if (bound == null) {
        return false;
      }
      try {
        return Objects.equals(plain(toToml.apply(bound)), plain(written));
      } catch (RuntimeException e) {
        return false;
      }
    }

    /**
     * A written value as the document stores it: what a {@link TomlValue} holds, and a {@code Long} or {@code Double}
     * for a narrower number.
     */
    private static Object plain(Object written) {
      Object value = (written instanceof TomlValue) ? ((TomlValue) written).get() : written;
      if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
        return ((Number) value).longValue();
      }
      if (value instanceof Float) {
        return ((Float) value).doubleValue();
      }
      return value;
    }
  }

  private static String describe(Throwable e) {
    String message = e.getMessage();
    return message != null ? message : e.toString();
  }

  /**
   * Writes a value declared as {@code Object}, by the writer for its own class.
   */
  private static final class RuntimeTypeWriter extends Writer {
    @Override
    @Nullable
    Object write(Object value, Path path, Context context) {
      return runtimeWriter(value, path, context).write(value, path, context);
    }

    @Override
    @Nullable
    Object update(Object existing, Object value, Path path, Context context) {
      return runtimeWriter(value, path, context).update(existing, value, path, context);
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      Writer writer;
      try {
        writer = writerFor(runtimeType(value, context.options), context.options);
      } catch (IllegalArgumentException e) {
        // Writing the value reports it, with its path
        return false;
      }
      return writer.holds(existing, value, context);
    }

    private static Writer runtimeWriter(Object value, Path path, Context context) {
      try {
        return writerFor(runtimeType(value, context.options), context.options);
      } catch (IllegalArgumentException e) {
        throw path.error(e.getMessage(), e);
      }
    }
  }

  private static final class OptionalWriter extends Writer {
    private final Writer element;

    OptionalWriter(Writer element) {
      this.element = element;
    }

    @Override
    @Nullable
    Object write(Object value, Path path, Context context) {
      Optional<?> optional = (Optional<?>) value;
      return optional.isPresent() ? element.write(optional.get(), path, context) : null;
    }

    @Override
    @Nullable
    Object update(Object existing, Object value, Path path, Context context) {
      Optional<?> optional = (Optional<?>) value;
      return optional.isPresent() ? element.update(existing, optional.get(), path, context) : null;
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      Optional<?> optional = (Optional<?>) value;
      return optional.isPresent() ? element.holds(existing, optional.get(), context) : existing == null;
    }
  }

  /**
   * Writes a collection or Java array as an array.
   *
   * <p>
   * An array in a document is updated as a line diff changes a file. The elements it already holds, in the same order,
   * are found first, as many as there can be, and left as they are, with their comments. Between two of them, the
   * elements of the document are updated in place, in order, by the elements of the list, and what is left over of the
   * list is inserted, or of the document removed.
   */
  private static class ArrayWriter extends Writer {
    /**
     * The most pairs of elements compared to find those an array already holds, after the elements it holds at its
     * start and end. An array with more elements in between is updated in place index by index.
     */
    private static final int MAX_COMPARED = 10_000;

    private final Writer element;

    ArrayWriter(Writer element) {
      this.element = element;
    }

    @Override
    Object write(Object value, Path path, Context context) {
      context.enter(value, path);
      List<Object> elements = elements(value);
      List<Object> written = new ArrayList<>(elements.size());
      for (int i = 0; i < elements.size(); i++) {
        written.add(nonNull(writeValue(element, elements.get(i), path.index(i), context), path.index(i)));
      }
      context.exit(value);
      return written;
    }

    @Override
    Object update(Object existing, Object value, Path path, Context context) {
      if (!(existing instanceof MutableTomlArray)) {
        return write(value, path, context);
      }
      updateArray((MutableTomlArray) existing, value, path, context);
      return KEEP;
    }

    void updateArray(MutableTomlArray array, Object value, Path path, Context context) {
      context.enter(value, path);
      List<Object> elements = elements(value);
      List<int[]> held = held(array, elements, context);
      held.add(new int[] {array.size(), elements.size()});
      // The index in the array as it is being updated, and the next index of the array as it was and of the list
      int at = 0;
      int from = 0;
      int to = 0;
      for (int[] pair : held) {
        for (; from < pair[0] && to < pair[1]; from++, to++, at++) {
          Path elementPath = path.index(to);
          Object updated = updateValue(element, array.get(at), elements.get(to), elementPath, context);
          if (!isKept(updated)) {
            array.set(at, inNotationOf(array.entry(at), nonNull(updated, elementPath)));
          }
        }
        for (; to < pair[1]; to++, at++) {
          Path elementPath = path.index(to);
          Object written = nonNull(writeValue(element, elements.get(to), elementPath, context), elementPath);
          if (at == array.size()) {
            array.add(written);
          } else {
            array.insertBefore(at, written);
          }
        }
        for (; from < pair[0]; from++) {
          array.remove(at);
        }
        from++;
        to++;
        at++;
      }
      context.exit(value);
    }

    /**
     * Find the elements of an array that already hold elements of a list, in the same order, as many as there can be.
     *
     * @return The index in the array and the index in the list of each element found, in order.
     */
    private List<int[]> held(MutableTomlArray array, List<Object> elements, Context context) {
      List<int[]> held = new ArrayList<>();
      int start = 0;
      int arrayEnd = array.size();
      int listEnd = elements.size();
      while (start < arrayEnd
          && start < listEnd
          && holdsValue(element, array.get(start), elements.get(start), context)) {
        held.add(new int[] {start, start});
        start++;
      }
      List<int[]> atEnd = new ArrayList<>();
      while (arrayEnd > start
          && listEnd > start
          && holdsValue(element, array.get(arrayEnd - 1), elements.get(listEnd - 1), context)) {
        arrayEnd--;
        listEnd--;
        atEnd.add(new int[] {arrayEnd, listEnd});
      }

      int rows = arrayEnd - start;
      int columns = listEnd - start;
      if ((long) rows * columns <= MAX_COMPARED) {
        // The longest common subsequence: longest[i][j] is the most elements held from index i of the array and j of
        // the list onward
        boolean[][] holds = new boolean[rows][columns];
        int[][] longest = new int[rows + 1][columns + 1];
        for (int i = rows - 1; i >= 0; i--) {
          for (int j = columns - 1; j >= 0; j--) {
            holds[i][j] = holdsValue(element, array.get(start + i), elements.get(start + j), context);
            longest[i][j] = holds[i][j] ? longest[i + 1][j + 1] + 1 : Math.max(longest[i + 1][j], longest[i][j + 1]);
          }
        }
        int i = 0;
        int j = 0;
        while (i < rows && j < columns) {
          if (holds[i][j]) {
            held.add(new int[] {start + i, start + j});
            i++;
            j++;
          } else if (longest[i + 1][j] >= longest[i][j + 1]) {
            i++;
          } else {
            j++;
          }
        }
      }

      Collections.reverse(atEnd);
      held.addAll(atEnd);
      return held;
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      if (!(existing instanceof MutableTomlArray)) {
        return false;
      }
      MutableTomlArray array = (MutableTomlArray) existing;
      List<Object> elements = elements(value);
      if (array.size() != elements.size()) {
        return false;
      }
      for (int i = 0; i < elements.size(); i++) {
        if (!holdsValue(element, array.get(i), elements.get(i), context)) {
          return false;
        }
      }
      return true;
    }

    private static Object nonNull(@Nullable Object element, Path path) {
      if (element == null) {
        throw path.error("an array cannot hold a missing value");
      }
      return element;
    }

    private static List<Object> elements(Object value) {
      List<Object> list = new ArrayList<>();
      if (value instanceof TomlArray) {
        TomlArray array = (TomlArray) value;
        for (int i = 0; i < array.size(); i++) {
          list.add(array.get(i));
        }
      } else if (value.getClass().isArray()) {
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
          list.add(Array.get(value, i));
        }
      } else {
        for (Iterator<?> it = ((Iterable<?>) value).iterator(); it.hasNext();) {
          list.add(it.next());
        }
      }
      return list;
    }
  }

  /**
   * Writes a record, class, map or {@link TomlTable} as a table.
   */
  private abstract static class TableWriter extends Writer {
    @Override
    Object update(Object existing, Object value, Path path, Context context) {
      if (!(existing instanceof MutableTomlTable)) {
        return write(value, path, context);
      }
      updateTable((MutableTomlTable) existing, value, path, context);
      return KEEP;
    }

    abstract void updateTable(MutableTomlTable table, Object value, Path path, Context context);

    static void updateEntry(
        MutableTomlTable table,
        String key,
        Writer writer,
        @Nullable Object value,
        Path path,
        Context context) {
      List<String> keyPath = Collections.singletonList(key);
      Object updated = updateValue(writer, table.get(keyPath), value, path, context);
      if (updated == null) {
        table.remove(keyPath);
      } else if (!isKept(updated)) {
        table.set(keyPath, inNotationOf(table.entry(keyPath), updated));
      }
    }
  }

  /**
   * Writes a map as a table, with an entry for each entry of the map that has a value.
   *
   * <p>
   * A table in a document is updated to hold the entries of the map: a key the map does not have is removed.
   */
  private static class MapWriter extends TableWriter {
    private final Writer valueWriter;

    MapWriter(Writer valueWriter) {
      this.valueWriter = valueWriter;
    }

    @Override
    Object write(Object value, Path path, Context context) {
      context.enter(value, path);
      Map<String, Object> table = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : asMap(value).entrySet()) {
        String key = key(entry.getKey(), path);
        Object written = writeValue(valueWriter, entry.getValue(), path.key(key), context);
        if (written != null) {
          table.put(key, written);
        }
      }
      context.exit(value);
      return table;
    }

    @Override
    void updateTable(MutableTomlTable table, Object value, Path path, Context context) {
      context.enter(value, path);
      Map<String, Object> byKey = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : asMap(value).entrySet()) {
        byKey.put(key(entry.getKey(), path), entry.getValue());
      }
      for (String key : new ArrayList<>(table.keySet())) {
        if (!byKey.containsKey(key)) {
          table.remove(Collections.singletonList(key));
        }
      }
      for (Map.Entry<String, Object> entry : byKey.entrySet()) {
        updateEntry(table, entry.getKey(), valueWriter, entry.getValue(), path.key(entry.getKey()), context);
      }
      context.exit(value);
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      if (!(existing instanceof MutableTomlTable)) {
        return false;
      }
      MutableTomlTable table = (MutableTomlTable) existing;
      Map<String, Object> byKey = new HashMap<>();
      for (Map.Entry<?, ?> entry : asMap(value).entrySet()) {
        if (!(entry.getKey() instanceof CharSequence)) {
          return false;
        }
        byKey.put(entry.getKey().toString(), entry.getValue());
      }
      for (String key : table.keySet()) {
        if (!byKey.containsKey(key)) {
          return false;
        }
      }
      for (Map.Entry<String, Object> entry : byKey.entrySet()) {
        Object existingValue = table.get(Collections.singletonList(entry.getKey()));
        if (!holdsValue(valueWriter, existingValue, entry.getValue(), context)) {
          return false;
        }
      }
      return true;
    }

    /**
     * The entries of a map, or of a {@link TomlTable} in its order.
     */
    private static Map<?, ?> asMap(Object value) {
      if (!(value instanceof TomlTable)) {
        return (Map<?, ?>) value;
      }
      TomlTable table = (TomlTable) value;
      Map<String, Object> entries = new LinkedHashMap<>();
      for (String key : table.keySet()) {
        entries.put(key, table.get(Collections.singletonList(key)));
      }
      return entries;
    }

    private static String key(@Nullable Object key, Path path) {
      if (!(key instanceof CharSequence)) {
        throw path.error("a key of a map is " + (key == null ? "null" : "not a string"));
      }
      return key.toString();
    }
  }

  /**
   * Writes a {@link TomlTable} as itself, for the editing API to store a copy of.
   *
   * <p>
   * A table in a document is updated in place to hold the entries of the table, as for a map, so the values it already
   * holds are left as they are.
   */
  private static final class TomlTableWriter extends MapWriter {
    TomlTableWriter() {
      super(new RuntimeTypeWriter());
    }

    @Override
    Object write(Object value, Path path, Context context) {
      return value;
    }
  }

  /**
   * Writes a {@link TomlArray} as itself, for the editing API to store a copy of.
   *
   * <p>
   * An array in a document is updated in place to hold the elements of the array, as for a list.
   */
  private static final class TomlArrayWriter extends ArrayWriter {
    TomlArrayWriter() {
      super(new RuntimeTypeWriter());
    }

    @Override
    Object write(Object value, Path path, Context context) {
      return value;
    }
  }

  /**
   * Writes a record or class as a table, with an entry for each member that is not {@code null} or an empty
   * {@code Optional}.
   *
   * <p>
   * A table in a document is updated to hold the members: the key of a member that is {@code null} or empty is removed,
   * and a key that names no member is left as it is.
   */
  private static final class ObjectTableWriter extends TableWriter {
    private List<ObjectBinder.Member> members = Collections.emptyList();
    private List<Writer> writers = Collections.emptyList();

    void init(Type type, TomlBindOptions options, Map<Type, Writer> made) {
      List<ObjectBinder.Member> list = ObjectBinder.members(type, options);
      List<Writer> memberWriters = new ArrayList<>(list.size());
      for (ObjectBinder.Member member : list) {
        try {
          memberWriters.add(make(member.type, options, made));
        } catch (ObjectBinder.GrowingTypeException e) {
          throw e;
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "Cannot write " + member.field.getDeclaringClass().getName() + "." + member.name + ": " + e.getMessage(),
              e);
        }
      }
      this.members = list;
      this.writers = memberWriters;
    }

    @Override
    Object write(Object value, Path path, Context context) {
      context.enter(value, path);
      Map<String, Object> table = new LinkedHashMap<>();
      for (int i = 0; i < members.size(); i++) {
        ObjectBinder.Member member = members.get(i);
        Object written = writeValue(writers.get(i), member.get(value), path.key(member.key), context);
        if (written != null) {
          table.put(member.key, written);
        }
      }
      context.exit(value);
      return table;
    }

    @Override
    void updateTable(MutableTomlTable table, Object value, Path path, Context context) {
      context.enter(value, path);
      for (int i = 0; i < members.size(); i++) {
        ObjectBinder.Member member = members.get(i);
        updateEntry(table, member.key, writers.get(i), member.get(value), path.key(member.key), context);
      }
      context.exit(value);
    }

    @Override
    boolean holds(@Nullable Object existing, Object value, Context context) {
      if (!(existing instanceof MutableTomlTable)) {
        return false;
      }
      MutableTomlTable table = (MutableTomlTable) existing;
      for (int i = 0; i < members.size(); i++) {
        ObjectBinder.Member member = members.get(i);
        Object existingValue = table.get(Collections.singletonList(member.key));
        if (!holdsValue(writers.get(i), existingValue, member.get(value), context)) {
          return false;
        }
      }
      return true;
    }
  }
}
