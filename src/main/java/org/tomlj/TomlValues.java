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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * How the editing API ({@link MutableTomlTable}, {@link MutableTomlArray}) accepts a value handed to it.
 *
 * <p>
 * A table or array stored as a value is always stored as a deep copy: a {@link LinkedTomlTable} or
 * {@link ListTomlArray} is copied even when it is not currently stored anywhere, a foreign {@link TomlTable} or
 * {@link TomlArray} implementation is copied because this library cannot assume it stays unchanged underneath the copy
 * holding it, and a {@link Map} or {@link Collection} is converted because it is not one of the model's own container
 * types at all. A copy can never contain the table or array it is being stored into, so there is nothing to check for a
 * cycle.
 */
final class TomlValues {

  private TomlValues() {}

  /**
   * Accept a value handed to the editing API, ready to be wrapped as an entry's value with {@link Value#of}.
   *
   * @param value The value.
   * @return A deep copy of {@code value} if it is a {@link TomlTable} or a {@link TomlArray}, made as
   *         {@link MutableTomlTable#copyOf(TomlTable)} and {@link MutableTomlArray#copyOf(TomlArray)} make one, whoever
   *         implemented it; a {@link LinkedTomlTable} for a {@link Map} or a {@link ListTomlArray} for a
   *         {@link Collection}, their values converted the same way; a widened {@code Long} or {@code Double} for an
   *         {@code Integer}/{@code Short}/{@code Byte} or a {@code Float}; or {@code value} itself for any other
   *         accepted scalar type.
   * @throws NullPointerException If {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value.
   */
  static Object normalize(Object value) {
    if (value == null) {
      throw new NullPointerException("TOML has no null value; remove the entry instead of setting it to null");
    }
    if (value instanceof TomlTable) {
      return MutableTomlTable.copyOf((TomlTable) value);
    }
    if (value instanceof TomlArray) {
      return MutableTomlArray.copyOf((TomlArray) value);
    }
    if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
      return ((Number) value).longValue();
    }
    if (value instanceof Float) {
      return ((Float) value).doubleValue();
    }
    if (value instanceof Map) {
      return copyMap((Map<?, ?>) value);
    }
    if (value instanceof Collection) {
      return copyCollection((Collection<?>) value);
    }
    if (TomlType.typeFor(value).isPresent()) {
      return value;
    }
    throw new IllegalArgumentException("Cannot convert a " + value.getClass().getSimpleName() + " to a TOML value");
  }

  // Built with set() rather than by touching the new table's entries directly, so that a value nested in the map is
  // normalized and reported on error exactly as a top-level one is.
  private static LinkedTomlTable copyMap(Map<?, ?> map) {
    LinkedTomlTable table = new LinkedTomlTable();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object rawKey = entry.getKey();
      if (!(rawKey instanceof String)) {
        throw new IllegalArgumentException("Cannot convert a Map with a non-String key to a table");
      }
      table.set(Collections.singletonList((String) rawKey), entry.getValue());
    }
    return table;
  }

  // Built with add(), for the same reason copyMap is built with set().
  private static ListTomlArray copyCollection(Collection<?> collection) {
    ListTomlArray array = new ListTomlArray(false, null);
    for (Object item : collection) {
      array.add(item);
    }
    return array;
  }
}
