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

import static org.tomlj.TomlVersion.V0_5_0;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class MutableTomlArray extends ElementContainer<Element.Value> implements TomlArray {

  /**
   * Create an array for an array written in a document.
   *
   * @param version The TOML version.
   * @param position The position of the array's opening {@code [}, or of its first {@code [[x]]} header if it is an
   *        array of tables.
   * @return A new array.
   */
  static MutableTomlArray create(TomlVersion version, TomlPosition position) {
    return create(version, position, false);
  }

  /**
   * Create an array for an array written in a document.
   *
   * @param version The TOML version.
   * @param position The position of the array's opening {@code [}, or of its first {@code [[x]]} header if it is an
   *        array of tables.
   * @param tableArray {@code true} if this array holds the tables of a {@code [[x]]} header.
   * @return A new array.
   */
  static MutableTomlArray create(TomlVersion version, TomlPosition position, boolean tableArray) {
    return version.after(V0_5_0) ? new MutableTomlArray(tableArray, position)
        : new MutableHomogeneousTomlArray(tableArray, position);
  }

  // The values of this array, in document order; the index into this list is the array index. This is a separate
  // index over the same value elements that elements() holds, kept for lookup by position.
  private final List<Element.Value> values = new ArrayList<>();
  private final boolean isTableArray;

  // Final, unlike a table's: an array only ever comes from a literal written in the document, at a position known
  // as soon as it is created, whereas a table can also be created implicitly by a dotted key and defined later.
  private final TomlPosition position;

  MutableTomlArray(boolean isTableArray, TomlPosition position) {
    this.isTableArray = isTableArray;
    this.position = position;
  }

  boolean isTableArray() {
    return isTableArray;
  }

  @Override
  TomlPosition position() {
    return position;
  }

  @Override
  public boolean containsStrings() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsLongs() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsDoubles() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsBooleans() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsOffsetDateTimes() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsLocalDateTimes() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsLocalDates() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsLocalTimes() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsArrays() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
  }

  @Override
  public boolean containsTables() {
    throw new UnsupportedOperationException("Deprecated (after 0.5.0, arrays are heterogeneous)");
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
  public Object get(int index) {
    return values.get(index).get();
  }

  @Override
  public TomlPosition inputPositionOf(int index) {
    return values.get(index).position();
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
    return append(Element.Value.of(value, position));
  }

  /**
   * Append a value to this array's sequence, and index it by position.
   *
   * @param value The value, already wrapped as an element with its comments attached; see
   *        {@link Element.Value#of(Object, TomlPosition, List)}.
   * @return This array.
   */
  MutableTomlArray append(Element.Value value) {
    Object rawValue = value.get();
    if (!TomlType.typeFor(rawValue).isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + rawValue.getClass().getSimpleName());
    }
    add(value);
    values.add(value);
    return this;
  }

  @Override
  public List<TomlComment> comments(int index) {
    return values.get(index).attachedComments();
  }

  @Override
  public List<Object> toList() {
    return values.stream().map(Element.Value::get).collect(Collectors.toList());
  }
}
