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
   * @param position The position of the value in the document.
   * @return {@code value} itself if it is already a {@link Value}, otherwise a new {@link Scalar} wrapping it.
   */
  static Value of(Object value, TomlPosition position) {
    return (value instanceof Value) ? (Value) value : new Scalar(value, position);
  }

  /**
   * A scalar value: a string, integer, float, boolean or date/time.
   */
  static final class Scalar extends Value {

    private final Object value;
    private final TomlPosition position;

    Scalar(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }

    @Override
    public Object get() {
      return value;
    }

    @Override
    public TomlPosition position() {
      return position;
    }
  }
}
