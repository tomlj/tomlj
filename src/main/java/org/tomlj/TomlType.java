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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

enum TomlType {
  STRING("string", String.class),
  INTEGER("integer", Long.class),
  FLOAT("float", Double.class),
  BOOLEAN("boolean", Boolean.class),
  OFFSET_DATE_TIME("offset date-time", OffsetDateTime.class),
  LOCAL_DATE_TIME("local date-time", LocalDateTime.class),
  LOCAL_DATE("local date", LocalDate.class),
  LOCAL_TIME("local time", LocalTime.class),
  ARRAY("array", TomlArray.class),
  TABLE("table", TomlTable.class);

  private final String name;
  private final Class<?> clazz;

  TomlType(String name, Class<?> clazz) {
    this.name = name;
    this.clazz = clazz;
  }

  static Optional<TomlType> typeFor(Object obj) {
    return typeForClass(obj.getClass());
  }

  static Optional<TomlType> typeForClass(Class<?> clazz) {
    return Arrays.stream(values()).filter(t -> t.clazz.isAssignableFrom(clazz)).findAny();
  }

  static String typeNameFor(Object obj) {
    return typeNameForClass(obj.getClass());
  }

  static String typeNameForClass(Class<?> clazz) {
    return typeForClass(clazz).map(t -> t.name).orElseGet(clazz::getSimpleName);
  }

  public String typeName() {
    return name;
  }
}
