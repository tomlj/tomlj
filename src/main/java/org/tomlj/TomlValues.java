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
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
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
   *         {@link MutableTomlTable#copyOf(TomlTable)} and {@link MutableTomlArray#copyOf(TomlArray)} make one, for any
   *         implementation; a {@link LinkedTomlTable} for a {@link Map} or a {@link ListTomlArray} for a
   *         {@link Collection}, their values converted the same way; what a {@link TomlValue} holds, converted the same
   *         way; a widened {@code Long} or {@code Double} for an {@code Integer}/{@code Short}/{@code Byte} or a
   *         {@code Float}; or {@code value} itself for any other accepted scalar type.
   * @throws NullPointerException If {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value.
   */
  static Object normalize(Object value) {
    if (value == null) {
      throw new NullPointerException("TOML has no null value; remove the entry instead of setting it to null");
    }
    if (value instanceof String) {
      checkNoUnpairedSurrogate((String) value, "String");
    } else if (value instanceof OffsetDateTime) {
      OffsetDateTime dateTime = (OffsetDateTime) value;
      checkYear(dateTime.getYear());
      if (dateTime.getOffset().getTotalSeconds() % 60 != 0) {
        throw new IllegalArgumentException("Offset has a non-zero seconds part");
      }
    } else if (value instanceof LocalDateTime) {
      checkYear(((LocalDateTime) value).getYear());
    } else if (value instanceof LocalDate) {
      checkYear(((LocalDate) value).getYear());
    }
    if (value instanceof TomlTable) {
      return MutableTomlTable.copyOf((TomlTable) value);
    }
    if (value instanceof TomlArray) {
      return MutableTomlArray.copyOf((TomlArray) value);
    }
    if (value instanceof TomlValue) {
      // A scalar TomlValue, such as one read from an entry: what it holds is stored, with the record of where its
      // literal was written
      TomlValue original = (TomlValue) value;
      return Value.copyOf(original, normalize(original.get()));
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

  /**
   * An integer to be written in a base other than ten; see {@link TomlValue#hex(long)}.
   *
   * @param value The integer.
   * @param prefix The prefix of the notation: {@code 0x}, {@code 0o} or {@code 0b}.
   * @param radix The base.
   * @param uppercase Whether a digit beyond 9 is written as an uppercase letter.
   * @return The value, carrying the text.
   * @throws IllegalArgumentException If {@code value} is negative, since TOML writes no sign before a prefix.
   */
  static TomlValue inBase(long value, String prefix, int radix, boolean uppercase) {
    if (value < 0) {
      throw new IllegalArgumentException("A negative integer cannot be written with the " + prefix + " prefix");
    }
    String digits = Long.toString(value, radix);
    return Value.withText(value, prefix + (uppercase ? digits.toUpperCase(Locale.ROOT) : digits));
  }

  /**
   * An integer to be written in decimal with its digits grouped in threes by underscores; see
   * {@link TomlValue#grouped(long)}.
   *
   * @param value The integer.
   * @return The value, carrying the text.
   */
  static TomlValue grouped(long value) {
    String digits = Long.toString(value);
    if (value < 0) {
      digits = digits.substring(1);
    }
    StringBuilder text = new StringBuilder();
    if (value < 0) {
      text.append('-');
    }
    int lead = digits.length() % 3;
    for (int i = 0; i < digits.length(); i++) {
      if (i > 0 && (i - lead) % 3 == 0) {
        text.append('_');
      }
      text.append(digits.charAt(i));
    }
    return Value.withText(value, text.toString());
  }

  /**
   * A string to be written as a literal string, between apostrophes with no escaping; see
   * {@link TomlValue#literal(String)}.
   *
   * @param value The string.
   * @return The value, carrying the text.
   * @throws IllegalArgumentException If {@code value} holds an apostrophe, a newline or a control character other than
   *         tab, none of which a literal string can hold.
   */
  static TomlValue literal(String value) {
    checkNoUnpairedSurrogate(value, "String");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\'') {
        throw new IllegalArgumentException("A literal string cannot hold an apostrophe");
      }
      if (isControl(c)) {
        throw new IllegalArgumentException("A literal string cannot hold a newline or a control character");
      }
    }
    return Value.withText(value, "'" + value + "'");
  }

  /**
   * A string to be written as a multi-line literal string, between triple apostrophes with no escaping; see
   * {@link TomlValue#multilineLiteral(String)}.
   *
   * @param value The string.
   * @return The value, carrying the text.
   * @throws IllegalArgumentException If {@code value} holds three apostrophes in a row, or a control character other
   *         than tab and newline, none of which a multi-line literal string can hold.
   */
  static TomlValue multilineLiteral(String value) {
    checkNoUnpairedSurrogate(value, "String");
    if (value.contains("'''")) {
      throw new IllegalArgumentException("A multi-line literal string cannot hold three apostrophes in a row");
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '\n' && isControl(c)) {
        throw new IllegalArgumentException("A multi-line literal string cannot hold a control character");
      }
    }
    // The newline directly after the opening delimiter is trimmed when read, so it makes the text a line of its own
    return Value.withText(value, "'''\n" + value + "'''");
  }

  // A character no string can hold unescaped: a control character other than tab, or the delete character.
  private static boolean isControl(char c) {
    return (c < 0x20 && c != '\t') || c == 0x7F;
  }

  /**
   * Check a key handed to the editing API, before anything is created for it.
   *
   * @param key The key.
   * @throws IllegalArgumentException If {@code key} contains an unpaired surrogate.
   */
  static void checkKey(String key) {
    checkNoUnpairedSurrogate(key, "Key");
  }

  // TOML text is a sequence of Unicode scalar values, so a surrogate that is not part of a pair encoding one cannot be
  // encoded. A high surrogate must be followed by a low one; a low surrogate found on its own, or a high surrogate
  // found at the end of the string or not followed by a low one, is unpaired.
  private static void checkNoUnpairedSurrogate(String value, String subject) {
    int length = value.length();
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= length || !Character.isLowSurrogate(value.charAt(i + 1))) {
          throw new IllegalArgumentException(subject + " contains an unpaired surrogate");
        }
        i++;
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException(subject + " contains an unpaired surrogate");
      }
    }
  }

  private static void checkYear(int year) {
    if (year < 0 || year > 9999) {
      throw new IllegalArgumentException("Year is outside the range 0 to 9999");
    }
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
