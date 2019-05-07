/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class MutableTomlArray implements TomlArray {

  private static class Element {
    final Object value;
    final TomlPosition position;

    private Element(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }
  }

  static final TomlArray EMPTY = new MutableTomlArray(true);
  private final List<Element> elements = new ArrayList<>();
  private final boolean definedAsLiteral;
  private TomlType type = null;

  MutableTomlArray() {
    this(false);
  }

  MutableTomlArray(boolean definedAsLiteral) {
    this.definedAsLiteral = definedAsLiteral;
  }

  boolean wasDefinedAsLiteral() {
    return definedAsLiteral;
  }

  @Override
  public int size() {
    return elements.size();
  }

  @Override
  public boolean isEmpty() {
    return type == null;
  }

  @Override
  public boolean containsStrings() {
    return type == null || type == TomlType.STRING;
  }

  @Override
  public boolean containsLongs() {
    return type == null || type == TomlType.INTEGER;
  }

  @Override
  public boolean containsDoubles() {
    return type == null || type == TomlType.FLOAT;
  }

  @Override
  public boolean containsBooleans() {
    return type == null || type == TomlType.BOOLEAN;
  }

  @Override
  public boolean containsOffsetDateTimes() {
    return type == null || type == TomlType.OFFSET_DATE_TIME;
  }

  @Override
  public boolean containsLocalDateTimes() {
    return type == null || type == TomlType.LOCAL_DATE_TIME;
  }

  @Override
  public boolean containsLocalDates() {
    return type == null || type == TomlType.LOCAL_DATE;
  }

  @Override
  public boolean containsLocalTimes() {
    return type == null || type == TomlType.LOCAL_TIME;
  }

  @Override
  public boolean containsArrays() {
    return type == null || type == TomlType.ARRAY;
  }

  @Override
  public boolean containsTables() {
    return type == null || type == TomlType.TABLE;
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
    requireNonNull(value);
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }

    TomlType origType = type;
    Optional<TomlType> valueType = TomlType.typeFor(value);
    if (!valueType.isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + value.getClass().getSimpleName());
    }
    if (type != null) {
      if (valueType.get() != type) {
        throw new TomlInvalidTypeException(
            "Cannot add a " + TomlType.typeNameFor(value) + " to an array containing " + type.typeName() + "s");
      }
    } else {
      type = valueType.get();
    }

    try {
      elements.add(new Element(value, position));
    } catch (Throwable e) {
      type = origType;
      throw e;
    }
    return this;
  }

  @Override
  public List<Object> toList() {
    return elements.stream().map(e -> e.value).collect(Collectors.toList());
  }
}
