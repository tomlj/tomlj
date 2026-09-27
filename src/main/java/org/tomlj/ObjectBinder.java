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
import static org.tomlj.JavaTypes.typeArguments;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Binds tables and arrays to Java types by reflection.
 *
 * <p>
 * A {@link Binder} is made for each Java type the first time it is bound, and kept in the options it was made with.
 * Making a binder checks that the type can be bound, and throws {@code IllegalArgumentException} if it cannot, before
 * any value is looked at. Binding then records every error in a {@link Context} and carries on, returning {@code null}
 * for a value that could not be bound; the errors are thrown together at the end.
 */
final class ObjectBinder {
  private ObjectBinder() {}

  private static final Set<String> NON_NULL_ANNOTATIONS =
      new LinkedHashSet<>(Arrays.asList("NonNull", "NotNull", "Nonnull"));
  private static final Set<String> NULLABLE_ANNOTATIONS =
      new LinkedHashSet<>(Arrays.asList("Nullable", "CheckForNull"));

  static Object bind(Object value, Type type, TomlBindOptions options) {
    Binder binder = binderFor(type, options);
    Context context = new Context(options);
    Object result = binder.bind(value, Location.root(value), context);
    if (!context.errors.isEmpty()) {
      List<TomlBindError> errors = new ArrayList<>(context.errors);
      errors
          .sort(
              Comparator
                  .comparing(
                      TomlBindError::position,
                      Comparator
                          .nullsLast(
                              Comparator.comparingInt(TomlPosition::line).thenComparingInt(TomlPosition::column))));
      throw new TomlBindException(errors);
    }
    if (result == null) {
      throw new IllegalStateException("Binding failed without an error");
    }
    return result;
  }

  static Binder binderFor(Type type, TomlBindOptions options) {
    Binder binder = options.binders().get(type);
    if (binder != null) {
      return binder;
    }
    Map<Type, Binder> made = new HashMap<>();
    binder = make(type, options, made);
    for (Map.Entry<Type, Binder> entry : made.entrySet()) {
      options.binders().putIfAbsent(entry.getKey(), entry.getValue());
    }
    // Kept for every type, not only records and classes, so that each is made once, not each time it is looked up
    options.binders().putIfAbsent(type, binder);
    return binder;
  }

  /**
   * Binds a value from a document to a Java type.
   */
  interface Binder {
    /**
     * Bind a value.
     *
     * @return The bound value, or {@code null} if an error was recorded in the context.
     */
    @Nullable
    Object bind(Object value, Location location, Context context);
  }

  /**
   * Where a value is in the table or array being bound.
   */
  static final class Location {

    @Nullable
    private final Location parent;
    @Nullable
    private final TomlTable table;
    @Nullable
    private final String key;
    private final int index;
    @Nullable
    private final TomlArray array;
    @Nullable
    private final TomlPosition rootPosition;

    private Location(
        @Nullable Location parent,
        @Nullable TomlTable table,
        @Nullable String key,
        int index,
        @Nullable TomlArray array,
        @Nullable TomlPosition rootPosition) {
      this.parent = parent;
      this.table = table;
      this.key = key;
      this.index = index;
      this.array = array;
      this.rootPosition = rootPosition;
    }

    /**
     * The location of the table or array being bound, at its own position if it was read from a document.
     */
    static Location root(Object value) {
      TomlPosition position = value instanceof TomlElement ? ((TomlElement) value).position() : null;
      return new Location(null, null, null, -1, null, position);
    }

    Location key(TomlTable table, String key) {
      return new Location(this, table, key, -1, null, null);
    }

    Location index(TomlArray array, int index) {
      return new Location(this, null, null, index, array, null);
    }

    String path() {
      if (parent == null) {
        return "";
      }
      return key != null ? keyPath(parent.path(), key) : indexPath(parent.path(), index);
    }

    /**
     * The position of the value, or of its key if the value has none, such as a table created by a dotted key.
     */
    @Nullable
    TomlPosition position() {
      if (table != null && key != null) {
        TomlKeyValue entry = table.entry(Collections.singletonList(key));
        if (entry == null) {
          return null;
        }
        TomlPosition valuePosition = entry.value().position();
        return valuePosition != null ? valuePosition : entry.position();
      }
      if (array != null) {
        return array.inputPositionOf(index);
      }
      return rootPosition;
    }

    @Nullable
    TomlPosition keyPosition() {
      if (table != null && key != null) {
        return table.inputPositionOf(Collections.singletonList(key));
      }
      return position();
    }
  }

  /**
   * The path of a key of the value at a path, as {@link TomlBindError#path()} gives it.
   */
  static String keyPath(String parentPath, String key) {
    String quoted = Toml.joinKeyPath(Collections.singletonList(key));
    return parentPath.isEmpty() ? quoted : parentPath + "." + quoted;
  }

  /**
   * The path of an element of the array at a path, as {@link TomlBindError#path()} gives it.
   */
  static String indexPath(String parentPath, int index) {
    return parentPath + "[" + index + "]";
  }

  static final class Context {
    final TomlBindOptions options;
    final List<TomlBindError> errors = new ArrayList<>();

    Context(TomlBindOptions options) {
      this.options = options;
    }

    @Nullable
    Object error(Location location, String message) {
      errors.add(new TomlBindError(location.path(), message, location.position()));
      return null;
    }

    @Nullable
    Object error(Location location, String message, Throwable cause) {
      errors.add(new TomlBindError(location.path(), message, location.position(), cause));
      return null;
    }

    @Nullable
    Object thrown(Location location, Throwable e) {
      String message = e.getMessage();
      errors.add(new TomlBindError(location.path(), message != null ? message : e.toString(), location.position(), e));
      return null;
    }

    @Nullable
    Object typeError(Location location, String expected, Object value) {
      return error(location, "expected " + expected + ", found " + withArticle(TomlType.typeNameFor(value)));
    }

    void missing(Location tableLocation, Location keyLocation) {
      errors.add(new TomlBindError(keyLocation.path(), "missing", tableLocation.position()));
    }

    void unknownKey(Location keyLocation) {
      errors.add(new TomlBindError(keyLocation.path(), "unknown key", keyLocation.keyPosition()));
    }

    void finalField(Location keyLocation) {
      errors.add(new TomlBindError(keyLocation.path(), "the field for this key is final", keyLocation.keyPosition()));
    }
  }

  private static String withArticle(String typeName) {
    char first = typeName.charAt(0);
    return ("aeiou".indexOf(first) >= 0 ? "an " : "a ") + typeName;
  }

  private static Binder make(Type type, TomlBindOptions options, Map<Type, Binder> made) {
    Binder existing = options.binders().get(type);
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

    Function<Object, ?> converter = options.converterFor(raw);
    if (converter != null) {
      return converted(converter);
    }
    Binder scalar = scalar(raw);
    if (scalar != null) {
      return scalar;
    }
    if (raw == Optional.class) {
      Binder element = make(typeArgument(type, Optional.class, 0), options, made);
      return (value, location, context) -> {
        Object bound = element.bind(value, location, context);
        return bound == null ? null : Optional.of(bound);
      };
    }
    if (raw.isEnum()) {
      return enumBinder(raw);
    }
    if (raw.isArray()) {
      Type componentType = type instanceof GenericArrayType ? ((GenericArrayType) type).getGenericComponentType()
          : raw.getComponentType();
      return arrayBinder(raw.getComponentType(), make(componentType, options, made));
    }
    if (Collection.class.isAssignableFrom(raw) || raw == Iterable.class) {
      Type elementType = typeArgument(type, raw == Iterable.class ? Iterable.class : Collection.class, 0);
      if (SortedSet.class.isAssignableFrom(raw) && !canSortElements(elementType)) {
        throw new IllegalArgumentException(
            "Cannot bind to " + type.getTypeName() + ": the elements of a sorted set must be Comparable");
      }
      return collectionBinder(collectionFactory(raw), make(elementType, options, made));
    }
    if (Map.class.isAssignableFrom(raw)) {
      if (!hasStringKeys(type)) {
        throw new IllegalArgumentException(
            "Cannot bind to " + type.getTypeName() + ": the keys of a map must be strings");
      }
      return mapBinder(mapFactory(raw), make(typeArgument(type, Map.class, 1), options, made));
    }
    checkTypeArgumentsBounded(raw, made.keySet(), "Cannot bind to");
    if (Records.isRecord(raw)) {
      RecordBinder binder = new RecordBinder(raw);
      made.put(type, binder);
      binder.init(type, options, made);
      return binder;
    }
    checkBindableClass(raw, type);
    ClassBinder binder = new ClassBinder(raw);
    made.put(type, binder);
    binder.init(type, options, made);
    return binder;
  }

  @Nullable
  private static Binder scalar(Class<?> raw) {
    if (raw == Object.class) {
      return (value, location, context) -> value;
    }
    if (raw == String.class) {
      return exact(String.class, "a string");
    }
    if (raw == boolean.class || raw == Boolean.class) {
      return exact(Boolean.class, "a boolean");
    }
    if (raw == long.class || raw == Long.class) {
      return exact(Long.class, "an integer");
    }
    if (raw == int.class || raw == Integer.class) {
      return integer("int", Integer.MIN_VALUE, Integer.MAX_VALUE, l -> (int) l);
    }
    if (raw == short.class || raw == Short.class) {
      return integer("short", Short.MIN_VALUE, Short.MAX_VALUE, l -> (short) l);
    }
    if (raw == byte.class || raw == Byte.class) {
      return integer("byte", Byte.MIN_VALUE, Byte.MAX_VALUE, l -> (byte) l);
    }
    if (raw == BigInteger.class) {
      return integer("BigInteger", Long.MIN_VALUE, Long.MAX_VALUE, BigInteger::valueOf);
    }
    if (raw == double.class || raw == Double.class) {
      return ObjectBinder::bindDouble;
    }
    if (raw == float.class || raw == Float.class) {
      return ObjectBinder::bindFloat;
    }
    if (raw == BigDecimal.class) {
      return ObjectBinder::bindBigDecimal;
    }
    if (raw == char.class || raw == Character.class) {
      return ObjectBinder::bindChar;
    }
    if (raw == OffsetDateTime.class) {
      return exact(OffsetDateTime.class, "an offset date-time");
    }
    if (raw == Instant.class) {
      return (value, location, context) -> value instanceof OffsetDateTime ? ((OffsetDateTime) value).toInstant()
          : context.typeError(location, "an offset date-time", value);
    }
    if (raw == ZonedDateTime.class) {
      return (value, location, context) -> value instanceof OffsetDateTime ? ((OffsetDateTime) value).toZonedDateTime()
          : context.typeError(location, "an offset date-time", value);
    }
    if (raw == LocalDateTime.class) {
      return exact(LocalDateTime.class, "a local date-time");
    }
    if (raw == LocalDate.class) {
      return exact(LocalDate.class, "a local date");
    }
    if (raw == LocalTime.class) {
      return exact(LocalTime.class, "a local time");
    }
    if (raw == TomlTable.class) {
      return exact(TomlTable.class, "a table");
    }
    if (raw == TomlArray.class) {
      return exact(TomlArray.class, "an array");
    }
    // A copy, so that changing it does not change the document it was bound from
    if (raw == MutableTomlTable.class) {
      return (value, location, context) -> value instanceof TomlTable ? MutableTomlTable.copyOf((TomlTable) value)
          : context.typeError(location, "a table", value);
    }
    if (raw == MutableTomlArray.class) {
      return (value, location, context) -> value instanceof TomlArray ? MutableTomlArray.copyOf((TomlArray) value)
          : context.typeError(location, "an array", value);
    }
    return null;
  }

  private static Binder exact(Class<?> tomlClass, String expected) {
    return (value, location, context) -> tomlClass.isInstance(value) ? value
        : context.typeError(location, expected, value);
  }

  private static Binder integer(String javaName, long min, long max, LongFunction<Object> box) {
    return (value, location, context) -> {
      if (!(value instanceof Long)) {
        return context.typeError(location, "an integer", value);
      }
      long l = (Long) value;
      if (l < min || l > max) {
        return context.error(location, l + " is out of range for " + javaName);
      }
      return box.apply(l);
    };
  }

  @Nullable
  private static Object bindDouble(Object value, Location location, Context context) {
    if (value instanceof Double) {
      return value;
    }
    if (value instanceof Long) {
      long l = (Long) value;
      double d = l;
      if (d == 0x1p63 || (long) d != l) {
        return context.error(location, l + " cannot be represented exactly as a double");
      }
      return d;
    }
    return context.typeError(location, "a float", value);
  }

  @Nullable
  private static Object bindFloat(Object value, Location location, Context context) {
    if (value instanceof Double) {
      double d = (Double) value;
      float f = (float) d;
      if (Double.isFinite(d) && (Math.abs(d) > Float.MAX_VALUE || (d != 0 && f == 0))) {
        return context.error(location, d + " is out of range for float");
      }
      return f;
    }
    if (value instanceof Long) {
      long l = (Long) value;
      float f = l;
      if (f == 0x1p63f || (long) f != l) {
        return context.error(location, l + " cannot be represented exactly as a float");
      }
      return f;
    }
    return context.typeError(location, "a float", value);
  }

  @Nullable
  private static Object bindBigDecimal(Object value, Location location, Context context) {
    if (value instanceof Long) {
      return BigDecimal.valueOf((Long) value);
    }
    if (value instanceof Double) {
      double d = (Double) value;
      if (!Double.isFinite(d)) {
        return context.error(location, d + " cannot be represented as a BigDecimal");
      }
      return new BigDecimal(Double.toString(d));
    }
    return context.typeError(location, "a float", value);
  }

  @Nullable
  private static Object bindChar(Object value, Location location, Context context) {
    if (!(value instanceof String)) {
      return context.typeError(location, "a string", value);
    }
    String s = (String) value;
    if (s.length() != 1) {
      return context.error(location, "expected a string of one character");
    }
    return s.charAt(0);
  }

  private static Binder converted(Function<Object, ?> converter) {
    return (value, location, context) -> {
      Object converted;
      try {
        converted = converter.apply(value);
      } catch (RuntimeException e) {
        return context.thrown(location, e);
      }
      if (converted == null) {
        return context.error(location, "the converter returned null");
      }
      return converted;
    };
  }

  private static Binder enumBinder(Class<?> raw) {
    Map<Object, String> names = enumNames(raw);
    return (value, location, context) -> {
      if (!(value instanceof String)) {
        return context.typeError(location, "a string", value);
      }
      String s = (String) value;
      for (Map.Entry<Object, String> entry : names.entrySet()) {
        if (entry.getValue().equals(s)) {
          return entry.getKey();
        }
      }
      String normalized = s.replace('-', '_').replace(' ', '_');
      for (Map.Entry<Object, String> entry : names.entrySet()) {
        String name = ((Enum<?>) entry.getKey()).name();
        if (entry.getValue().equals(name) && name.equalsIgnoreCase(normalized)) {
          return entry.getKey();
        }
      }
      return context.error(location, "\"" + s + "\" is not one of " + String.join(", ", names.values()));
    };
  }

  /**
   * The constants of an enum, in order, each with the string it is written as: the value of its {@link TomlName}
   * annotation, or else its name.
   *
   * @throws IllegalArgumentException If two constants have the same string.
   */
  static Map<Object, String> enumNames(Class<?> raw) {
    Map<Object, String> names = new LinkedHashMap<>();
    for (Object constant : raw.getEnumConstants()) {
      String name = ((Enum<?>) constant).name();
      TomlName tomlName;
      try {
        tomlName = raw.getField(name).getAnnotation(TomlName.class);
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException("Enum " + raw.getName() + " has no field for " + name, e);
      }
      names.put(constant, tomlName != null ? tomlName.value() : name);
    }
    Map<String, Object> byName = new HashMap<>();
    for (Map.Entry<Object, String> entry : names.entrySet()) {
      Object previous = byName.put(entry.getValue(), entry.getKey());
      if (previous != null) {
        throw new IllegalArgumentException(
            "Cannot bind to "
                + raw.getName()
                + ": "
                + ((Enum<?>) previous).name()
                + " and "
                + ((Enum<?>) entry.getKey()).name()
                + " are both named \""
                + entry.getValue()
                + "\"");
      }
    }
    return names;
  }


  private static Binder arrayBinder(Class<?> componentClass, Binder component) {
    return (value, location, context) -> {
      if (!(value instanceof TomlArray)) {
        return context.typeError(location, "an array", value);
      }
      TomlArray array = (TomlArray) value;
      int errorCount = context.errors.size();
      Object result = Array.newInstance(componentClass, array.size());
      for (int i = 0; i < array.size(); i++) {
        Object element = component.bind(array.get(i), location.index(array, i), context);
        if (element != null) {
          Array.set(result, i, element);
        }
      }
      return context.errors.size() > errorCount ? null : result;
    };
  }

  private static Binder collectionBinder(Supplier<Collection<Object>> factory, Binder element) {
    return (value, location, context) -> {
      if (!(value instanceof TomlArray)) {
        return context.typeError(location, "an array", value);
      }
      TomlArray array = (TomlArray) value;
      int errorCount = context.errors.size();
      Collection<Object> result = factory.get();
      for (int i = 0; i < array.size(); i++) {
        Location elementLocation = location.index(array, i);
        Object bound = element.bind(array.get(i), elementLocation, context);
        if (bound == null) {
          continue;
        }
        try {
          result.add(bound);
        } catch (ClassCastException e) {
          // A sorted set whose element type is Object, an interface or an abstract class can be given a value that is
          // not Comparable, or not comparable with the other elements
          context.error(elementLocation, "cannot be compared with the other elements of a sorted set", e);
        }
      }
      return context.errors.size() > errorCount ? null : result;
    };
  }

  private static Binder mapBinder(Supplier<Map<Object, Object>> factory, Binder valueBinder) {
    return (value, location, context) -> {
      if (!(value instanceof TomlTable)) {
        return context.typeError(location, "a table", value);
      }
      TomlTable table = (TomlTable) value;
      int errorCount = context.errors.size();
      Map<Object, Object> result = factory.get();
      for (String key : table.keySet()) {
        Object bound = valueBinder.bind(table.get(Collections.singletonList(key)), location.key(table, key), context);
        if (bound != null) {
          result.put(key, bound);
        }
      }
      return context.errors.size() > errorCount ? null : result;
    };
  }

  private static Supplier<Collection<Object>> collectionFactory(Class<?> raw) {
    if (raw == List.class || raw == Collection.class || raw == Iterable.class) {
      return ArrayList::new;
    }
    if (raw == Set.class) {
      return LinkedHashSet::new;
    }
    if (raw == SortedSet.class || raw == NavigableSet.class) {
      return TreeSet::new;
    }
    Supplier<Object> factory = noArgFactory(raw, "collection");
    @SuppressWarnings("unchecked")
    Supplier<Collection<Object>> result = () -> (Collection<Object>) factory.get();
    return result;
  }

  private static Supplier<Map<Object, Object>> mapFactory(Class<?> raw) {
    if (raw == Map.class) {
      return LinkedHashMap::new;
    }
    if (raw == SortedMap.class || raw == NavigableMap.class) {
      return TreeMap::new;
    }
    Supplier<Object> factory = noArgFactory(raw, "map");
    @SuppressWarnings("unchecked")
    Supplier<Map<Object, Object>> result = () -> (Map<Object, Object>) factory.get();
    return result;
  }

  private static Supplier<Object> noArgFactory(Class<?> raw, String kind) {
    if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
      throw new IllegalArgumentException(
          "Cannot bind to " + raw.getName() + ": no " + kind + " implementation is known for it");
    }
    Constructor<?> constructor = noArgConstructor(raw);
    return () -> {
      try {
        return constructor.newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot create " + raw.getName(), e);
      }
    };
  }

  private static Constructor<?> noArgConstructor(Class<?> raw) {
    Constructor<?> constructor;
    try {
      constructor = raw.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Cannot bind to " + raw.getName() + ": it has no constructor without parameters");
    }
    makeAccessible(constructor, raw, "bind to");
    return constructor;
  }

  /**
   * Make a constructor, field or accessor accessible by reflection.
   *
   * @param member The constructor, field or accessor.
   * @param declaringClass The class it is declared in.
   * @param verb What is done with the class: {@code "bind to"} or {@code "write"}.
   */
  private static void makeAccessible(AccessibleObject member, Class<?> declaringClass, String verb) {
    try {
      member.setAccessible(true);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Cannot "
              + verb
              + " "
              + declaringClass.getName()
              + ": it is not accessible to TomlJ; open its package to the module org.tomlj",
          e);
    }
  }

  /**
   * The most type arguments one generic record or class is made for while making the binder or writer of one type. A
   * record {@code Node<T>} with a member {@code List<Node<List<T>>>} would need one for {@code Node<List<T>>}, then for
   * {@code Node<List<List<T>>>}, and so on without end.
   */
  private static final int MAX_TYPE_ARGUMENTS = 16;

  /**
   * Thrown for a generic record or class whose members use it with type arguments that grow without end. The members
   * that lead to it do not add their names to the message, as there are as many of them as the limit.
   */
  static final class GrowingTypeException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    GrowingTypeException(String message) {
      super(message);
    }
  }

  /**
   * Check that a record or class has not been made for {@link #MAX_TYPE_ARGUMENTS} type arguments already.
   *
   * @param raw The record or class.
   * @param made The types made so far, including those still being made.
   * @param action The start of the message, such as {@code "Cannot bind to"}.
   * @throws GrowingTypeException If it has.
   */
  static void checkTypeArgumentsBounded(Class<?> raw, Set<Type> made, String action) {
    int count = 0;
    for (Type type : made) {
      if (rawClass(type) == raw) {
        count++;
      }
    }
    if (count >= MAX_TYPE_ARGUMENTS) {
      throw new GrowingTypeException(
          action + " " + raw.getName() + ": its members use it with type arguments that grow without end");
    }
  }

  /**
   * Whether the keys of a map type are strings, as the keys of a table are.
   */
  static boolean hasStringKeys(Type mapType) {
    Class<?> keyClass = rawClass(typeArgument(mapType, Map.class, 0));
    return keyClass == String.class || keyClass == Object.class || keyClass == CharSequence.class;
  }

  /**
   * Whether the elements of a sorted set can be sorted: its raw class is {@code Object}, an interface, an abstract
   * class, or implements {@link Comparable}.
   *
   * <p>
   * {@code Object}, an interface and an abstract class are allowed even though none of them implement
   * {@code Comparable}: the value bound at that element has a concrete class that TomlJ does not know when the binder
   * is made, and that class might implement {@code Comparable}. If it does not, binding reports an error for that
   * element.
   */
  static boolean canSortElements(Type elementType) {
    Class<?> elementClass = rawClass(elementType);
    return elementClass == Object.class
        || elementClass.isInterface()
        || Modifier.isAbstract(elementClass.getModifiers())
        || Comparable.class.isAssignableFrom(elementClass);
  }

  /**
   * Why a class cannot be bound or written by reflection, member by member, or {@code null} if it can be.
   *
   * @param raw The class.
   * @param verb What is done with it: {@code "bind to"} or {@code "write"}.
   */
  @Nullable
  static String notReflectedReason(Class<?> raw, String verb) {
    if (raw.isPrimitive()) {
      return "it is a primitive type";
    }
    if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
      return "it is an interface or abstract class";
    }
    if (raw.getName().startsWith("java.") || raw.getName().startsWith("javax.") || isPlatformClass(raw)) {
      return "TomlJ does not " + verb + " it";
    }
    return null;
  }

  /**
   * Whether a class is loaded by the bootstrap or platform class loader, and so is part of the JDK even though its name
   * is not {@code java.*} or {@code javax.*}, such as {@code sun.nio.fs.UnixPath}.
   */
  @SuppressWarnings("ReferenceEquality") // the platform class loader is one instance, never equal to another loader
  private static boolean isPlatformClass(Class<?> raw) {
    ClassLoader loader = raw.getClassLoader();
    return loader == null || loader == ClassLoader.getPlatformClassLoader();
  }

  private static void checkBindableClass(Class<?> raw, Type type) {
    String reason = notReflectedReason(raw, "bind to");
    if (reason == null && raw.isMemberClass() && !Modifier.isStatic(raw.getModifiers())) {
      reason = "it is an inner class; make it static";
    }
    if (reason != null) {
      throw new IllegalArgumentException(
          "Cannot bind to " + type.getTypeName() + ": " + reason + "; register a converter for it in TomlBindOptions");
    }
  }

  /**
   * A field or record component of a record or class, and the key it is bound to.
   */
  static final class Member {
    final String name;
    final String key;
    final Type type;
    final Field field;
    @Nullable
    final Method accessor;

    private Member(String name, String key, Type type, Field field, @Nullable Method accessor) {
      this.name = name;
      this.key = key;
      this.type = type;
      this.field = field;
      this.accessor = accessor;
    }

    /**
     * The value of this member in an instance, read through the accessor of a record component.
     */
    @Nullable
    Object get(Object instance) {
      try {
        return accessor != null ? accessor.invoke(instance) : field.get(instance);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        throw new IllegalStateException(cause);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Cannot read " + field.getDeclaringClass().getName() + "." + name, e);
      }
    }
  }

  /**
   * The components of a record, or the fields of a class and its superclasses that are bound: all but static, transient
   * and final fields. The fields of a superclass come first.
   *
   * @param verb What is done with the record or class: {@code "bind to"} or {@code "write"}.
   * @throws IllegalArgumentException If two members have the same key, or a member is not accessible.
   */
  static List<Member> members(Type genericType, TomlBindOptions options, String verb) {
    Class<?> type = rawClass(genericType);
    List<Member> list = new ArrayList<>();
    if (Records.isRecord(type)) {
      Map<TypeVariable<?>, Type> arguments = typeArguments(genericType);
      for (Records.Component component : Records.components(type)) {
        Field field;
        Method accessor;
        try {
          field = type.getDeclaredField(component.name);
          accessor = type.getDeclaredMethod(component.name);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
          throw new IllegalStateException(
              "Record " + type.getName() + " has no field or accessor for " + component.name,
              e);
        }
        makeAccessible(accessor, type, verb);
        list.add(member(component.name, resolve(component.genericType, arguments), field, accessor, options));
      }
    } else {
      Type current = genericType;
      while (true) {
        Class<?> raw = rawClass(current);
        if (raw == Object.class) {
          break;
        }
        Map<TypeVariable<?>, Type> arguments = typeArguments(current);
        List<Member> declared = new ArrayList<>();
        for (Field field : raw.getDeclaredFields()) {
          int modifiers = field.getModifiers();
          if (Modifier.isStatic(modifiers)
              || Modifier.isTransient(modifiers)
              || Modifier.isFinal(modifiers)
              || field.isSynthetic()) {
            continue;
          }
          makeAccessible(field, raw, verb);
          declared.add(member(field.getName(), resolve(field.getGenericType(), arguments), field, null, options));
        }
        list.addAll(0, declared);
        Type superclass = raw.getGenericSuperclass();
        if (superclass == null) {
          break;
        }
        current = resolve(superclass, arguments);
      }
    }
    Map<String, Member> byKey = new HashMap<>();
    for (Member member : list) {
      Member previous = byKey.put(member.key, member);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Cannot "
                + verb
                + " "
                + type.getName()
                + ": "
                + previous.name
                + " and "
                + member.name
                + " both have the key "
                + member.key);
      }
    }
    return list;
  }

  /**
   * The keys of the final fields of a class and its superclasses, which are not bound: a key of one is reported as a
   * key for a final field rather than as unknown.
   */
  private static Set<String> finalFieldKeys(Class<?> type, TomlBindOptions options) {
    Set<String> keys = new HashSet<>();
    for (Class<?> raw = type; raw != null && raw != Object.class; raw = raw.getSuperclass()) {
      for (Field field : raw.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)
            && !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && !field.isSynthetic()) {
          TomlName tomlName = field.getAnnotation(TomlName.class);
          keys.add(tomlName != null ? tomlName.value() : options.keyNaming().keyFor(field.getName()));
        }
      }
    }
    return keys;
  }

  private static Member member(
      String name,
      Type type,
      Field field,
      @Nullable Method accessor,
      TomlBindOptions options) {
    TomlName tomlName = field.getAnnotation(TomlName.class);
    String key = tomlName != null ? tomlName.value() : options.keyNaming().keyFor(name);
    return new Member(name, key, type, field, accessor);
  }

  /**
   * A member, and how its value is bound.
   */
  private static final class Property {
    final String key;
    final Binder binder;
    final boolean optional;
    final boolean nonNull;
    @Nullable
    final Field field;

    Property(String key, Binder binder, boolean optional, boolean nonNull, @Nullable Field field) {
      this.key = key;
      this.binder = binder;
      this.optional = optional;
      this.nonNull = nonNull;
      this.field = field;
    }
  }

  private static Property property(
      Class<?> owner,
      Member member,
      boolean bindsField,
      TomlBindOptions options,
      Map<Type, Binder> made) {
    Binder binder;
    try {
      binder = make(member.type, options, made);
    } catch (GrowingTypeException e) {
      throw e;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Cannot bind " + owner.getName() + "." + member.name + ": " + e.getMessage(),
          e);
    }
    boolean optional = rawClass(member.type) == Optional.class;
    return new Property(member.key, binder, optional, isNonNull(member.field), bindsField ? member.field : null);
  }

  private static Map<String, Property> byKey(List<Property> properties) {
    Map<String, Property> map = new LinkedHashMap<>();
    for (Property property : properties) {
      map.put(property.key, property);
    }
    return map;
  }

  private static void checkUnknownKeys(
      TomlTable table,
      Map<String, Property> properties,
      Set<String> finalFieldKeys,
      Location location,
      Context context) {
    if (context.options.ignoresUnknownKeys()) {
      return;
    }
    for (String key : table.keySet()) {
      if (properties.containsKey(key)) {
        continue;
      }
      if (finalFieldKeys.contains(key)) {
        context.finalField(location.key(table, key));
      } else {
        context.unknownKey(location.key(table, key));
      }
    }
  }

  private static final class RecordBinder implements Binder {
    private final Class<?> type;
    private List<Property> properties = Collections.emptyList();
    private Map<String, Property> byKey = Collections.emptyMap();
    @Nullable
    private Constructor<?> constructor;

    RecordBinder(Class<?> type) {
      this.type = type;
    }

    void init(Type genericType, TomlBindOptions options, Map<Type, Binder> made) {
      List<Property> list = new ArrayList<>();
      List<Class<?>> parameterTypes = new ArrayList<>();
      for (Member member : members(genericType, options, "bind to")) {
        list.add(property(type, member, false, options, made));
        parameterTypes.add(member.field.getType());
      }
      Constructor<?> canonical;
      try {
        canonical = type.getDeclaredConstructor(parameterTypes.toArray(new Class<?>[0]));
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Record " + type.getName() + " has no canonical constructor", e);
      }
      makeAccessible(canonical, type, "bind to");
      this.properties = list;
      this.byKey = byKey(list);
      this.constructor = canonical;
    }

    @Override
    @Nullable
    public Object bind(Object value, Location location, Context context) {
      if (!(value instanceof TomlTable)) {
        return context.typeError(location, "a table", value);
      }
      TomlTable table = (TomlTable) value;
      int errorCount = context.errors.size();
      Object[] arguments = new Object[properties.size()];
      for (int i = 0; i < arguments.length; i++) {
        Property property = properties.get(i);
        Location keyLocation = location.key(table, property.key);
        Object tomlValue = table.get(Collections.singletonList(property.key));
        if (tomlValue != null) {
          arguments[i] = property.binder.bind(tomlValue, keyLocation, context);
        } else if (property.optional) {
          arguments[i] = Optional.empty();
        } else if (property.nonNull) {
          context.missing(location, keyLocation);
        }
      }
      checkUnknownKeys(table, byKey, Collections.emptySet(), location, context);
      if (context.errors.size() > errorCount) {
        return null;
      }
      try {
        return constructor.newInstance(arguments);
      } catch (InvocationTargetException e) {
        return context.thrown(location, e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot create " + type.getName(), e);
      }
    }
  }

  private static final class ClassBinder implements Binder {
    private final Class<?> type;
    private List<Property> properties = Collections.emptyList();
    private Map<String, Property> byKey = Collections.emptyMap();
    private Set<String> finalFieldKeys = Collections.emptySet();
    @Nullable
    private Constructor<?> constructor;

    ClassBinder(Class<?> type) {
      this.type = type;
    }

    void init(Type genericType, TomlBindOptions options, Map<Type, Binder> made) {
      Constructor<?> noArg = noArgConstructor(type);
      List<Property> list = new ArrayList<>();
      for (Member member : members(genericType, options, "bind to")) {
        list.add(property(member.field.getDeclaringClass(), member, true, options, made));
      }
      this.properties = list;
      this.byKey = byKey(list);
      this.finalFieldKeys = finalFieldKeys(type, options);
      this.constructor = noArg;
    }

    @Override
    @Nullable
    public Object bind(Object value, Location location, Context context) {
      if (!(value instanceof TomlTable)) {
        return context.typeError(location, "a table", value);
      }
      TomlTable table = (TomlTable) value;
      Object instance;
      try {
        instance = constructor.newInstance();
      } catch (InvocationTargetException e) {
        return context.thrown(location, e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot create " + type.getName(), e);
      }
      int errorCount = context.errors.size();
      try {
        for (Property property : properties) {
          Field field = property.field;
          Location keyLocation = location.key(table, property.key);
          Object tomlValue = table.get(Collections.singletonList(property.key));
          if (tomlValue != null) {
            Object bound = property.binder.bind(tomlValue, keyLocation, context);
            if (bound != null) {
              field.set(instance, bound);
            }
          } else if (field.get(instance) == null) {
            if (property.optional) {
              field.set(instance, Optional.empty());
            } else if (property.nonNull) {
              context.missing(location, keyLocation);
            }
          }
        }
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Cannot set a field of " + type.getName(), e);
      }
      checkUnknownKeys(table, byKey, finalFieldKeys, location, context);
      return context.errors.size() > errorCount ? null : instance;
    }
  }

  /**
   * Whether a field, or the record component it holds, is marked as never {@code null}.
   *
   * <p>
   * A primitive field is never {@code null}. Otherwise an annotation named {@code NonNull}, {@code NotNull} or
   * {@code Nonnull} on the field or its type marks it as never {@code null}, and one named {@code Nullable} or
   * {@code CheckForNull} marks it as nullable, in any package. Without either, a field is never {@code null} if its
   * class, a class enclosing it, its package or its module is annotated {@code NullMarked}, with no
   * {@code NullUnmarked} on a nearer one, as defined by JSpecify.
   */
  private static boolean isNonNull(Field field) {
    if (field.getType().isPrimitive()) {
      return true;
    }
    List<Annotation> annotations = new ArrayList<>(Arrays.asList(field.getDeclaredAnnotations()));
    annotations.addAll(Arrays.asList(field.getAnnotatedType().getDeclaredAnnotations()));
    for (Annotation annotation : annotations) {
      String name = annotation.annotationType().getSimpleName();
      if (NON_NULL_ANNOTATIONS.contains(name)) {
        return true;
      }
      if (NULLABLE_ANNOTATIONS.contains(name)) {
        return false;
      }
    }
    return isNullMarked(field.getDeclaringClass());
  }

  private static boolean isNullMarked(Class<?> type) {
    for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
      Boolean marked = nullMarking(c.getDeclaredAnnotations());
      if (marked != null) {
        return marked;
      }
    }
    Package pkg = type.getPackage();
    if (pkg != null) {
      Boolean marked = nullMarking(pkg.getDeclaredAnnotations());
      if (marked != null) {
        return marked;
      }
    }
    Boolean marked = nullMarking(type.getModule().getDeclaredAnnotations());
    return marked != null && marked;
  }

  @Nullable
  private static Boolean nullMarking(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      String name = annotation.annotationType().getSimpleName();
      if (name.equals("NullMarked")) {
        return Boolean.TRUE;
      }
      if (name.equals("NullUnmarked")) {
        return Boolean.FALSE;
      }
    }
    return null;
  }

  /**
   * Access to records through reflection, as TomlJ is compiled for Java 9, before records were added.
   */
  private static final class Records {
    @Nullable
    private static final Method IS_RECORD;
    @Nullable
    private static final Method GET_RECORD_COMPONENTS;
    @Nullable
    private static final Method GET_NAME;
    @Nullable
    private static final Method GET_GENERIC_TYPE;

    static {
      Method isRecord = null;
      Method getRecordComponents = null;
      Method getName = null;
      Method getGenericType = null;
      try {
        isRecord = Class.class.getMethod("isRecord");
        getRecordComponents = Class.class.getMethod("getRecordComponents");
        Class<?> component = Class.forName("java.lang.reflect.RecordComponent");
        getName = component.getMethod("getName");
        getGenericType = component.getMethod("getGenericType");
      } catch (ReflectiveOperationException e) {
        isRecord = null;
      }
      IS_RECORD = isRecord;
      GET_RECORD_COMPONENTS = getRecordComponents;
      GET_NAME = getName;
      GET_GENERIC_TYPE = getGenericType;
    }

    static final class Component {
      final String name;
      final Type genericType;

      Component(String name, Type genericType) {
        this.name = name;
        this.genericType = genericType;
      }
    }

    static boolean isRecord(Class<?> type) {
      if (IS_RECORD == null) {
        return false;
      }
      try {
        return (Boolean) IS_RECORD.invoke(type);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }

    static List<Component> components(Class<?> type) {
      try {
        Object[] components = (Object[]) GET_RECORD_COMPONENTS.invoke(type);
        List<Component> list = new ArrayList<>(components.length);
        for (Object component : components) {
          list.add(new Component((String) GET_NAME.invoke(component), (Type) GET_GENERIC_TYPE.invoke(component)));
        }
        return list;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
