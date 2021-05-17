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

import java.io.*;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.IntStream;

/**
 * Methods for parsing data stored in Tom's Obvious, Minimal Language (TOML).
 */
public final class Toml {
  private static final Pattern simpleKeyPattern = Pattern.compile("^[A-Za-z0-9_-]+$");

  private Toml() {}

  /**
   * Parse a TOML string.
   *
   * @param input The input to parse.
   * @return The parse result.
   */
  public static TomlParseResult parse(String input) {
    return parse(input, TomlVersion.LATEST);
  }

  /**
   * Parse a TOML string.
   *
   * @param input The input to parse.
   * @param version The version level to parse at.
   * @return The parse result.
   */
  public static TomlParseResult parse(String input, TomlVersion version) {
    CharStream stream = CharStreams.fromString(input);
    return Parser.parse(stream, version.canonical);
  }

  /**
   * Parse a TOML file.
   *
   * @param file The input file to parse.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(Path file) throws IOException {
    return parse(file, TomlVersion.LATEST);
  }

  /**
   * Parse a TOML file.
   *
   * @param file The input file to parse.
   * @param version The version level to parse at.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(Path file, TomlVersion version) throws IOException {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), decoder);
    return parse(reader, version);
  }

  /**
   * Parse a TOML input stream.
   *
   * @param is The UTF-8 encoded input stream to read the TOML document from.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(InputStream is) throws IOException {
    return parse(is, TomlVersion.LATEST);
  }

  /**
   * Parse a TOML input stream.
   *
   * @param is The UTF-8 encoded input stream to read the TOML document from.
   * @param version The version level to parse at.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(InputStream is, TomlVersion version) throws IOException {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    return parse(new InputStreamReader(is, decoder), version);
  }

  /**
   * Parse a TOML reader.
   *
   * @param reader The reader to obtain the TOML document from.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(Reader reader) throws IOException {
    return parse(reader, TomlVersion.LATEST);
  }

  /**
   * Parse a TOML input stream.
   *
   * @param reader The reader to obtain the TOML document from.
   * @param version The version level to parse at.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(Reader reader, TomlVersion version) throws IOException {
    CharStream stream = CharStreams.fromReader(reader);
    return Parser.parse(stream, version.canonical);
  }

  /**
   * Parse a TOML reader.
   *
   * @param channel The channel to read the TOML document from.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(ReadableByteChannel channel) throws IOException {
    return parse(channel, TomlVersion.LATEST);
  }

  /**
   * Parse a TOML input stream.
   *
   * @param channel The UTF-8 encoded channel to read the TOML document from.
   * @param version The version level to parse at.
   * @return The parse result.
   * @throws IOException If an IO error occurs.
   */
  public static TomlParseResult parse(ReadableByteChannel channel, TomlVersion version) throws IOException {
    CharStream stream = CharStreams
        .fromChannel(
            channel,
            StandardCharsets.UTF_8,
            4096,
            CodingErrorAction.REPORT,
            IntStream.UNKNOWN_SOURCE_NAME,
            -1);
    return Parser.parse(stream, version.canonical);
  }

  /**
   * Parse a dotted key into individual parts.
   *
   * @param dottedKey A dotted key (e.g. {@code server.address.port}).
   * @return A list of individual keys in the path.
   * @throws IllegalArgumentException If the dotted key cannot be parsed.
   */
  public static List<String> parseDottedKey(String dottedKey) {
    requireNonNull(dottedKey);
    return Parser.parseDottedKey(dottedKey);
  }

  /**
   * Join a list of keys into a single dotted key string.
   *
   * @param path The list of keys that form the path.
   * @return The path string.
   */
  public static String joinKeyPath(List<String> path) {
    requireNonNull(path);

    StringJoiner joiner = new StringJoiner(".");
    for (String key : path) {
      if (simpleKeyPattern.matcher(key).matches()) {
        joiner.add(key);
      } else {
        joiner.add("\"" + tomlEscape(key) + '\"');
      }
    }
    return joiner.toString();
  }

  /**
   * Get the canonical form of the dotted key.
   *
   * @param dottedKey A dotted key (e.g. {@code server.address.port}).
   * @return The canonical form of the dotted key.
   * @throws IllegalArgumentException If the dotted key cannot be parsed.
   */
  public static String canonicalDottedKey(String dottedKey) {
    return joinKeyPath(parseDottedKey(dottedKey));
  }

  /**
   * Escape a text string using the TOML escape sequences.
   *
   * @param text The text string to escape.
   * @return A {@link StringBuilder} holding the results of escaping the text.
   */
  public static StringBuilder tomlEscape(String text) {
    final StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      int codepoint = text.codePointAt(i);
      if (Character.charCount(codepoint) > 1) {
        out.append("\\U").append(String.format("%08x", codepoint));
        ++i;
        continue;
      }

      char ch = Character.toChars(codepoint)[0];
      if (ch == '\'') {
        out.append("\\'");
        continue;
      }
      if (ch >= 0x20 && ch < 0x7F) {
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
          out.append("\\u").append(String.format("%04x", codepoint));
      }
    }
    return out;
  }

  public static boolean arrayEquals(TomlArray array, TomlArray array2) {
    if (array.size() != array2.size()) {
      return false;
    }

    for (int i = 0; i < array.size(); i++) {
      Object value1 = array.get(i);
      Object value2 = array2.get(i);

      Optional<TomlType> tomlType = typeFor(value1);
      assert tomlType.isPresent();

      Optional<TomlType> tomlType2 = typeFor(value2);
      assert tomlType2.isPresent();

      if (tomlType.get() != tomlType2.get()) {
        return false;
      }

      if (tomlType.get().equals(TABLE)) {
        if (!tableEquals((TomlTable) value1, (TomlTable) value2)) {
          return false;
        }
      } else if (tomlType.get().equals(ARRAY)) {
        if (!arrayEquals((TomlArray) value1, (TomlArray) value2)) {
          return false;
        }
      } else {
        if (!value1.equals(value2)) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean tableEquals(TomlTable table, TomlTable table2) {
    if (table.entrySet().size() != table2.entrySet().size()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : table.entrySet()) {
      String key = entry.getKey();
      if (!table2.keySet().contains(key)) {
        return false;
      }

      Object value1 = entry.getValue();
      Optional<Map.Entry<String, Object>> value2Entry =
          table2.entrySet().stream().filter(entry2 -> entry2.getKey().equals(key)).findFirst();

      if (!value2Entry.isPresent()) {
        return false;
      }

      Object value2 = value2Entry.get().getValue();

      Optional<TomlType> tomlType = typeFor(value1);
      assert tomlType.isPresent();

      Optional<TomlType> tomlType2 = typeFor(value2);
      assert tomlType2.isPresent();

      if (tomlType.get() != tomlType2.get()) {
        return false;
      }

      if (tomlType.get().equals(TABLE)) {
        if (!tableEquals((TomlTable) value1, (TomlTable) value2)) {
          return false;
        }
      } else if (tomlType.get().equals(ARRAY)) {
        if (!arrayEquals((TomlArray) value1, (TomlArray) value2)) {
          return false;
        }
      } else {
        if (!value1.equals(value2)) {
          return false;
        }
      }
    }
    return true;
  }
}
