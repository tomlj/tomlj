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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class MutableTomlArray extends ElementContainer<Entry.Value> implements TomlArray {

  // The values of this array, in document order; the index into this list is the array index. This is a separate
  // index over the same value elements that elements() holds, kept for lookup by position.
  private final List<Entry.Value> values = new ArrayList<>();
  private final boolean isTableArray;

  // Final, unlike a table's: an array only ever comes from a literal written in the document, at a position known
  // as soon as it is created, whereas a table can also be created implicitly by a dotted key and defined later.
  private final TomlPosition position;

  /**
   * Create an array for an array written in a document.
   *
   * @param isTableArray {@code true} if this array holds the tables of a {@code [[x]]} header.
   * @param position The position of the array's opening {@code [}, or of its first {@code [[x]]} header if it is an
   *        array of tables.
   */
  MutableTomlArray(boolean isTableArray, TomlPosition position) {
    this.isTableArray = isTableArray;
    this.position = position;
  }

  boolean isTableArray() {
    return isTableArray;
  }

  @Override
  public TomlPosition position() {
    return position;
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public boolean isEmpty() {
    return values.isEmpty();
  }

  @Override
  public Entry.Value entry(int index) {
    return values.get(index);
  }

  /**
   * Append a value to this array's sequence, and index it by position.
   *
   * @param value The value.
   * @param position The input position.
   * @return This array.
   */
  MutableTomlArray append(Object value, TomlPosition position) {
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }
    return append(Entry.Value.of(value, position));
  }

  /**
   * Append a value to this array's sequence, and index it by position.
   *
   * @param value The value, already wrapped as an element with its comments attached; see
   *        {@link Entry.Value#of(Object, TomlPosition, List)}.
   * @return This array.
   */
  MutableTomlArray append(Entry.Value value) {
    Object rawValue = value.get();
    if (!TomlType.typeFor(rawValue).isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + rawValue.getClass().getSimpleName());
    }
    add(value);
    values.add(value);
    return this;
  }

  @Override
  public List<Object> toList() {
    return values.stream().map(Entry.Value::get).collect(Collectors.toList());
  }
}
