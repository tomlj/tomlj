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

  // New lines are written with the separator these options ask for; the lines of the document keep the one they had
  private static final TomlWriteOptions LF = TomlWriteOptions.defaults().withLineSeparator("\n");

  @ParameterizedTest
  @MethodSource("org.tomlj.SourceSpanTest#allDocuments")
  void writesAnUneditedDocumentBackUnchanged(String input) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals(input, result.toToml());
    // The options shape only what is written anew, never what comes from the document
    assertEquals(input, result.toToml(TomlWriteOptions.defaults().withIndent(4).withMaxLineWidth(0)));
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
  void writesATableThatIsNotTheDocumentRootAsNotationWritesIt() {
    TomlParseResult result = Toml.parse("[a]\n  x = 0x10  # note\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    TomlTable a = result.getTable("a");
    assertNotNull(a);
    assertEquals("x = 0x10  # note\n", a.toToml(LF));
  }

  @Test
  void writesADocumentInTheDefaultStyleWhenTheOptionsKeepNothing() {
    String input = "# a run\n[a]\n  x = 0x10  # note\n\n[[b]]\n  y = [ 1,2 ]\n";
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    TomlParseResult withoutSource = Toml.parse(input, TomlParseOptions.defaults().withoutSource());

    assertEquals(withoutSource.toToml(LF), result.toToml(LF.keep(TomlWriteOptions.Keep.NOTHING)));
  }

  @Test
  void writesNewLinesWithTheSeparatorTheOptionsAskFor() {
    TomlParseResult result = Toml.parse("a = 1\r\n[t]\r\nb = 2\r\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    result.set("c", 3);

    assertEquals(
        "a = 1\r\nc = 3\r\n[t]\r\nb = 2\r\n",
        result.toToml(TomlWriteOptions.defaults().withLineSeparator("\r\n")));
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
  @MethodSource("documentsWithNotationKept")
  void writesADocumentKeepingItsNotation(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlWriteOptions options,
      String expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    edit.accept(result);

    String written = result.toToml(options);
    assertEquals(expected, written);

    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
    TomlAssertions.assertSameComments(result, reparsed);
  }

  static Stream<Arguments> documentsWithNotationKept() {
    TomlWriteOptions notation =
        TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTATION).withLineSeparator("\n");
    return Stream
        .of(
            notationKept("the spacing around the equals is normalized", "a   =    1\n", notation, "a = 1\n"),
            notationKept("blank lines never double up", "a = 1\n\n\n\nb = 2\n", notation, "a = 1\n\nb = 2\n"),
            notationKept("the blank lines a document opens with go", "\n\na = 1\n", notation, "a = 1\n"),
            notationKept("the blank lines a document ends with go", "a = 1\n\n\n", notation, "a = 1\n"),
            notationKept("a document with no newline at its end gets one", "a = 1", notation, "a = 1\n"),
            notationKept("the whitespace a line ends with goes", "a = 1   \nb = 2\t\n", notation, "a = 1\nb = 2\n"),
            notationKept(
                "a header is separated from the line above it",
                "a = 1\n[t]\nb = 2\n[u]\nc = 3\n",
                notation,
                "a = 1\n\n[t]\nb = 2\n\n[u]\nc = 3\n"),
            notationKept(
                "each table of an array of tables is separated from the one above it",
                "[[t]]\nx = 1\n[[t]]\nx = 2\n",
                notation,
                "[[t]]\nx = 1\n\n[[t]]\nx = 2\n"),
            notationKept(
                "the indentation of a document goes",
                "[t]\n    a = 1\n    [t.u]\n        b = 2\n",
                notation,
                "[t]\na = 1\n\n[t.u]\nb = 2\n"),
            notationKept(
                "the indentation of a document is the one the options ask for",
                "[t]\na = 1\n[t.u]\nb = 2\n[[q]]\nz = 1\n",
                notation.withIndent(2),
                "[t]\n  a = 1\n\n  [t.u]\n    b = 2\n\n[[q]]\n  z = 1\n"),
            notationKept(
                "the literal of every value is kept",
                "a = 0xff\nb = 1_000\nc = 'literal'\nd = \"\"\"multi\nline\"\"\"\n"
                    + "e = 1979-05-27 07:32:00\nf = +1.0e3\n",
                notation,
                "a = 0xff\nb = 1_000\nc = 'literal'\nd = \"\"\"multi\nline\"\"\"\n"
                    + "e = 1979-05-27 07:32:00\nf = +1.0e3\n"),
            notationKept(
                "a key keeps the form it was written in",
                "'a b'   =   1\n\"c d\" = 2\nt = { 'e f' = 3 }\n",
                notation,
                "'a b' = 1\n\"c d\" = 2\nt = { 'e f' = 3 }\n"),
            notationKept(
                "a dotted key stays dotted",
                "a.b.c = 1\nq = { p.r = 2, s = 3 }\n",
                notation,
                "a.b.c = 1\nq = { p.r = 2, s = 3 }\n"),
            notationKept(
                "the entries of an inline table keep the order they were written in",
                "q = { a.b = 1, c = 2, a.d = 3 }\n",
                notation,
                "q = { a.b = 1, c = 2, a.d = 3 }\n"),
            notationKept(
                "an inline table stays inline and is laid out anew",
                "t = {p=1,q=2}\n",
                notation,
                "t = { p = 1, q = 2 }\n"),
            notationKept(
                "an array written over lines is packed onto one that fits",
                "a = [\n  1,\n  0x2,\n]\n",
                notation,
                "a = [1, 0x2]\n"),
            notationKept(
                "an array written on one line that does not fit is written over lines",
                "a = [\"aaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccccccccc\"]\n",
                notation,
                "a = [\n  \"aaaaaaaaaaaaaaaaaaaaaaaaa\",\n  \"bbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n"
                    + "  \"cccccccccccccccccccc\",\n]\n"),
            notationKept(
                "an array holding a comment is written over lines",
                "a = [\n  1, # one\n  2,\n]\n",
                notation,
                "a = [\n  1,  # one\n  2,\n]\n"),
            notationKept(
                "the comment after a value is separated from it by two spaces",
                "a = 1 #c\nb = 2      # spaced\n",
                notation,
                "a = 1  #c\nb = 2  # spaced\n"),
            notationKept(
                "the comment run above a line is indented like the line",
                "[t]\n      # about x\n  x = 1\n",
                notation.withIndent(2),
                "[t]\n  # about x\n  x = 1\n"),
            notationKept(
                "an unattached comment is given a blank line above it",
                "x = 1\n# note\n\ny = 2\n",
                notation,
                "x = 1\n\n# note\n\ny = 2\n"),
            notationKept(
                "the trailing comment of a table keeps its place under the line above it",
                "[t]\nx = 1\n# tail\n\n[u]\ny = 2\n",
                notation,
                "[t]\nx = 1\n# tail\n\n[u]\ny = 2\n"),
            notationKept(
                "a comment of the root written after a section keeps its blank line",
                "[t]\nx = 1\n\n# root note\n",
                notation,
                "[t]\nx = 1\n\n# root note\n"),
            notationKept(
                "a document is written with the separator its own lines end with",
                "a = 1\r\n[t]\r\nb = 2\r\n",
                TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTATION),
                "a = 1\r\n\r\n[t]\r\nb = 2\r\n"),
            notationKept(
                "an edited document is written in the same layout",
                "a = 0x10  # note\nb = 2\nc = 3\n",
                result -> {
                  result.set("a", 5);
                  result.remove("b");
                  result.set("d", 4);
                },
                notation,
                "a = 5  # note\nc = 3\nd = 4\n"),
            notationKept(
                "a table copied into the document keeps the literals it was read with",
                "[a.b]\n  x = 0x10  # kept\n",
                result -> result.set("z", requireTable(result, "a.b")),
                notation,
                "[a.b]\nx = 0x10  # kept\n\n[z]\nx = 0x10  # kept\n"),
            notationKept(
                "an inline table and an array of tables copied from another document keep their brackets",
                "x = 1\n",
                result -> {
                  TomlParseResult other = Toml.parse("t = { a = 0x1 }\naot = [ { b = 2 } ]\n");
                  result.set("t", other.get("t"));
                  result.set("aot", other.get("aot"));
                },
                notation,
                "x = 1\nt = { a = 0x1 }\naot = [{ b = 2 }]\n"));
  }

  @ParameterizedTest
  @MethodSource("org.tomlj.SourceSpanTest#allDocuments")
  void keepingTheNotationWritesTextThatParsesBackToTheSameDocument(String input) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    String written = result.toToml(TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTATION).withIndent(2));
    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
    TomlAssertions.assertSameComments(result, reparsed);
  }

  @Test
  void writesADocumentWithNoRetainedSourceInTheDefaultStyleWhenTheOptionsKeepTheNotation() {
    String input = "# a run\n[a]\n  x = 0x10  # note\n\n[[b]]\n  y = [ 1,2 ]\n";
    TomlParseResult withoutSource = Toml.parse(input, TomlParseOptions.defaults().withoutSource());
    assertFalse(withoutSource.hasErrors(), () -> joinErrors(withoutSource));

    assertEquals(withoutSource.toToml(LF), withoutSource.toToml(LF.keep(TomlWriteOptions.Keep.NOTATION)));
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
                "a line of a copied table written anew keeps the literal of its value",
                "[a.b]\nx = 0x10\n",
                result -> result
                    .set("z", MutableTomlTable.copyOf(requireTable(result, "a.b")))
                    .setComment("z.x", "note", TomlComment.Placement.AFTER),
                "[a.b]\nx = 0x10\n\n[z]\nx = 0x10  # note\n"),
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
                "a value replaced by one read from another line keeps that value's literal",
                "a = 1  # note\nb = 0x10\n[t]\nc = 2\n",
                result -> {
                  result.set("a", result.entry("b").value());
                  requireTable(result, "t").set("c", Toml.parse("d = 'lit'\n").entry("d").value());
                },
                "a = 0x10  # note\nb = 0x10\n[t]\nc = 'lit'\n"),
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

  private static Arguments notationKept(String description, String input, TomlWriteOptions options, String expected) {
    return notationKept(description, input, result -> {
    }, options, expected);
  }

  private static Arguments notationKept(
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
