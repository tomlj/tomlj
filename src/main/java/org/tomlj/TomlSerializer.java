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

import static java.util.Objects.requireNonNull;
import static org.tomlj.TomlType.ARRAY;
import static org.tomlj.TomlType.TABLE;
import static org.tomlj.TomlType.typeFor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class TomlSerializer {
  private TomlSerializer() {}

  static void toToml(TomlTable table, Appendable appendable) throws IOException {
    requireNonNull(table);
    requireNonNull(appendable);
    toToml(table, appendable, -2, "");
  }

  private static void toToml(TomlTable table, Appendable appendable, int indent, String path)
      throws IOException {
    final List<Map.Entry<String, Object>> entryListSorted =
        table.entrySet().stream()
            .sorted(
                Comparator.comparing(
                    entry -> {
                      final TomlType tomlType = typeFor(entry.getValue()).get();
                      return tomlType.equals(TABLE)
                              || (tomlType.equals(ARRAY)
                                  && isTableArray((TomlArray) entry.getValue()))
                          ? 1
                          : 0;
                    }))
            .collect(Collectors.toList());

    for (Map.Entry<String, Object> entry : entryListSorted) {
      String key = entry.getKey();
      Object value = entry.getValue();

      key = Toml.tomlEscape(key).toString();
      if (!key.matches("[a-zA-Z0-9_-]*")) {
        key = "\"" + key + "\"";
      }

      String newPath = (path.isEmpty() ? "" : path + ".") + key;

      Optional<TomlType> tomlType = typeFor(value);
      assert tomlType.isPresent();

      boolean isTableArray = tomlType.get().equals(ARRAY) && isTableArray((TomlArray) value);

      if (tomlType.get().equals(TABLE)) {
        append(appendable, indent + 2, "[" + newPath + "]");
        appendable.append(System.lineSeparator());
      } else if (!isTableArray) {
        append(appendable, indent + 2, key + "=");
      }

      appendTomlValue(value, appendable, indent, newPath);
      if (!tomlType.get().equals(TABLE) && !isTableArray) {
        appendable.append(System.lineSeparator());
      }
    }
  }

  static void toToml(TomlArray array, Appendable appendable) throws IOException {
    requireNonNull(array);
    requireNonNull(appendable);
    toToml(array, appendable, 0, "");
  }

  private static void toToml(TomlArray array, Appendable appendable, int indent, String path) throws IOException {
    boolean tableArray = isTableArray(array);
    if (!tableArray) {
      appendable.append("[");
      if (!array.isEmpty()) {
        appendable.append(System.lineSeparator());
      }
    }

    for (Iterator<Object> iterator = array.toList().iterator(); iterator.hasNext();) {
      Object tomlValue = iterator.next();
      Optional<TomlType> tomlType = typeFor(tomlValue);
      assert tomlType.isPresent();
      if (tomlType.get().equals(TABLE)) {
        append(appendable, indent, "[[" + path + "]]");
        appendable.append(System.lineSeparator());
        toToml((TomlTable) tomlValue, appendable, indent, path);
      } else {
        indentLine(appendable, indent + 2);
        appendTomlValue(tomlValue, appendable, indent, path);
      }

      if (!tableArray) {
        if (iterator.hasNext()) {
          appendable.append(",");
        }
        appendable.append(System.lineSeparator());
      }
    }
    if (!tableArray) {
      append(appendable, indent, "]");
    }
  }

  private static void appendTomlValue(Object value, Appendable appendable, int indent, String path) throws IOException {
    Optional<TomlType> tomlType = typeFor(value);
    assert tomlType.isPresent();
    switch (tomlType.get()) {
      case STRING:
        append(appendable, 0, "\"" + Toml.tomlEscape((String) value) + "\"");
        break;
      case INTEGER:
      case FLOAT:
        append(appendable, 0, value.toString());
        break;
      case OFFSET_DATE_TIME:
        append(appendable, 0, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((OffsetDateTime) value));
        break;
      case LOCAL_DATE_TIME:
        append(appendable, 0, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((LocalDateTime) value));
        break;
      case LOCAL_DATE:
        append(appendable, 0, DateTimeFormatter.ISO_LOCAL_DATE.format((LocalDate) value));
        break;
      case LOCAL_TIME:
        append(appendable, 0, DateTimeFormatter.ISO_LOCAL_TIME.format((LocalTime) value));
        break;
      case BOOLEAN:
        append(appendable, 0, ((Boolean) value) ? "true" : "false");
        break;
      case ARRAY:
        toToml((TomlArray) value, appendable, indent + 2, path);
        break;
      case TABLE:
        toToml((TomlTable) value, appendable, indent + 2, path);
        break;
    }
  }

  private static void append(Appendable appendable, int indent, String line) throws IOException {
    indentLine(appendable, indent);
    appendable.append(line);
  }

  private static void indentLine(Appendable appendable, int indent) throws IOException {
    for (int i = 0; i < indent; ++i) {
      appendable.append(' ');
    }
  }

  private static boolean isTableArray(TomlArray array) {
    for (Object tomlValue : array.toList()) {
      Optional<TomlType> tomlType = typeFor(tomlValue);
      assert tomlType.isPresent();
      if (tomlType.get().equals(TABLE)) {
        return true;
      }
    }
    return false;
  }
}
