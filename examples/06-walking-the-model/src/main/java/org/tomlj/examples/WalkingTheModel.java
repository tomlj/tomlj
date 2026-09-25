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
package org.tomlj.examples;

import org.tomlj.JsonOptions;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Walks a document whose structure is not known in advance, then converts it to JSON.
 */
public final class WalkingTheModel {

  public static void main(String[] args) throws IOException {
    TomlParseResult catalog = Toml.parse(Path.of(args.length > 0 ? args[0] : "catalog.toml"));
    if (catalog.hasErrors()) {
      catalog.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    System.out.println("--- the tree");
    printTable(catalog, "");

    System.out.println("--- as JSON");
    System.out.print(catalog.toJson());

    // With VALUES_AS_OBJECTS_WITH_TYPE, each value is written as {"type": ..., "value": ...}, keeping the TOML type
    // that JSON has no form for.
    System.out.println("--- as JSON with TOML types");
    System.out.print(catalog.getTable("discounts").toJson(JsonOptions.VALUES_AS_OBJECTS_WITH_TYPE));
  }

  private static void printTable(TomlTable table, String indent) {
    // entrySet() lists the entries of this table only, in document order, with the keys unquoted.
    for (Map.Entry<String, Object> entry : table.entrySet()) {
      printValue(entry.getKey(), entry.getValue(), indent);
    }
  }

  private static void printArray(TomlArray array, String indent) {
    for (int i = 0; i < array.size(); i++) {
      printValue("[" + i + "]", array.get(i), indent);
    }
  }

  private static void printValue(String name, Object value, String indent) {
    // Every value is one of these ten types.
    if (value instanceof TomlTable table) {
      System.out.println(indent + name + ": table");
      printTable(table, indent + "  ");
    } else if (value instanceof TomlArray array) {
      System.out.println(indent + name + ": array of " + array.size());
      printArray(array, indent + "  ");
    } else {
      System.out.println(indent + name + ": " + typeName(value) + " " + value);
    }
  }

  private static String typeName(Object value) {
    if (value instanceof String) {
      return "string";
    } else if (value instanceof Long) {
      return "integer";
    } else if (value instanceof Double) {
      return "float";
    } else if (value instanceof Boolean) {
      return "boolean";
    } else if (value instanceof OffsetDateTime) {
      return "offset date-time";
    } else if (value instanceof LocalDateTime) {
      return "local date-time";
    } else if (value instanceof LocalDate) {
      return "local date";
    } else if (value instanceof LocalTime) {
      return "local time";
    }
    throw new IllegalArgumentException("not a TOML value: " + value);
  }
}
