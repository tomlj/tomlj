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
 * Checks how a table or array reformatted by reformat() is written, and how the amount it keeps combines with the one
 * the options ask for.
 */
class ReformatTest {

  private static final TomlWriteOptions LF = TomlWriteOptions.defaults().withLineSeparator("\n");
  private static final TomlWriteOptions.Keep LAYOUT = TomlWriteOptions.Keep.LAYOUT;
  private static final TomlWriteOptions.Keep NOTATION = TomlWriteOptions.Keep.NOTATION;
  private static final TomlWriteOptions.Keep NOTHING = TomlWriteOptions.Keep.NOTHING;

  @ParameterizedTest(name = "{0}")
  @MethodSource("reformattedDocuments")
  void writesATableOrArrayKeepingWhatReformatAskedFor(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlWriteOptions options,
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
                "a table reformatted to keep nothing is written in the place its header had",
                "[t]\nx   =  1 # c\n\ny=2\n[u]\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                "[t]\nx = 1  # c\ny = 2\n[u]\n"),
            reformatted(
                "a table reformatted to keep nothing is written with the tables written in it",
                "[t]\nx = 1\n[u]\ny = 2\n[t.c]\nz = 3\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                "[t]\nx = 1\n\n[t.c]\nz = 3\n[u]\ny = 2\n"),
            reformatted(
                "the comments of a table reformatted to keep nothing are written from the model",
                "# above\n[t]  # after\n# a run of its own\n\nx = 1\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                "# above\n[t]  # after\n\n# a run of its own\n\nx = 1\n"),
            reformatted(
                "a table the document wrote as dotted keys becomes a section of its own",
                "[p]\na.x   =  1\nb = 2\n[q]\n",
                result -> requireTable(result, "p.a").reformat(NOTHING),
                "[p]\nb = 2\n\n[p.a]\nx = 1\n[q]\n"),
            reformatted(
                "a table the root wrote as dotted keys becomes a section at the end",
                "a.x   =  1\nb = 2\n",
                result -> requireTable(result, "a").reformat(NOTHING),
                "b = 2\n\n[a]\nx = 1\n"),
            reformatted(
                "an array of tables reformatted to keep nothing is written where its first table was",
                "[[t]]\nx=1\n[[t]]\nx=2\n[u]\n",
                result -> requireArray(result, "t").reformat(NOTHING),
                "[[t]]\nx = 1\n\n[[t]]\nx = 2\n[u]\n"),
            reformatted(
                "an array reformatted to keep nothing is written anew on its line",
                "a = [ 1,2 ]  # note\nb = 2\n",
                result -> requireArray(result, "a").reformat(NOTHING),
                "a = [1, 2]  # note\nb = 2\n"),
            reformatted(
                "the literal a value was written with is dropped when nothing is kept",
                "a = [ 0x10,2 ]\nb = 0x10\n",
                result -> requireArray(result, "a").reformat(NOTHING),
                "a = [16, 2]\nb = 0x10\n"),
            reformatted(
                "an inline table reformatted to keep nothing stays on its line",
                "t = {p=1}\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                "t = { p = 1 }\n"),
            reformatted(
                "an array reformatted to keep nothing inside one that is not",
                "a = [ 1, [ 2,3 ] ]\n",
                result -> requireArray(result, "a").getArray(1).reformat(NOTHING),
                "a = [ 1, [2, 3] ]\n"),
            reformatted(
                "an array reformatted to keep nothing inside one that keeps the notation",
                "a = [ 0x10, [ 0x20,3 ] ]\n",
                result -> requireArray(result, "a").getArray(1).reformat(NOTHING),
                LF.keep(TomlWriteOptions.Keep.NOTATION),
                "a = [0x10, [32, 3]]\n"),
            reformatted(
                "a table reformatted to keep nothing is written at the indentation the options ask for",
                "[t]\nx   = 1\n[t.s]\ny = 2\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                LF.withIndent(2),
                "[t]\n  x = 1\n\n  [t.s]\n    y = 2\n"),
            reformatted(
                "a table reformatted to keep the notation keeps its literals and the lines around it",
                "[t]\nx   =  0x10 # c\n\n\ny=2\n[u]\nz   = 3\n",
                result -> requireTable(result, "t").reformat(NOTATION),
                "[t]\nx = 0x10  # c\n\ny = 2\n[u]\nz   = 3\n"),
            reformatted(
                "a table reformatted to keep the notation keeps the dotted keys it was written with",
                "a.b   =  0x10\nc = 2\n",
                result -> requireTable(result, "a").reformat(NOTATION),
                "a.b = 0x10\nc = 2\n"),
            reformatted(
                "an array reformatted to keep the notation is laid out anew around its literals",
                "a = [ 0x10,2 ]\nb   = 1\n",
                result -> requireArray(result, "a").reformat(NOTATION),
                "a = [0x10, 2]\nb   = 1\n"),
            reformatted(
                "an inline table reformatted to keep the notation is laid out anew",
                "t = {p=0x10,q=2}\nb   = 1\n",
                result -> requireTable(result, "t").reformat(NOTATION),
                "t = { p = 0x10, q = 2 }\nb   = 1\n"),
            reformatted(
                "what a table keeps is what every table written in it keeps",
                "[t]\nx = 1\n[t.s]\ny   =  0x2\n",
                result -> requireTable(result, "t").reformat(NOTATION),
                "[t]\nx = 1\n\n[t.s]\ny = 0x2\n"),
            reformatted(
                "keeping the layout changes nothing",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(LAYOUT),
                "[t]\nx   =  0x10\n"),
            reformatted(
                "keeping the layout changes nothing for an array",
                "a   =  [ 0x10 ]\n",
                result -> requireArray(result, "a").reformat(LAYOUT),
                "a   =  [ 0x10 ]\n"),
            reformatted(
                "keeping the notation stays when keeping the layout is asked for after it",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(NOTATION).reformat(LAYOUT),
                "[t]\nx = 0x10\n"),
            reformatted(
                "keeping nothing wins over keeping the notation asked for first",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(NOTATION).reformat(NOTHING),
                "[t]\nx = 16\n"),
            reformatted(
                "keeping nothing stays when keeping the notation is asked for after it",
                "[t]\nx   =  0x10\n",
                result -> requireTable(result, "t").reformat(NOTHING).reformat(NOTATION),
                "[t]\nx = 16\n"),
            reformatted(
                "the whole document keeps nothing when the root is reformatted to keep nothing",
                "a   = 0x10  # c\n[t]\nx=1\n\n\n",
                result -> result.reformat(NOTHING),
                "a = 16  # c\n\n[t]\nx = 1\n"),
            reformatted(
                "the whole document keeps the notation when the root is reformatted to keep it",
                "a   = 0x10  # c\n[t]\nx=1\n\n\n",
                result -> result.reformat(NOTATION),
                "a = 0x10  # c\n\n[t]\nx = 1\n"),
            reformatted(
                "the options win when they keep less than the table was reformatted to keep",
                "[t]\nx   = 0x10\n",
                result -> requireTable(result, "t").reformat(NOTATION),
                LF.keep(TomlWriteOptions.Keep.NOTHING),
                "[t]\nx = 16\n"),
            reformatted(
                "a table reformatted to keep nothing keeps nothing in a document that keeps the notation",
                "[t]\nx   = 0x10\n[u]\ny  = 0x20\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                LF.keep(TomlWriteOptions.Keep.NOTATION),
                "[t]\nx = 16\n\n[u]\ny = 0x20\n"),
            reformatted(
                "a table reformatted to keep nothing is written with what was added to it",
                "[t]\nx   = 1\n",
                result -> requireTable(result, "t").set("y", 2).reformat(NOTHING),
                "[t]\nx = 1\ny = 2\n"),
            reformatted(
                "a table reformatted to keep nothing is written with a comment written in an array in it",
                "[t]\nx = [1,\n  # c\n]\n",
                result -> requireTable(result, "t").reformat(NOTHING),
                "[t]\nx = [\n  1,\n  # c\n]\n"));
  }

  @Test
  void rejectsANullAmountToKeep() {
    TomlParseResult result = Toml.parse("a = [1]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertThrows(NullPointerException.class, () -> result.reformat(null));
    assertThrows(NullPointerException.class, () -> requireArray(result, "a").reformat(null));
  }

  @Test
  void copiesWhatReformatSetOnATable() {
    TomlParseResult result = Toml.parse("[a]\nx = 0x10  # kept\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    MutableTomlTable table = requireTable(result, "a");
    table.reformat(NOTHING);
    result.set("z", MutableTomlTable.copyOf(table));

    assertWrites(result, LF, "[a]\nx = 16  # kept\n\n[z]\nx = 16  # kept\n");
  }

  @Test
  void copiesWhatReformatSetOnAnArray() {
    TomlParseResult result = Toml.parse("a = [ 0x10,2 ]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    MutableTomlArray array = requireArray(result, "a");
    array.reformat(NOTHING);
    result.set("b", MutableTomlArray.copyOf(array));

    assertWrites(result, LF, "a = [16, 2]\nb = [16, 2]\n");
  }

  @Test
  void writesADocumentWithNoSourceTheSameWayRegardlessOfReformat() {
    TomlParseResult result = Toml.parse("a   =   0x10\n[t]\nx = 1\n", TomlParseOptions.defaults().withoutSource());
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    String written = result.toToml(LF);

    requireTable(result, "t").reformat(NOTATION);
    assertEquals(written, result.toToml(LF));
    result.reformat(NOTHING);
    assertEquals(written, result.toToml(LF));
  }

  @Test
  void writesATableBuiltThroughTheEditingApiTheSameWayRegardlessOfReformat() {
    MutableTomlTable table = MutableTomlTable.create().set("a", 1).set("t.x", 2);
    String written = table.toToml(LF);

    table.reformat(NOTATION);
    assertEquals(written, table.toToml(LF));
    table.reformat(NOTHING);
    assertEquals(written, table.toToml(LF));
  }

  /**
   * Assert what a document is written as, and that parsing it again gives back the values and the comments it holds.
   *
   * @param result The document.
   * @param options The options to write it with.
   * @param expected What it is expected to be written as.
   */
  private static void assertWrites(TomlParseResult result, TomlWriteOptions options, String expected) {
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
      TomlWriteOptions options,
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
