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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Anything a key or an array slot holds: a scalar, a table or an array.
 *
 * <p>
 * A value is the data an entry ({@link Entry}) holds; it never carries comments of its own. The comments written around
 * it belong to its entry, read through {@link TomlEntry#comments()}.
 */
abstract class Value implements TomlValue {

  /**
   * The value as the public API exposes it: the wrapped scalar, or the table or array itself.
   *
   * @return The value.
   */
  @Override
  public abstract Object get();

  /**
   * Wrap a value read from the document as a {@code Value}, for an entry to hold.
   *
   * @param value The value: a scalar such as a {@code Long} or {@code String}, or a {@link LinkedTomlTable} /
   *        {@link ListTomlArray}, which is already a {@link Value}.
   * @param position The position of the value in the document, or {@code null} if the value did not come from one.
   * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it.
   */
  static Value of(Object value, @Nullable TomlPosition position) {
    return (value instanceof Value) ? (Value) value : new Scalar(value, position);
  }

  /**
   * Wrap a copy of a {@link TomlValue}, keeping the record of where the original was written.
   *
   * @param original The value being copied.
   * @param normalized The value to wrap, as {@link TomlValues#normalize} gives it: a copy for a table or an array, the
   *        scalar itself otherwise.
   * @return {@code normalized} itself if it is already a {@link Value}, otherwise a new {@link Scalar} with no
   *         position, sharing {@code original}'s span if it has one.
   */
  static Value copyOf(TomlValue original, Object normalized) {
    if (normalized instanceof Value) {
      return (Value) normalized;
    }
    Scalar copy = new Scalar(normalized, null);
    if (original instanceof Scalar) {
      copy.span = ((Scalar) original).span;
    }
    return copy;
  }

  /**
   * A scalar value: a string, integer, float, boolean or date/time.
   */
  static final class Scalar extends Value {

    private final Object value;

    // Nullable: a scalar set through the editing API has no input position.
    private final @Nullable TomlPosition position;

    // Where the literal of this scalar was written in the document it was read from. Null for a scalar set through the
    // editing API, and for one read from a document parsed with no source kept.
    @Nullable
    ValueSpan span;

    Scalar(Object value, @Nullable TomlPosition position) {
      this.value = value;
      this.position = position;
    }

    @Override
    public Object get() {
      return value;
    }

    @Override
    @Nullable
    public TomlPosition position() {
      return position;
    }
  }
}
