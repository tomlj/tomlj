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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Options controlling how a table or array is bound to a Java type, and how a Java object is written as a table or
 * array.
 *
 * <p>
 * Options are immutable, and hold what they learn about each Java type bound or written with them, so reusing one set
 * of options for many bindings avoids examining the same classes again.
 *
 * @see TomlTable#as(Class, TomlBindOptions)
 * @see MutableTomlTable#from(Object, TomlBindOptions)
 * @see MutableTomlTable#update(Object, TomlBindOptions)
 */
public final class TomlBindOptions {

  /**
   * How the name of a field or record component is turned into a TOML key.
   *
   * <p>
   * A {@link TomlName} annotation on the field or component overrides the key naming.
   */
  public enum KeyNaming {
    /**
     * The key is the name, unchanged: {@code maxConnections} is bound to {@code maxConnections}.
     */
    EXACT,
    /**
     * The words of the name, in lower case, joined by {@code _}: {@code maxConnections} is bound to
     * {@code max_connections}, and {@code httpURL} to {@code http_url}.
     */
    SNAKE_CASE,
    /**
     * The words of the name, in lower case, joined by {@code -}: {@code maxConnections} is bound to
     * {@code max-connections}, and {@code httpURL} to {@code http-url}.
     */
    KEBAB_CASE;

    String keyFor(String name) {
      switch (this) {
        case SNAKE_CASE:
          return joinWords(name, '_');
        case KEBAB_CASE:
          return joinWords(name, '-');
        default:
          return name;
      }
    }

    private static String joinWords(String name, char separator) {
      StringBuilder sb = new StringBuilder(name.length() + 4);
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (!Character.isUpperCase(c)) {
          sb.append(c);
          continue;
        }
        if (i > 0) {
          char previous = name.charAt(i - 1);
          boolean startsWord = Character.isLowerCase(previous) || Character.isDigit(previous);
          boolean endsAcronym =
              Character.isUpperCase(previous) && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
          if (startsWord || endsAcronym) {
            sb.append(separator);
          }
        }
        sb.append(Character.toLowerCase(c));
      }
      return sb.toString();
    }
  }

  // The default options are shared for as long as TomlJ is loaded, so they keep what they learn about a class with the
  // class itself: a class loader that is discarded, as on a redeploy, can then be unloaded
  private static final TomlBindOptions DEFAULTS = new TomlBindOptions(
      KeyNaming.EXACT,
      false,
      Collections.<Class<?>, Function<Object, ?>>emptyMap(),
      Collections.<Class<?>, Function<Object, ?>>emptyMap(),
      new OnClassCache<ObjectBinder.Binder>(),
      new OnClassCache<ObjectWriter.Writer>());

  private final KeyNaming keyNaming;
  private final boolean ignoresUnknownKeys;
  private final Map<Class<?>, Function<Object, ?>> converters;
  private final Map<Class<?>, Function<Object, ?>> writeConverters;
  private final TypeCache<ObjectBinder.Binder> binders;
  private final TypeCache<ObjectWriter.Writer> writers;

  private TomlBindOptions(
      KeyNaming keyNaming,
      boolean ignoresUnknownKeys,
      Map<Class<?>, Function<Object, ?>> converters,
      Map<Class<?>, Function<Object, ?>> writeConverters) {
    this(
        keyNaming,
        ignoresUnknownKeys,
        converters,
        writeConverters,
        new MapCache<ObjectBinder.Binder>(),
        new MapCache<ObjectWriter.Writer>());
  }

  private TomlBindOptions(
      KeyNaming keyNaming,
      boolean ignoresUnknownKeys,
      Map<Class<?>, Function<Object, ?>> converters,
      Map<Class<?>, Function<Object, ?>> writeConverters,
      TypeCache<ObjectBinder.Binder> binders,
      TypeCache<ObjectWriter.Writer> writers) {
    this.keyNaming = keyNaming;
    this.ignoresUnknownKeys = ignoresUnknownKeys;
    this.converters = converters;
    this.writeConverters = writeConverters;
    this.binders = binders;
    this.writers = writers;
  }

  /**
   * The default bind options: keys are the names of fields and record components, unchanged, a key with no field or
   * component is an error, and no converters are registered.
   *
   * @return The default bind options.
   */
  public static TomlBindOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Create a copy of these options that turns names into keys with a different key naming.
   *
   * @param keyNaming The key naming.
   * @return A new set of options with the given key naming.
   */
  public TomlBindOptions withKeyNaming(KeyNaming keyNaming) {
    requireNonNull(keyNaming);
    return new TomlBindOptions(keyNaming, ignoresUnknownKeys, converters, writeConverters);
  }

  /**
   * Create a copy of these options that ignores keys with no field or record component, or that reports them.
   *
   * <p>
   * By default, a key in a table that is bound to a record or class, and that names none of its fields or components,
   * is an error, which catches a misspelled key in a document.
   *
   * @param ignored {@code true} to ignore keys with no field or record component, {@code false} to report each as an
   *        error.
   * @return A new set of options.
   */
  public TomlBindOptions withUnknownKeysIgnored(boolean ignored) {
    return new TomlBindOptions(keyNaming, ignored, converters, writeConverters);
  }

  /**
   * Create a copy of these options that binds values to a type with a converter.
   *
   * <p>
   * The converter is given the value from the document: a {@code String}, {@code Long}, {@code Double},
   * {@code Boolean}, {@code OffsetDateTime}, {@code LocalDateTime}, {@code LocalDate}, {@code LocalTime},
   * {@link TomlArray} or {@link TomlTable}. It must return a value of the type, not {@code null}. If it throws a
   * {@code RuntimeException}, the value is reported as a {@link TomlBindError} with the exception's message.
   *
   * <p>
   * A converter is used for fields, components and elements declared with exactly this type, and takes the place of the
   * binding TomlJ would use otherwise. A converter registered earlier for the same type is replaced, and values of the
   * type cannot be written as TOML; see {@link #withConverter(Class, Function, Function)} to write them.
   *
   * <pre>{@code
   * TomlBindOptions options =
   *     TomlBindOptions.defaults().withConverter(Duration.class, value -> Duration.parse((String) value));
   * }</pre>
   *
   * @param type The type the converter produces.
   * @param converter The converter.
   * @param <T> The type the converter produces.
   * @return A new set of options with the given converter.
   */
  public <T> TomlBindOptions withConverter(Class<T> type, Function<Object, ? extends T> converter) {
    requireNonNull(type);
    requireNonNull(converter);
    Map<Class<?>, Function<Object, ?>> newConverters = new HashMap<>(converters);
    newConverters.put(type, converter);
    Map<Class<?>, Function<Object, ?>> newWriteConverters = new HashMap<>(writeConverters);
    newWriteConverters.remove(type);
    return new TomlBindOptions(
        keyNaming,
        ignoresUnknownKeys,
        Collections.unmodifiableMap(newConverters),
        Collections.unmodifiableMap(newWriteConverters));
  }

  /**
   * Create a copy of these options that binds values to a type with a converter, and writes values of the type with
   * another.
   *
   * <p>
   * {@code read} binds values as the converter given to {@link #withConverter(Class, Function)} does. {@code write} is
   * given a value of the type, never {@code null}, when an object is written as TOML, and returns the TOML value to
   * write for it: any value {@link MutableTomlTable#set(String, Object)} accepts, such as a {@code String}, a number, a
   * {@code Map}, a {@code List}, or a {@link TomlValue} to choose how it is written. If it throws a
   * {@code RuntimeException} or returns {@code null}, writing throws {@code IllegalArgumentException}.
   *
   * <p>
   * When a document is updated from an object, a value in the document is left as it is if {@code read} converts it to
   * a value equal to the one being written, so {@code write} does not have to return the same text as the document
   * holds.
   *
   * <pre>{@code
   * TomlBindOptions options = TomlBindOptions
   *     .defaults()
   *     .withConverter(Duration.class, value -> Duration.parse((String) value), Duration::toString);
   * }</pre>
   *
   * @param type The type the converters produce and write.
   * @param read The converter from TOML values.
   * @param write The converter to TOML values.
   * @param <T> The type the converters produce and write.
   * @return A new set of options with the given converters.
   */
  public <T> TomlBindOptions withConverter(
      Class<T> type,
      Function<Object, ? extends T> read,
      Function<? super T, ?> write) {
    requireNonNull(write);
    TomlBindOptions withRead = withConverter(type, read);
    Map<Class<?>, Function<Object, ?>> newWriteConverters = new HashMap<>(writeConverters);
    @SuppressWarnings("unchecked")
    Function<Object, ?> writer = (Function<Object, ?>) write;
    newWriteConverters.put(type, writer);
    return new TomlBindOptions(
        keyNaming,
        ignoresUnknownKeys,
        withRead.converters,
        Collections.unmodifiableMap(newWriteConverters));
  }

  /**
   * How names of fields and record components are turned into keys.
   *
   * @return The key naming.
   */
  public KeyNaming keyNaming() {
    return keyNaming;
  }

  /**
   * Whether a key with no field or record component is ignored.
   *
   * @return {@code true} if such keys are ignored.
   */
  public boolean ignoresUnknownKeys() {
    return ignoresUnknownKeys;
  }

  @Nullable
  Function<Object, ?> converterFor(Class<?> type) {
    return converters.get(type);
  }

  @Nullable
  Function<Object, ?> writeConverterFor(Class<?> type) {
    return writeConverters.get(type);
  }

  TypeCache<ObjectBinder.Binder> binders() {
    return binders;
  }

  TypeCache<ObjectWriter.Writer> writers() {
    return writers;
  }

  /**
   * The binder or writer made for each Java type, so that each is made once.
   */
  interface TypeCache<T> {
    @Nullable
    T get(Type type);

    void putIfAbsent(Type type, T value);
  }

  /**
   * Keeps every type, for as long as the options are kept.
   */
  private static final class MapCache<T> implements TypeCache<T> {
    private final Map<Type, T> values = new ConcurrentHashMap<>();

    @Override
    @Nullable
    public T get(Type type) {
      return values.get(type);
    }

    @Override
    public void putIfAbsent(Type type, T value) {
      values.putIfAbsent(type, value);
    }
  }

  /**
   * Keeps a class's value with the class, so it holds nothing that outlives the class. A generic type, such as
   * {@code List<Server>}, is not kept: its value would be kept with {@code List}, and would hold {@code Server}. A
   * binder or writer made for a class holds those it made for its members, so they are made once all the same.
   */
  private static final class OnClassCache<T> implements TypeCache<T> {
    private final ClassValue<AtomicReference<T>> values = new ClassValue<AtomicReference<T>>() {
      @Override
      protected AtomicReference<T> computeValue(Class<?> type) {
        return new AtomicReference<>();
      }
    };

    @Override
    @Nullable
    public T get(Type type) {
      return (type instanceof Class) ? values.get((Class<?>) type).get() : null;
    }

    @Override
    public void putIfAbsent(Type type, T value) {
      if (type instanceof Class) {
        values.get((Class<?>) type).compareAndSet(null, value);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlBindOptions)) {
      return false;
    }
    TomlBindOptions other = (TomlBindOptions) obj;
    return this.keyNaming == other.keyNaming
        && this.ignoresUnknownKeys == other.ignoresUnknownKeys
        && this.converters.equals(other.converters)
        && this.writeConverters.equals(other.writeConverters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyNaming, ignoresUnknownKeys, converters, writeConverters);
  }

  @Override
  public String toString() {
    return "TomlBindOptions{keyNaming="
        + keyNaming
        + ", ignoresUnknownKeys="
        + ignoresUnknownKeys
        + ", converters="
        + converters.keySet()
        + ", writeConverters="
        + writeConverters.keySet()
        + '}';
  }
}
