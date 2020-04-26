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

class MutableTomlArray implements TomlArray {

  static MutableTomlArray create(TomlVersion version) {
    return create(version, false);
  }

  static MutableTomlArray create(TomlVersion version, boolean tableArray) {
    return version.after(V0_5_0) ? new MutableTomlArray(tableArray) : new MutableHomogeneousTomlArray(tableArray);
  }

  private static class Element {
    final Object value;
    final TomlPosition position;

    private Element(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }
  }

  private final List<Element> elements = new ArrayList<>();
  private final boolean isTableArray;

  MutableTomlArray(boolean isTableArray) {
    this.isTableArray = isTableArray;
  }

  boolean isTableArray() {
    return isTableArray;
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
    return elements.size();
  }

  @Override
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  @Override
  public Object get(int index) {
    return elements.get(index).value;
  }

  @Override
  public TomlPosition inputPositionOf(int index) {
    return elements.get(index).position;
  }

  MutableTomlArray append(Object value, TomlPosition position) {
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }

    if (!TomlType.typeFor(value).isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + value.getClass().getSimpleName());
    }

    elements.add(new Element(value, position));
    return this;
  }

  @Override
  public List<Object> toList() {
    return elements.stream().map(e -> e.value).collect(Collectors.toList());
  }
}
