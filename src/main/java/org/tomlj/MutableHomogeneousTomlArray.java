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


import java.util.Optional;

final class MutableHomogeneousTomlArray extends MutableTomlArray {

  // The one type every value holds, or null while the array is empty: an empty array contains no type, so each
  // containsX() answers false until the first value is appended.
  private TomlType type = null;

  MutableHomogeneousTomlArray(boolean tableArray, TomlPosition position) {
    super(tableArray, position);
  }

  @Override
  public boolean containsStrings() {
    return type == TomlType.STRING;
  }

  @Override
  public boolean containsLongs() {
    return type == TomlType.INTEGER;
  }

  @Override
  public boolean containsDoubles() {
    return type == TomlType.FLOAT;
  }

  @Override
  public boolean containsBooleans() {
    return type == TomlType.BOOLEAN;
  }

  @Override
  public boolean containsOffsetDateTimes() {
    return type == TomlType.OFFSET_DATE_TIME;
  }

  @Override
  public boolean containsLocalDateTimes() {
    return type == TomlType.LOCAL_DATE_TIME;
  }

  @Override
  public boolean containsLocalDates() {
    return type == TomlType.LOCAL_DATE;
  }

  @Override
  public boolean containsLocalTimes() {
    return type == TomlType.LOCAL_TIME;
  }

  @Override
  public boolean containsArrays() {
    return type == TomlType.ARRAY;
  }

  @Override
  public boolean containsTables() {
    return type == TomlType.TABLE;
  }

  @Override
  public MutableHomogeneousTomlArray append(Element.Value value) {
    Object rawValue = value.get();
    TomlType origType = type;
    Optional<TomlType> valueType = TomlType.typeFor(rawValue);
    if (!valueType.isPresent()) {
      throw new IllegalArgumentException("Unsupported type " + rawValue.getClass().getSimpleName());
    }
    if (type != null) {
      if (valueType.get() != type) {
        throw new TomlInvalidTypeException(
            "Cannot add a " + TomlType.typeNameFor(rawValue) + " to an array containing " + type.typeName() + "s");
      }
    } else {
      type = valueType.get();
    }

    try {
      super.append(value);
    } catch (Throwable e) {
      type = origType;
      throw e;
    }
    return this;
  }
}
