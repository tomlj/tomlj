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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks how a table or array asked for a style of its own is written, and how that style meets the one the options ask
 * for.
 */
class ReformatTest {

  private static final TomlOptions LF = TomlOptions.defaults().withLineSeparator("\n");
  private static final TomlOptions.Style PRETTIFY = TomlOptions.Style.PRETTIFY;
  private static final TomlOptions.Style CANONICAL = TomlOptions.Style.CANONICAL;

  @ParameterizedTest(name = "{0}")
  @MethodSource("reformattedDocuments")
  void writesATableOrArrayInTheStyleItWasAskedFor(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlOptions options,
      String expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    edit.accept(result);

    assertWrites(result, options, expected);
  }

  static Stream<Arguments> reformattedDocuments() {
    return Stream
        .of(
            reformatted(
                "a table written in the canonical style keeps the place its header had",
                "[t]\nx   =  1 # c\n\ny=2\n[u]\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                "[t]\nx = 1  # c\ny = 2\n[u]\n"),
            reformatted(
                "a table written in the canonical style takes the tables written in it with it",
                "[t]\nx = 1\n[u]\ny = 2\n[t.c]\nz = 3\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                "[t]\nx = 1\n\n[t.c]\nz = 3\n[u]\ny = 2\n"),
            reformatted(
                "the comments of a table written in the canonical style are written from the model",
                "# above\n[t]  # after\n# a run of its own\n\nx = 1\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                "# above\n[t]  # after\n\n# a run of its own\n\nx = 1\n"),
            reformatted(
                "a table the document wrote as dotted keys becomes a section of its own",
                "[p]\na.x   =  1\nb = 2\n[q]\n",
                result -> requireTable(result, "p.a").reformat(CANONICAL),
                "[p]\nb = 2\n\n[p.a]\nx = 1\n[q]\n"),
            reformatted(
                "a table the root wrote as dotted keys becomes a section at the end",
                "a.x   =  1\nb = 2\n",
                result -> requireTable(result, "a").reformat(CANONICAL),
                "b = 2\n\n[a]\nx = 1\n"),
            reformatted(
                "an array of tables written in the canonical style is written where its first table was",
                "[[t]]\nx=1\n[[t]]\nx=2\n[u]\n",
                result -> requireArray(result, "t").reformat(CANONICAL),
                "[[t]]\nx = 1\n\n[[t]]\nx = 2\n[u]\n"),
            reformatted(
                "an array written in the canonical style is written anew on its line",
                "a = [ 1,2 ]  # note\nb = 2\n",
                result -> requireArray(result, "a").reformat(CANONICAL),
                "a = [1, 2]  # note\nb = 2\n"),
            reformatted(
                "the literal a value was written with goes in the canonical style",
                "a = [ 0x10,2 ]\nb = 0x10\n",
                result -> requireArray(result, "a").reformat(CANONICAL),
                "a = [16, 2]\nb = 0x10\n"),
            reformatted(
                "an inline table written in the canonical style stays on its line",
                "t = {p=1}\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                "t = { p = 1 }\n"),
            reformatted(
                "an array written in the canonical style inside one that is not",
                "a = [ 1, [ 2,3 ] ]\n",
                result -> requireArray(result, "a").getArray(1).reformat(CANONICAL),
                "a = [ 1, [2, 3] ]\n"),
            reformatted(
                "a table written in the canonical style is written at the indentation the options ask for",
                "[t]\nx   = 1\n[t.s]\ny = 2\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                LF.withIndent(2),
                "[t]\n  x = 1\n\n  [t.s]\n    y = 2\n"),
            reformatted(
                "a table written in a normalized layout keeps its literals and the lines around it",
                "[t]\nx   =  0x10 # c\n\n\ny=2\n[u]\nz   = 3\n",
                result -> requireTable(result, "t").reformat(PRETTIFY),
                "[t]\nx = 0x10  # c\n\ny = 2\n[u]\nz   = 3\n"),
            reformatted(
                "a table written in a normalized layout keeps the dotted keys it was written with",
                "a.b   =  0x10\nc = 2\n",
                result -> requireTable(result, "a").reformat(PRETTIFY),
                "a.b = 0x10\nc = 2\n"),
            reformatted(
                "an array written in a normalized layout is laid out anew around its literals",
                "a = [ 0x10,2 ]\nb   = 1\n",
                result -> requireArray(result, "a").reformat(PRETTIFY),
                "a = [0x10, 2]\nb   = 1\n"),
            reformatted(
                "an inline table written in a normalized layout is laid out anew",
                "t = {p=0x10,q=2}\nb   = 1\n",
                result -> requireTable(result, "t").reformat(PRETTIFY),
                "t = { p = 0x10, q = 2 }\nb   = 1\n"),
            reformatted(
                "the style of a table is the style of every table written in it",
                "[t]\nx = 1\n[t.s]\ny   =  0x2\n",
                result -> requireTable(result, "t").reformat(PRETTIFY),
                "[t]\nx = 1\n\n[t.s]\ny = 0x2\n"),
            reformatted(
                "the canonical style wins over a normalized layout asked for first",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(PRETTIFY).reformat(CANONICAL),
                "[t]\nx = 16\n"),
            reformatted(
                "the canonical style stays when a normalized layout is asked for after it",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(CANONICAL).reformat(PRETTIFY),
                "[t]\nx = 16\n"),
            reformatted(
                "the whole document is written in the canonical style when the root asks for it",
                "a   = 0x10  # c\n[t]\nx=1\n\n\n",
                result -> result.reformat(CANONICAL),
                "a = 16  # c\n\n[t]\nx = 1\n"),
            reformatted(
                "the whole document is written in a normalized layout when the root asks for it",
                "a   = 0x10  # c\n[t]\nx=1\n\n\n",
                result -> result.reformat(PRETTIFY),
                "a = 0x10  # c\n\n[t]\nx = 1\n"),
            reformatted(
                "the style the options ask for wins over a weaker one asked for here",
                "[t]\nx   = 0x10\n",
                result -> requireTable(result, "t").reformat(PRETTIFY),
                LF.canonical(),
                "[t]\nx = 16\n"),
            reformatted(
                "a table written in the canonical style keeps it in a normalized document",
                "[t]\nx   = 0x10\n[u]\ny  = 0x20\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                LF.prettify(),
                "[t]\nx = 16\n\n[u]\ny = 0x20\n"),
            reformatted(
                "a table written in the canonical style is written with what was added to it",
                "[t]\nx   = 1\n",
                result -> requireTable(result, "t").set("y", 2).reformat(CANONICAL),
                "[t]\nx = 1\ny = 2\n"),
            reformatted(
                "a table written in the canonical style keeps a comment written in an array in it",
                "[t]\nx = [1,\n  # c\n]\n",
                result -> requireTable(result, "t").reformat(CANONICAL),
                "[t]\nx = [\n  1,\n  # c\n]\n"));
  }

  @Test
  void rejectsTheStyleThatKeepsTheDocument() {
    TomlParseResult result = Toml.parse("a = [1]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertThrows(IllegalArgumentException.class, () -> result.reformat(TomlOptions.Style.PRESERVE));
    assertThrows(IllegalArgumentException.class, () -> requireArray(result, "a").reformat(TomlOptions.Style.PRESERVE));
    assertThrows(NullPointerException.class, () -> result.reformat(null));
  }

  @Test
  void keepsTheStyleOfACopiedTable() {
    TomlParseResult result = Toml.parse("[a]\nx = 0x10  # kept\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    MutableTomlTable table = requireTable(result, "a");
    table.reformat(CANONICAL);
    result.set("z", MutableTomlTable.copyOf(table));

    assertWrites(result, LF, "[a]\nx = 16  # kept\n\n[z]\nx = 16  # kept\n");
  }

  @Test
  void keepsTheStyleOfACopiedArray() {
    TomlParseResult result = Toml.parse("a = [ 0x10,2 ]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    MutableTomlArray array = requireArray(result, "a");
    array.reformat(CANONICAL);
    result.set("b", MutableTomlArray.copyOf(array));

    assertWrites(result, LF, "a = [16, 2]\nb = [16, 2]\n");
  }

  @Test
  void writesADocumentWithNoSourceTheSameWayWhateverStyleItAsksFor() {
    TomlParseResult result = Toml.parse("a   =   0x10\n[t]\nx = 1\n", TomlParseOptions.defaults().withoutSource());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    String written = result.toToml(LF);

    requireTable(result, "t").reformat(PRETTIFY);
    assertEquals(written, result.toToml(LF));
    result.reformat(CANONICAL);
    assertEquals(written, result.toToml(LF));
  }

  @Test
  void writesATableBuiltThroughTheEditingApiTheSameWayWhateverStyleItAsksFor() {
    MutableTomlTable table = MutableTomlTable.create().set("a", 1).set("t.x", 2);
    String written = table.toToml(LF);

    table.reformat(PRETTIFY);
    assertEquals(written, table.toToml(LF));
    table.reformat(CANONICAL);
    assertEquals(written, table.toToml(LF));
  }

  /**
   * Assert what a document is written as, and that parsing it again gives back the values and the comments it holds.
   *
   * @param result The document.
   * @param options The options to write it with.
   * @param expected What it is expected to be written as.
   */
  private static void assertWrites(TomlParseResult result, TomlOptions options, String expected) {
    String written = result.toToml(options);
    assertEquals(expected, written);

    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
    TomlAssertions.assertSameComments(result, reparsed);
  }

  private static Arguments reformatted(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      String expected) {
    return reformatted(description, input, edit, LF, expected);
  }

  private static Arguments reformatted(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlOptions options,
      String expected) {
    return Arguments.of(description, input, edit, options, expected);
  }

  private static MutableTomlTable requireTable(MutableTomlTable table, String dottedKey) {
    MutableTomlTable subTable = table.getTable(dottedKey);
    assertNotNull(subTable, () -> "No table at " + dottedKey);
    return subTable;
  }

  private static MutableTomlArray requireArray(MutableTomlTable table, String dottedKey) {
    MutableTomlArray array = table.getArray(dottedKey);
    assertNotNull(array, () -> "No array at " + dottedKey);
    return array;
  }

  private static String joinErrors(TomlParseResult result) {
    return result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("\n"));
  }
}
