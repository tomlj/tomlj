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
import static org.tomlj.JsonOptions.ALL_VALUES_AS_STRINGS;
import static org.tomlj.JsonOptions.VALUES_AS_OBJECTS_WITH_TYPE;
import static org.tomlj.TomlType.TABLE;
import static org.tomlj.TomlType.typeFor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class JsonSerializer {
  private JsonSerializer() {}

  static void toJson(TomlTable table, Appendable appendable, Set<JsonOptions> options) throws IOException {
    requireNonNull(table);
    requireNonNull(appendable);
    toJson(table, appendable, options, 0);
    appendable.append(System.lineSeparator());
  }

  private static void toJson(TomlTable table, Appendable appendable, Set<JsonOptions> options, int indent)
      throws IOException {
    if (table.isEmpty()) {
      appendable.append("{}");
      return;
    }
    appendLine(appendable, "{");
    for (Iterator<Map.Entry<String, Object>> iterator = table.entrySet().stream().iterator(); iterator.hasNext();) {
      Map.Entry<String, Object> entry = iterator.next();
      String key = entry.getKey();
      append(appendable, indent + 2, "\"" + escape(key) + "\" : ");
      Object value = entry.getValue();
      assert value != null;
      appendTomlValue(value, appendable, options, indent);
      if (iterator.hasNext()) {
        appendable.append(",");
        appendable.append(System.lineSeparator());
      }
    }
    appendable.append(System.lineSeparator());
    append(appendable, indent, "}");
  }

  static void toJson(TomlArray array, Appendable appendable, Set<JsonOptions> options) throws IOException {
    toJson(array, appendable, options, 0);
    appendable.append(System.lineSeparator());
  }

  private static void toJson(TomlArray array, Appendable appendable, Set<JsonOptions> options, int indent)
      throws IOException {
    if (array.isEmpty()) {
      appendable.append("[]");
      return;
    }

    appendable.append("[");
    Optional<TomlType> tomlType = Optional.empty();
    for (Iterator<Object> iterator = array.toList().iterator(); iterator.hasNext();) {
      Object tomlValue = iterator.next();
      tomlType = typeFor(tomlValue);
      assert tomlType.isPresent();
      if (tomlType.get().equals(TABLE)) {
        toJson((TomlTable) tomlValue, appendable, options, indent);
      } else {
        appendable.append(System.lineSeparator());
        indentLine(appendable, indent + 2);
        appendTomlValue(tomlValue, appendable, options, indent);
      }

      if (iterator.hasNext()) {
        appendable.append(",");
      } else if (!tomlType.get().equals(TABLE)) {
        appendable.append(System.lineSeparator());
      }
    }
    if (tomlType.isPresent() && tomlType.get().equals(TABLE)) {
      appendable.append("]");
    } else {
      append(appendable, indent, "]");
    }
  }

  private static void appendTomlValue(Object value, Appendable appendable, Set<JsonOptions> options, int indent)
      throws IOException {
    Optional<TomlType> tomlType = typeFor(value);
    assert tomlType.isPresent();
    switch (tomlType.get()) {
      case ARRAY:
        toJson((TomlArray) value, appendable, options, indent + 2);
        return;
      case TABLE:
        toJson((TomlTable) value, appendable, options, indent + 2);
        return;
      default:
        // continue
    }

    if (options.contains(VALUES_AS_OBJECTS_WITH_TYPE)) {
      appendable.append("{ \"type\": \"");
      appendable.append(typeName(tomlType.get()));
      appendable.append("\", \"value\": ");
      appendTomlValueLiteral(tomlType.get(), value, appendable, options);
      appendable.append(" }");
    } else {
      appendTomlValueLiteral(tomlType.get(), value, appendable, options);
    }
  }

  private static String typeName(TomlType tomlType) {
    switch (tomlType) {
      case BOOLEAN:
        return "bool";
      case OFFSET_DATE_TIME:
        return "datetime";
      case LOCAL_DATE_TIME:
        return "datetime-local";
      case LOCAL_DATE:
        return "date-local";
      case LOCAL_TIME:
        return "time-local";
      default:
        return tomlType.typeName();
    }
  }

  private static void appendTomlValueLiteral(
      TomlType tomlType,
      Object value,
      Appendable appendable,
      Set<JsonOptions> options) throws IOException {
    switch (tomlType) {
      case STRING:
        appendable.append('"');
        appendable.append(escape((String) value));
        appendable.append('"');
        break;
      case INTEGER:
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        appendable.append(value.toString());
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        break;
      case FLOAT:
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        if (Double.isNaN((Double) value)) {
          appendable.append("nan");
        } else if ((Double) value == Double.POSITIVE_INFINITY) {
          appendable.append("+inf");
        } else if ((Double) value == Double.NEGATIVE_INFINITY) {
          appendable.append("-inf");
        } else {
          appendable.append(value.toString());
        }
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        break;
      case BOOLEAN:
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        appendable.append(((Boolean) value) ? "true" : "false");
        if (options.contains(ALL_VALUES_AS_STRINGS)) {
          appendable.append('"');
        }
        break;
      case OFFSET_DATE_TIME:
        appendable.append('"');
        appendable.append(((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        appendable.append('"');
        break;
      case LOCAL_DATE_TIME:
        appendable.append('"');
        appendable.append(((LocalDateTime) value).format(DateTimeFormatter.ISO_DATE_TIME));
        appendable.append('"');
        break;
      case LOCAL_DATE:
        appendable.append('"');
        appendable.append(((LocalDate) value).format(DateTimeFormatter.ISO_DATE));
        appendable.append('"');
        break;
      case LOCAL_TIME:
        appendable.append('"');
        appendable.append(((LocalTime) value).format(DateTimeFormatter.ISO_TIME));
        appendable.append('"');
        break;
      default:
        throw new AssertionError("Attempted to output literal form of non-literal type " + tomlType.typeName());
    }
  }

  private static void append(Appendable appendable, int indent, String line) throws IOException {
    indentLine(appendable, indent);
    appendable.append(line);
  }

  private static void appendLine(Appendable appendable, String line) throws IOException {
    appendable.append(line);
    appendable.append(System.lineSeparator());
  }

  private static void indentLine(Appendable appendable, int indent) throws IOException {
    for (int i = 0; i < indent; ++i) {
      appendable.append(' ');
    }
  }

  private static StringBuilder escape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '"') {
        out.append("\\\"");
        continue;
      }
      if (ch == '\\') {
        out.append("\\\\");
        continue;
      }
      if (ch >= 0x20) {
        out.append(ch);
        continue;
      }

      switch (ch) {
        case '\t':
          out.append("\\t");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\f':
          out.append("\\f");
          break;
        default:
          out.append("\\u").append(String.format("%04x", text.codePointAt(i)));
      }
    }
    return out;
  }
}
