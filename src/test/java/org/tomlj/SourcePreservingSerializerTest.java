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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks what a parse result is written as: the document it was parsed from, with what the editing API changed written
 * anew where it belongs.
 */
class SourcePreservingSerializerTest {

  // New lines are written with the separator these options ask for; the lines of the document keep their own
  private static final TomlOptions LF = TomlOptions.defaults().withLineSeparator("\n");

  @ParameterizedTest
  @MethodSource("org.tomlj.SourceSpanTest#allDocuments")
  void writesAnUneditedDocumentBackUnchanged(String input) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals(input, result.toToml());
    // The options shape only what is written anew, never what comes from the document
    assertEquals(input, result.toToml(TomlOptions.defaults().withIndent(4).withMaxLineWidth(0)));
  }

  @ParameterizedTest
  @MethodSource("org.tomlj.SourceSpanTest#skippedLineDocuments")
  void writesADocumentWithoutTheLinesTheParserRejected(String input, String expected) {
    TomlParseResult result = Toml.parse(input);
    assertTrue(result.hasErrors(), () -> "the document was expected to be rejected in part");

    assertEquals(expected, result.toToml());
  }

  @Test
  void appendsTheSameTextAsItReturns() throws IOException {
    TomlParseResult result = Toml.parse("a = 1  # note\n\n[t]\nb = [ 2,3 ]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    result.set("c", 3);

    StringWriter out = new StringWriter();
    result.toToml(out, LF);
    assertEquals(result.toToml(LF), out.toString());
  }

  @Test
  void writesATableThatIsNotTheDocumentRootInTheDefaultStyle() {
    TomlParseResult result = Toml.parse("[a]\n  x = 0x10  # note\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    TomlTable a = result.getTable("a");
    assertNotNull(a);
    assertEquals("x = 16  # note\n", a.toToml(LF));
  }

  @Test
  void writesADocumentInTheDefaultStyleWhenTheOptionsAskForTheCanonicalOne() {
    String input = "# a run\n[a]\n  x = 0x10  # note\n\n[[b]]\n  y = [ 1,2 ]\n";
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    TomlParseResult withoutSource = Toml.parse(input, TomlParseOptions.defaults().withoutSource());

    assertEquals(withoutSource.toToml(LF), result.toToml(LF.canonical()));
  }

  @Test
  void writesNewLinesWithTheSeparatorTheOptionsAskFor() {
    TomlParseResult result = Toml.parse("a = 1\r\n[t]\r\nb = 2\r\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    result.set("c", 3);

    assertEquals("a = 1\r\nc = 3\r\n[t]\r\nb = 2\r\n", result.toToml(TomlOptions.defaults().withLineSeparator("\r\n")));
    assertEquals("a = 1\r\nc = 3\n[t]\r\nb = 2\r\n", result.toToml(LF));
  }

  @Test
  void writesNewLinesTheWayTheDocumentEndsItsOwnWhenTheOptionsAskForNoSeparator() {
    TomlParseResult result = Toml.parse("a = 1\r\n[t]\r\nb = 2\r\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    result.set("c", 3);
    result.getOrCreateTable("u").set("d", 4);

    assertEquals("a = 1\r\nc = 3\r\n[t]\r\nb = 2\r\n\r\n[u]\r\nd = 4\r\n", result.toToml());
  }

  @Test
  void writesACommentSetOnALineWithTheSeparatorTheDocumentEndsItsLinesWith() {
    TomlParseResult result = Toml.parse("a = 1\r\n# about b\r\nb = 2\r\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    result.setCommentAbove("a", "note", "and more");

    assertEquals("# note\r\n# and more\r\na = 1\r\n# about b\r\nb = 2\r\n", result.toToml());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("editedDocuments")
  void writesAnEditIntoTheDocument(String description, String input, Consumer<TomlParseResult> edit, String expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    edit.accept(result);

    String written = result.toToml(LF);
    assertEquals(expected, written);

    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
    TomlAssertions.assertSameComments(result, reparsed);
  }

  static Stream<Arguments> editedDocuments() {
    return Stream
        .of(
            edited(
                "a new entry of the root table goes before the first header",
                "a = 1\n\n[t]\nb = 2\n",
                result -> result.set("c", 3),
                "a = 1\nc = 3\n\n[t]\nb = 2\n"),
            edited(
                "a new entry goes after the last line of its table's section",
                "[t]\nb = 2\n[u]\nc = 3\n",
                result -> requireTable(result, "t").set("z", 9),
                "[t]\nb = 2\nz = 9\n[u]\nc = 3\n"),
            edited(
                "a new entry is indented like the entries of its section",
                "[t]\n  a = 1\n[u]\nc = 3\n",
                result -> requireTable(result, "t").set("b", 2),
                "[t]\n  a = 1\n  b = 2\n[u]\nc = 3\n"),
            edited(
                "a new entry is indented with the tabs the entries of its section use",
                "[t]\n\ta = 1\n",
                result -> requireTable(result, "t").set("b", 2),
                "[t]\n\ta = 1\n\tb = 2\n"),
            edited(
                "a new entry of a table a dotted key opened is written with that key",
                "[t]\n  a.b = 1\n",
                result -> requireTable(result, "t.a").set("z", 2),
                "[t]\n  a.b = 1\n  a.z = 2\n"),
            edited(
                "a new entry of a table with no header of its own is written as a dotted key",
                "[a.b]\nx = 1\n",
                result -> requireTable(result, "a").set("y", 2),
                "a.y = 2\n[a.b]\nx = 1\n"),
            edited(
                "a new entry inserted before the only entry of a section goes above its line",
                "[t]\na = 1\n",
                result -> requireTable(result, "t").insertBefore("a", "x", 9),
                "[t]\nx = 9\na = 1\n"),
            edited(
                "a new entry inserted after an entry in the middle of a section follows its line",
                "[t]\na = 1\nb = 2\nc = 3\n",
                result -> requireTable(result, "t").insertAfter("a", "x", 9),
                "[t]\na = 1\nx = 9\nb = 2\nc = 3\n"),
            edited(
                "a new entry inserted before an entry in the middle of a section follows the line above it",
                "[t]\na = 1\nb = 2\nc = 3\n",
                result -> requireTable(result, "t").insertBefore("c", "x", 9),
                "[t]\na = 1\nb = 2\nx = 9\nc = 3\n"),
            edited(
                "a new entry inserted after an unattached comment is separated from it",
                "a = 1\n\n# note\n\nb = 2\n",
                result -> result.insertAfter(result.elements().get(1), "c", 3),
                "a = 1\n\n# note\n\nc = 3\n\nb = 2\n"),
            edited(
                "a new table goes after the last line of its parent's subtree",
                "[t]\nb = 2\n[u]\nc = 3\n",
                result -> requireTable(result, "t").getOrCreateTable("s").set("k", 1),
                "[t]\nb = 2\n\n[t.s]\nk = 1\n[u]\nc = 3\n"),
            edited(
                "a new table of the root goes at the end of the document",
                "a = 1\n[t]\nb = 2\n",
                result -> result.getOrCreateTable("new").set("k", 1),
                "a = 1\n[t]\nb = 2\n\n[new]\nk = 1\n"),
            edited(
                "a new table of an array of tables follows the last one",
                "[[t]]\nx = 1\n[q]\ny = 2\n",
                result -> requireArray(result, "t").add(MutableTomlTable.create().set("x", 2)),
                "[[t]]\nx = 1\n\n[[t]]\nx = 2\n[q]\ny = 2\n"),
            edited(
                "an entry that is removed takes the comment above it",
                "a = 1\n# about b\nb = 2\nc = 3\n",
                result -> result.remove("b"),
                "a = 1\nc = 3\n"),
            edited(
                "a table that is removed takes its whole section",
                "[t]\nx = 1\n[u]\ny = 2\n",
                result -> result.remove("t"),
                "[u]\ny = 2\n"),
            edited(
                "a table a dotted key opened, left empty by a removal, is written under a header",
                "a.b = 1\n",
                result -> result.remove("a.b"),
                "[a]\n"),
            edited(
                "a table left empty by a removal keeps its place in the section that held it",
                "[t]\na.b = 1\nx = 2\n",
                result -> result.remove("t.a.b"),
                "[t]\nx = 2\n\n[t.a]\n"),
            edited(
                "an unattached comment inserted before an entry is separated from the lines around it",
                "a = 1\nb = 2\n",
                result -> result.insertCommentBefore("b", "note"),
                "a = 1\n\n# note\n\nb = 2\n"),
            edited(
                "an unattached comment inserted above a line with a blank line above it does not double it",
                "a = 1\n\nb = 2\n",
                result -> result.insertCommentBefore("b", "note"),
                "a = 1\n\n# note\n\nb = 2\n"),
            edited(
                "an unattached comment added to a section followed by a header is written under its last line",
                "[t]\nx = 1\n[u]\n",
                result -> requireTable(result, "t").addComment("note"),
                "[t]\nx = 1\n# note\n\n[u]\n"),
            edited(
                "an unattached comment added to the root of a document with sections goes after the last of them",
                "a = 1\n[t]\nb = 2\n",
                result -> result.addComment("note"),
                "a = 1\n[t]\nb = 2\n\n# note\n"),
            edited(
                "a new entry of the root goes before the first header, comments written between sections aside",
                "[fruit]\nx = 1\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n",
                result -> result.set("z", 3),
                "z = 3\n[fruit]\nx = 1\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n"),
            edited(
                "an unattached comment inserted before a section of the root goes before its header",
                "[fruit]\nx = 1\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n",
                result -> result.insertCommentBefore("fruit", "note"),
                "# note\n\n[fruit]\nx = 1\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n"),
            edited(
                "an unattached comment inserted after a section of the root goes before the run written in it",
                "[fruit]\nx = 1\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n",
                result -> result.insertCommentAfter("fruit", "note"),
                "[fruit]\nx = 1\n\n# note\n\n# a note about the header below\n\n[fruit.apple]\ny = 2\n"),
            edited(
                "two unattached runs left side by side by a removal stay two runs",
                "x = 1\n# one\n\ny = 2\n# two\n",
                result -> result.remove("y"),
                "x = 1\n# one\n\n# two\n"),
            edited(
                "a comment set on a table is written above its header",
                "[t]\nx = 0x10\n",
                result -> result.setCommentAbove("t", "note"),
                "# note\n[t]\nx = 0x10\n"),
            edited(
                "a comment added to a table a dotted key opened gives it a header to hold it",
                "name.first = \"Arthur\"\n",
                result -> requireTable(result, "name").addComment("note"),
                "[name]\nfirst = \"Arthur\"\n# note\n"),
            edited(
                "a table stored under another key is written as a section, keeping the text of its lines",
                "[a.b]\nx = 0x10  # kept\n",
                result -> result.set("z", requireTable(result, "a.b")),
                "[a.b]\nx = 0x10  # kept\n\n[z]\nx = 0x10  # kept\n"),
            edited(
                "a table from another document keeps the text of its lines too",
                "a = 1\n",
                result -> result.set("w", requireTable(Toml.parse("[q]\nk = 0b11  # other\n"), "q")),
                "a = 1\n\n[w]\nk = 0b11  # other\n"),
            edited(
                "a value that is replaced keeps its line and the comment on it",
                "a = 1  # note\nb = 2\n",
                result -> result.set("a", 5),
                "a = 5  # note\nb = 2\n"),
            edited(
                "a value that is replaced keeps the comment above it and its place in its section",
                "[t]\n# about x\nx = 1\ny = 2\n",
                result -> requireTable(result, "t").set("x", "new"),
                "[t]\n# about x\nx = \"new\"\ny = 2\n"),
            edited(
                "a value a dotted key names keeps its line when it is replaced",
                "a.b = 1\nc = 2\n",
                result -> result.set("a.b", 9),
                "a.b = 9\nc = 2\n"),
            edited(
                "an inline table replaced by a value keeps its line",
                "t = { p = 1 }  # note\nu = 2\n",
                result -> result.set("t", 3),
                "t = 3  # note\nu = 2\n"),
            edited(
                "a value replaced by a string with a newline is written over lines, and the line keeps its comment",
                "a = 1  # note\n",
                result -> result.set("a", "x\ny"),
                "a = \"\"\"\nx\ny\"\"\"  # note\n"),
            edited(
                "an array replaced by a scalar keeps its line",
                "a = [1, 2]\nb = 3\n",
                result -> result.set("a", 9),
                "a = 9\nb = 3\n"),
            edited(
                "a scalar replaced by an array that fits on the line keeps it",
                "a = 1  # note\n",
                result -> result.set("a", MutableTomlArray.of(1, 2)),
                "a = [1, 2]  # note\n"),
            edited(
                "a value replaced by a table leaves its line and is written as a section",
                "a = 1  # note\nb = 2\n",
                result -> result.set("a", MutableTomlTable.create().set("k", 1)),
                "b = 2\n\n[a]  # note\nk = 1\n"),
            edited(
                "a value replaced by an array of tables leaves its line and is written as sections",
                "a = 1\nb = 2\n",
                result -> result.set("a", MutableTomlArray.of(MutableTomlTable.create().set("k", 1))),
                "b = 2\n\n[[a]]\nk = 1\n"),
            edited(
                "an array of tables that replaces a value keeps the line when a comment is written on it",
                "a = 1  # note\nb = 2\n",
                result -> result.set("a", MutableTomlArray.of(MutableTomlTable.create().set("k", 1))),
                "a = [{ k = 1 }]  # note\nb = 2\n"),
            edited(
                "an array of inline tables the document wrote as a literal keeps its line",
                "x = [{ a = 1 }]\ny = 2\n",
                result -> result.set("z", 3),
                "x = [{ a = 1 }]\ny = 2\nz = 3\n"),
            edited(
                "a value replaced in a table of an array of tables keeps its line",
                "[[x]]\na = 1  # note\n",
                result -> requireArray(result, "x").getTable(0).set("a", 2),
                "[[x]]\na = 2  # note\n"),
            edited(
                "a value replaced in a copied table keeps the line's spacing and its comment",
                "[a.b]\nx = 0x10  # kept\ny = 1\n",
                result -> requireTable(result.set("z", requireTable(result, "a.b")), "z").set("x", 5),
                "[a.b]\nx = 0x10  # kept\ny = 1\n\n[z]\nx = 5  # kept\ny = 1\n"),
            edited(
                "a value replaced twice is written as it was left",
                "a = 1  # note\n",
                result -> result.set("a", 5).set("a", 6),
                "a = 6  # note\n"),
            edited(
                "a comment set above a line is indented like it",
                "[t]\n\tx = 1\n",
                result -> requireTable(result, "t").setCommentAbove("x", "note"),
                "[t]\n\t# note\n\tx = 1\n"),
            edited(
                "a run replaced by a longer one",
                "# one\na = 1\n",
                result -> result.setCommentAbove("a", "first", "second"),
                "# first\n# second\na = 1\n"),
            edited(
                "a run replaced by a shorter one",
                "# one\n# two\na = 1\n",
                result -> result.setCommentAbove("a", "new"),
                "# new\na = 1\n"),
            edited(
                "a run that is removed takes its lines, and the blank lines above it stay",
                "x = 0\n\n# one\n# two\na = 1\n",
                result -> result.removeCommentAbove("a"),
                "x = 0\n\na = 1\n"),
            edited(
                "a comment set after a value is written two spaces from it",
                "a = 1\nb = 2\n",
                result -> result.setCommentAfter("a", "note"),
                "a = 1  # note\nb = 2\n"),
            edited(
                "a comment that replaces another keeps the spacing the document wrote before it",
                "a = 1    # old\n",
                result -> result.setCommentAfter("a", "new"),
                "a = 1    # new\n"),
            edited(
                "a comment after a value that is removed takes the spacing before it",
                "a = 1    # old\nb = 2\n",
                result -> result.removeCommentAfter("a"),
                "a = 1\nb = 2\n"),
            edited(
                "a comment set above a header keeps the header line as it was written",
                "[a.b]  # h\nx = 1\n",
                result -> result.setCommentAbove("a.b", "note"),
                "# note\n[a.b]  # h\nx = 1\n"),
            edited(
                "a comment removed from a header line takes the spacing before it",
                "[a.b]  # h\nx = 1\n",
                result -> result.removeCommentAfter("a.b"),
                "[a.b]\nx = 1\n"),
            edited(
                "a comment set above the header of a table of an array of tables",
                "[[x]]\na = 1\n",
                result -> requireArray(result, "x").setCommentAbove(0, "note"),
                "# note\n[[x]]\na = 1\n"),
            edited(
                "both comments of an entry edited at once",
                "# old\na = 1  # old after\n",
                result -> result.setCommentAbove("a", "new").setCommentAfter("a", "new after"),
                "# new\na = 1  # new after\n"),
            edited(
                "a value replaced and the comment on its line edited",
                "a = 1  # old\n",
                result -> result.set("a", 2).setCommentAfter("a", "new"),
                "a = 2  # new\n"),
            edited(
                "a comment set on the last line of a document that ends without a newline",
                "a = 1",
                result -> result.setCommentAfter("a", "note"),
                "a = 1  # note"),
            edited("a document with no newline at its end", "a = 1", result -> result.set("b", 2), "a = 1\nb = 2\n"),
            edited("an empty document", "", result -> result.set("a", 1), "a = 1\n"),
            edited(
                "a new table in an empty document",
                "",
                result -> result.getOrCreateTable("t").set("k", 1),
                "[t]\nk = 1\n"));
  }

  private static Arguments edited(String description, String input, Consumer<TomlParseResult> edit, String expected) {
    return Arguments.of(description, input, edit, expected);
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
