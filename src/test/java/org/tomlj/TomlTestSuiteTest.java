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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.tomlj.JsonOptions.ALL_VALUES_AS_STRINGS;
import static org.tomlj.JsonOptions.VALUES_AS_OBJECTS_WITH_TYPE;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the official TOML test suite, https://github.com/toml-lang/toml-test, against the parser.
 *
 * <p>
 * The suite is extracted into the build directory by the {@code extractTomlTest} Gradle task, and its location is
 * passed in the {@code org.tomlj.tomlTestDir} system property. Each {@code files-toml-<version>} list names the cases
 * that apply to a TOML specification version. Valid cases must parse without errors and match the expected tagged JSON
 * document, compared using the same rules as the official {@code toml-test} runner: floats numerically, date/times as
 * instants, everything else as strings. Valid cases must also survive a round trip: their {@code toToml()} output must
 * parse without errors at the same version and match the same expected document. Invalid cases must produce at least
 * one error.
 */
class TomlTestSuiteTest {

  @TestFactory
  Stream<DynamicTest> toml_1_0_0() throws IOException {
    return suite("files-toml-1.0.0", TomlVersion.V1_0_0, Set.of());
  }

  @TestFactory
  Stream<DynamicTest> toml_1_1_0() throws IOException {
    return suite("files-toml-1.1.0", TomlVersion.V1_1_0, Set.of());
  }

  @TestFactory
  Stream<DynamicTest> toml_1_1_0_at_head() throws IOException {
    return suite("files-toml-1.1.0", TomlVersion.HEAD, Set.of());
  }

  private static Stream<DynamicTest> suite(String fileList, TomlVersion version, Set<String> knownFailures)
      throws IOException {
    Path suiteDir = suiteDir();
    List<String> files = Files.readAllLines(suiteDir.resolve(fileList), UTF_8);
    Set<String> stale = new TreeSet<>(knownFailures);
    files.forEach(stale::remove);
    assertTrue(stale.isEmpty(), () -> "Known failures not present in " + fileList + ": " + stale);
    return files
        .stream()
        .filter(file -> file.endsWith(".toml"))
        .map(file -> DynamicTest.dynamicTest(file, () -> run(suiteDir, file, version, knownFailures.contains(file))));
  }

  static Path suiteDir() {
    Path dir = Paths.get(System.getProperty("org.tomlj.tomlTestDir", "build/toml-test"));
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException(
          "TOML test suite not found at " + dir.toAbsolutePath() + " (run ./gradlew extractTomlTest)");
    }
    return dir;
  }

  private static void run(Path suiteDir, String file, TomlVersion version, boolean knownFailure) throws IOException {
    Path toml = suiteDir.resolve(file);
    try {
      if (file.startsWith("invalid/")) {
        assertRejected(toml, version);
      } else {
        assertParsesTo(toml, suiteDir.resolve(file.replaceAll("\\.toml$", ".json")), version);
      }
    } catch (AssertionError e) {
      if (knownFailure) {
        return;
      }
      throw e;
    }
    if (knownFailure) {
      fail(file + " passes but is listed as a known failure; remove it from the list");
    }
  }

  private static void assertRejected(Path toml, TomlVersion version) throws IOException {
    TomlParseResult result;
    try {
      result = Toml.parse(toml, version);
    } catch (CharacterCodingException e) {
      // Malformed UTF-8 is rejected before parsing, which counts as an error
      return;
    }
    assertTrue(
        result.hasErrors(),
        () -> "Expected errors, but the document parsed as " + result.toJson(VALUES_AS_OBJECTS_WITH_TYPE));
  }

  private static void assertParsesTo(Path toml, Path json, TomlVersion version) throws IOException {
    TomlParseResult result = Toml.parse(toml, version);
    assertFalse(result.hasErrors(), () -> "Unexpected errors: " + result.errors());
    Object expected = new JsonReader(Files.readString(json, UTF_8)).read();
    assertMatches(expected, result, "");

    String serialized = result.toToml();
    TomlParseResult reparsed = Toml.parse(serialized, version);
    assertFalse(
        reparsed.hasErrors(),
        () -> "Unexpected errors after serializing to TOML: " + reparsed.errors() + "\n" + serialized);
    assertMatches(expected, reparsed, "After serializing to TOML: ");
    TomlAssertions.assertSameComments(result, reparsed);
  }

  private static void assertMatches(Object expected, TomlParseResult result, String context) {
    Object actual = new JsonReader(result.toJson(VALUES_AS_OBJECTS_WITH_TYPE, ALL_VALUES_AS_STRINGS)).read();
    String difference = compare(expected, actual, "");
    if (difference != null) {
      fail(context + difference);
    }
  }

  // The comparison below mirrors CompareJSON in the toml-test runner (json.go).

  private static String compare(Object want, Object have, String key) {
    if (want instanceof Map<?, ?> wantMap) {
      return compareMaps(wantMap, have, key);
    }
    if (want instanceof List<?> wantList) {
      return compareLists(wantList, have, key);
    }
    return "Key " + quote(key) + " in expected output should be an object or an array, but it is " + describe(want);
  }

  private static String compareMaps(Map<?, ?> want, Object have, String key) {
    if (!(have instanceof Map<?, ?> haveMap)) {
      return "Key " + quote(key) + " should be a table or value, but the parser output " + describe(have);
    }
    boolean wantValue = isValue(want);
    boolean haveValue = isValue(haveMap);
    if (wantValue && !haveValue) {
      return "Key " + quote(key) + " should be a value, but the parser reports it as a table";
    }
    if (!wantValue && haveValue) {
      return "Key " + quote(key) + " should be a table, but the parser reports it as a value";
    }
    if (wantValue) {
      return compareValues(want, haveMap, key);
    }
    for (Object k : want.keySet()) {
      if (!haveMap.containsKey(k)) {
        return "Could not find key " + quote(join(key, k)) + " in parser output";
      }
    }
    for (Object k : haveMap.keySet()) {
      if (!want.containsKey(k)) {
        return "Could not find key " + quote(join(key, k)) + " in expected output";
      }
    }
    for (Map.Entry<?, ?> entry : want.entrySet()) {
      String difference = compare(entry.getValue(), haveMap.get(entry.getKey()), join(key, entry.getKey()));
      if (difference != null) {
        return difference;
      }
    }
    return null;
  }

  private static String compareLists(List<?> want, Object have, String key) {
    if (!(have instanceof List<?> haveList)) {
      return "Key " + quote(key) + " should be an array, but the parser output " + describe(have);
    }
    if (want.size() != haveList.size()) {
      return "Array lengths differ for key "
          + quote(key)
          + ": expected "
          + want.size()
          + ", parser output "
          + haveList.size();
    }
    for (int i = 0; i < want.size(); i++) {
      String difference = compare(want.get(i), haveList.get(i), key + "[" + i + "]");
      if (difference != null) {
        return difference;
      }
    }
    return null;
  }

  private static String compareValues(Map<?, ?> want, Map<?, ?> have, String key) {
    String wantType = String.valueOf(want.get("type"));
    String haveType = String.valueOf(have.get("type"));
    if (!wantType.equals(haveType)) {
      return "Key " + quote(key) + " should be a " + wantType + ", but the parser reports a " + haveType;
    }
    String wantValue = String.valueOf(want.get("value"));
    String haveValue = String.valueOf(have.get("value"));
    boolean equal = switch (wantType) {
      case "float" -> floatsEqual(wantValue, haveValue);
      case "datetime", "datetime-local", "date-local", "time-local" -> dateTimesEqual(wantType, wantValue, haveValue);
      case "bool" -> wantValue.toLowerCase(Locale.ROOT).equals(haveValue.toLowerCase(Locale.ROOT));
      default -> wantValue.equals(haveValue);
    };
    if (!equal) {
      return "Values for key "
          + quote(key)
          + " don't match: expected "
          + wantType
          + " "
          + wantValue
          + ", parser output "
          + haveValue;
    }
    return null;
  }

  private static boolean floatsEqual(String want, String have) {
    want = want.toLowerCase(Locale.ROOT);
    have = have.toLowerCase(Locale.ROOT);
    if (want.endsWith("nan") || have.endsWith("nan")) {
      return stripSign(want).equals(stripSign(have));
    }
    try {
      return parseFloat(want) == parseFloat(have);
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static String stripSign(String value) {
    return value.startsWith("+") || value.startsWith("-") ? value.substring(1) : value;
  }

  private static double parseFloat(String value) {
    return switch (value) {
      case "inf", "+inf" -> Double.POSITIVE_INFINITY;
      case "-inf" -> Double.NEGATIVE_INFINITY;
      default -> Double.parseDouble(value);
    };
  }

  private static boolean dateTimesEqual(String type, String want, String have) {
    want = normalizeDateTime(want);
    have = normalizeDateTime(have);
    try {
      return switch (type) {
        case "datetime" -> OffsetDateTime.parse(want).isEqual(OffsetDateTime.parse(have));
        case "datetime-local" -> LocalDateTime.parse(want).equals(LocalDateTime.parse(have));
        case "date-local" -> LocalDate.parse(want).equals(LocalDate.parse(have));
        case "time-local" -> LocalTime.parse(want).equals(LocalTime.parse(have));
        default -> throw new IllegalArgumentException(type);
      };
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private static String normalizeDateTime(String value) {
    return value.replace(' ', 'T').replace('t', 'T').replace('z', 'Z');
  }

  private static boolean isValue(Map<?, ?> map) {
    return map.size() == 2 && map.containsKey("type") && map.containsKey("value");
  }

  private static String join(String key, Object subKey) {
    return key.isEmpty() ? String.valueOf(subKey) : key + "." + subKey;
  }

  private static String quote(String key) {
    return '"' + key + '"';
  }

  private static String describe(Object value) {
    if (value instanceof Map) {
      return "a table";
    }
    if (value instanceof List) {
      return "an array";
    }
    return String.valueOf(value);
  }

  /**
   * A minimal JSON reader, sufficient for the tagged JSON documents used by toml-test. Objects are read as maps, arrays
   * as lists, and strings and other literals as strings.
   */
  private static final class JsonReader {
    private final String input;
    private int pos = 0;

    JsonReader(String input) {
      this.input = input;
    }

    Object read() {
      Object value = readValue();
      skipWhitespace();
      if (pos != input.length()) {
        throw error("Unexpected trailing content");
      }
      return value;
    }

    private Object readValue() {
      skipWhitespace();
      if (pos >= input.length()) {
        throw error("Unexpected end of input");
      }
      char c = input.charAt(pos);
      if (c == '{') {
        return readObject();
      }
      if (c == '[') {
        return readArray();
      }
      if (c == '"') {
        return readString();
      }
      int start = pos;
      while (pos < input.length()
          && ",]}".indexOf(input.charAt(pos)) < 0
          && !Character.isWhitespace(input.charAt(pos))) {
        pos++;
      }
      if (start == pos) {
        throw error("Unexpected character '" + c + "'");
      }
      return input.substring(start, pos);
    }

    private Map<String, Object> readObject() {
      Map<String, Object> map = new LinkedHashMap<>();
      expect('{');
      skipWhitespace();
      if (peek() == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = readString();
        skipWhitespace();
        expect(':');
        map.put(key, readValue());
        skipWhitespace();
        if (peek() == ',') {
          pos++;
          continue;
        }
        expect('}');
        return map;
      }
    }

    private List<Object> readArray() {
      List<Object> list = new ArrayList<>();
      expect('[');
      skipWhitespace();
      if (peek() == ']') {
        pos++;
        return list;
      }
      while (true) {
        list.add(readValue());
        skipWhitespace();
        if (peek() == ',') {
          pos++;
          continue;
        }
        expect(']');
        return list;
      }
    }

    private String readString() {
      expect('"');
      StringBuilder builder = new StringBuilder();
      while (true) {
        if (pos >= input.length()) {
          throw error("Unterminated string");
        }
        char c = input.charAt(pos++);
        if (c == '"') {
          return builder.toString();
        }
        if (c != '\\') {
          builder.append(c);
          continue;
        }
        if (pos >= input.length()) {
          throw error("Unterminated escape sequence");
        }
        char escape = input.charAt(pos++);
        switch (escape) {
          case 'b' -> builder.append('\b');
          case 'f' -> builder.append('\f');
          case 'n' -> builder.append('\n');
          case 'r' -> builder.append('\r');
          case 't' -> builder.append('\t');
          case 'u' -> {
            if (pos + 4 > input.length()) {
              throw error("Unterminated unicode escape");
            }
            builder.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
            pos += 4;
          }
          default -> builder.append(escape);
        }
      }
    }

    private void skipWhitespace() {
      while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
        pos++;
      }
    }

    private char peek() {
      if (pos >= input.length()) {
        throw error("Unexpected end of input");
      }
      return input.charAt(pos);
    }

    private void expect(char c) {
      if (peek() != c) {
        throw error("Expected '" + c + "' but found '" + input.charAt(pos) + "'");
      }
      pos++;
    }

    private IllegalStateException error(String message) {
      return new IllegalStateException(message + " at offset " + pos + " of JSON: " + input);
    }
  }
}
