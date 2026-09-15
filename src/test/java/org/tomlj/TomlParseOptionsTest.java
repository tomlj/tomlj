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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlParseOptionsTest {

  // ---- TomlParseOptions itself ----

  @Test
  void defaultsAreLatestVersionAndDefaultMaxNestingDepth() {
    TomlParseOptions options = TomlParseOptions.defaults();
    assertEquals(TomlVersion.LATEST, options.version());
    assertEquals(128, options.maxNestingDepth());
    assertEquals(TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH, options.maxNestingDepth());
  }

  @Test
  void withVersionReturnsANewInstanceLeavingTheOriginalUnchanged() {
    TomlParseOptions original = TomlParseOptions.defaults();
    TomlParseOptions updated = original.withVersion(TomlVersion.V0_4_0);

    assertEquals(TomlVersion.V0_4_0, updated.version());
    assertEquals(TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH, updated.maxNestingDepth());
    assertEquals(TomlVersion.LATEST, original.version());
  }

  @Test
  void withVersionRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlParseOptions.defaults().withVersion(null));
  }

  @Test
  void withMaxNestingDepthReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheVersion() {
    TomlParseOptions original = TomlParseOptions.defaults().withVersion(TomlVersion.V1_0_0);
    TomlParseOptions updated = original.withMaxNestingDepth(5);

    assertEquals(5, updated.maxNestingDepth());
    assertEquals(TomlVersion.V1_0_0, updated.version());
    assertEquals(TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH, original.maxNestingDepth());
  }

  @Test
  void withVersionKeepsTheMaxNestingDepth() {
    TomlParseOptions original = TomlParseOptions.defaults().withMaxNestingDepth(5);
    TomlParseOptions updated = original.withVersion(TomlVersion.V1_0_0);

    assertEquals(5, updated.maxNestingDepth());
    assertEquals(TomlVersion.V1_0_0, updated.version());
  }

  @Test
  void withMaxNestingDepthAllowsZero() {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(0);
    assertEquals(0, options.maxNestingDepth());
  }

  @Test
  void withMaxNestingDepthRejectsANegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> TomlParseOptions.defaults().withMaxNestingDepth(-1));
  }

  @Test
  void equalOptionsAreEqualAndHaveTheSameHashCode() {
    TomlParseOptions a = TomlParseOptions.defaults().withVersion(TomlVersion.V1_0_0);
    TomlParseOptions b = TomlParseOptions.defaults().withVersion(TomlVersion.V1_0_0);
    TomlParseOptions c = TomlParseOptions.defaults().withVersion(TomlVersion.V1_1_0);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertFalse(a.equals(c));
  }

  @Test
  void optionsDifferingOnlyInMaxNestingDepthAreNotEqual() {
    TomlParseOptions a = TomlParseOptions.defaults().withMaxNestingDepth(10);
    TomlParseOptions b = TomlParseOptions.defaults().withMaxNestingDepth(11);

    assertNotEquals(a, b);
  }

  @Test
  void toStringShowsVersionAndMaxNestingDepth() {
    TomlParseOptions options = TomlParseOptions.defaults().withVersion(TomlVersion.V1_1_0).withMaxNestingDepth(5);
    assertEquals("TomlParseOptions{version=V1_1_0, maxNestingDepth=5}", options.toString());
  }

  // ---- Toml.parse(..., TomlParseOptions) overloads ----

  @Test
  void parsesStringWithOptions() {
    TomlParseResult result = Toml.parse("a = 1", TomlParseOptions.defaults());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(1), result.getLong("a"));
  }

  @Test
  void parsesPathWithOptions(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("test.toml");
    Files.write(file, "a = 1".getBytes(StandardCharsets.UTF_8));
    TomlParseResult result = Toml.parse(file, TomlParseOptions.defaults());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(1), result.getLong("a"));
  }

  @Test
  void parsesInputStreamWithOptions() throws IOException {
    ByteArrayInputStream is = new ByteArrayInputStream("a = 1".getBytes(StandardCharsets.UTF_8));
    TomlParseResult result = Toml.parse(is, TomlParseOptions.defaults());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(1), result.getLong("a"));
  }

  @Test
  void parsesReaderWithOptions() throws IOException {
    TomlParseResult result = Toml.parse(new StringReader("a = 1"), TomlParseOptions.defaults());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(1), result.getLong("a"));
  }

  @Test
  void parsesChannelWithOptions() throws IOException {
    ReadableByteChannel channel =
        Channels.newChannel(new ByteArrayInputStream("a = 1".getBytes(StandardCharsets.UTF_8)));
    TomlParseResult result = Toml.parse(channel, TomlParseOptions.defaults());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(1), result.getLong("a"));
  }

  @Test
  void optionsWithVersionReportsSameErrorAsVersionOverload() {
    TomlParseResult viaVersion = Toml.parse("a.b = 1", TomlVersion.V0_4_0);
    TomlParseResult viaOptions = Toml.parse("a.b = 1", TomlParseOptions.defaults().withVersion(TomlVersion.V0_4_0));

    assertEquals(1, viaVersion.errors().size());
    assertEquals(1, viaOptions.errors().size());
    assertEquals(viaVersion.errors().get(0).toString(), viaOptions.errors().get(0).toString());
  }

  // ---- Behaviour with a custom maximum nesting depth ----

  @Test
  void aDocumentAtTheLimitParsesAndOneMoreLevelReportsAnError() {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(150);

    TomlParseResult atLimit = Toml.parse("a = " + "[".repeat(150) + "1" + "]".repeat(150) + "\n", options);
    assertTrue(atLimit.errors().isEmpty(), () -> joinErrors(atLimit));

    TomlParseResult overLimit = Toml.parse("a = " + "[".repeat(151) + "1" + "]".repeat(151) + "\n", options);
    assertNestingError(overLimit, 1, 156, "Nesting is too deep (more than 150 levels of tables and arrays)");
  }

  @Test
  void zeroMaxNestingDepthAllowsOnlyUnnestedValuesAndEmptyTables() {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(0);

    assertTrue(Toml.parse("a = 1\n", options).errors().isEmpty());
    assertTrue(Toml.parse("a = []\n", options).errors().isEmpty());
    assertTrue(Toml.parse("[t]\n", options).errors().isEmpty());

    assertNestingError(
        Toml.parse("a = [1]\n", options),
        1,
        6,
        "Nesting is too deep (more than 0 levels of tables and arrays)");
    assertNestingError(
        Toml.parse("a.b = 1\n", options),
        1,
        7,
        "Nesting is too deep (more than 0 levels of tables and arrays)");
    assertNestingError(
        Toml.parse("[t]\nx = 1\n", options),
        2,
        1,
        "Nesting is too deep (more than 0 levels of tables and arrays)");
    assertNestingError(
        Toml.parse("[[t]]\n", options),
        1,
        1,
        "Nesting is too deep (more than 0 levels of tables and arrays)");
  }

  @Test
  void aMaxNestingDepthOfOneUsesTheSingularWordInTheMessage() {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(1);
    TomlParseResult result = Toml.parse("a.b.c = 1\n", options);
    assertNestingError(result, 1, 9, "Nesting is too deep (more than 1 level of tables and arrays)");
  }

  @Test
  void integerMaxValueMaxNestingDepthAllowsDeepNesting() {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(Integer.MAX_VALUE);
    TomlParseResult result = Toml.parse("a = " + "[".repeat(150) + "1" + "]".repeat(150) + "\n", options);
    assertTrue(result.errors().isEmpty(), () -> joinErrors(result));
  }

  @Test
  void everyOverloadHonoursTheMaxNestingDepth(@TempDir Path tempDir) throws IOException {
    TomlParseOptions options = TomlParseOptions.defaults().withMaxNestingDepth(0);
    String input = "a = [1]\n";
    String expectedMessage = "Nesting is too deep (more than 0 levels of tables and arrays)";

    assertNestingError(Toml.parse(input, options), 1, 6, expectedMessage);

    Path file = tempDir.resolve("test.toml");
    Files.write(file, input.getBytes(StandardCharsets.UTF_8));
    assertNestingError(Toml.parse(file, options), 1, 6, expectedMessage);

    InputStream is = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    assertNestingError(Toml.parse(is, options), 1, 6, expectedMessage);

    assertNestingError(Toml.parse(new StringReader(input), options), 1, 6, expectedMessage);

    ReadableByteChannel channel = Channels.newChannel(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    assertNestingError(Toml.parse(channel, options), 1, 6, expectedMessage);
  }

  // ---- The depth examples from TomlParseOptions#withMaxNestingDepth's javadoc, at their exact boundary ----

  @Test
  void arrayInArrayIsNestedTwoDeep() {
    assertTrue(Toml.parse("a = [[1]]\n", TomlParseOptions.defaults().withMaxNestingDepth(2)).errors().isEmpty());

    TomlParseResult result = Toml.parse("a = [[1]]\n", TomlParseOptions.defaults().withMaxNestingDepth(1));
    assertNestingError(result, 1, 7, "Nesting is too deep (more than 1 level of tables and arrays)");
  }

  @Test
  void dottedKeyValueIsNestedTwoDeep() {
    assertTrue(Toml.parse("a.b.c = 1\n", TomlParseOptions.defaults().withMaxNestingDepth(2)).errors().isEmpty());

    TomlParseResult result = Toml.parse("a.b.c = 1\n", TomlParseOptions.defaults().withMaxNestingDepth(1));
    assertNestingError(result, 1, 9, "Nesting is too deep (more than 1 level of tables and arrays)");
  }

  @Test
  void dottedTableHeaderTableIsNestedOneDeep() {
    assertTrue(Toml.parse("[a.b]\n", TomlParseOptions.defaults().withMaxNestingDepth(1)).errors().isEmpty());

    TomlParseResult result = Toml.parse("[a.b]\n", TomlParseOptions.defaults().withMaxNestingDepth(0));
    assertNestingError(result, 1, 1, "Nesting is too deep (more than 0 levels of tables and arrays)");
  }

  @Test
  void dottedArrayTableHeaderTablesAreNestedTwoDeep() {
    assertTrue(Toml.parse("[[a.b]]\n", TomlParseOptions.defaults().withMaxNestingDepth(2)).errors().isEmpty());

    TomlParseResult result = Toml.parse("[[a.b]]\n", TomlParseOptions.defaults().withMaxNestingDepth(1));
    assertNestingError(result, 1, 1, "Nesting is too deep (more than 1 level of tables and arrays)");
  }

  private static void assertNestingError(TomlParseResult result, int line, int column, String expectedMessage) {
    List<TomlParseError> errors = result.errors();
    assertEquals(1, errors.size(), () -> joinErrors(result));
    assertEquals(expectedMessage, errors.get(0).getMessage());
    assertEquals(line, errors.get(0).position().line());
    assertEquals(column, errors.get(0).position().column());
  }

  private static String joinErrors(TomlParseResult result) {
    return result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("\n"));
  }
}
