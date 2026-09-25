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

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalTime;
import java.util.List;
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
  private static final TomlWriteOptions TOML_100 = LF.withVersion(TomlVersion.V1_0_0);

  private static final String INLINE_TABLE_OVER_LINES =
      "An inline table holding a comment cannot be written for TOML 1.0.0, which allows no line break inside an inline table";

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

  @Test
  void writesAnElementAddedToAnArrayWithTheSeparatorTheDocumentEndsItsLinesWith() {
    TomlParseResult result = Toml.parse("a = [\r\n  1,\r\n  2\r\n]\r\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    MutableTomlArray array = result.getArray("a");
    assertNotNull(array);
    array.add(3);

    assertEquals("a = [\r\n  1,\r\n  2,\r\n  3\r\n]\r\n", result.toToml());
  }

  @Test
  void throwsForAnInlineTableACommentSetOnAnEntryPutsOverLinesForToml100() {
    TomlParseResult result = Toml.parse("t = { b = 1, c = 2 }\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    requireTable(result, "t").setCommentAfter("b", "note");

    assertEquals("t = {\n  b = 1,  # note\n  c = 2\n}\n", result.toToml(LF));
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> result.toToml(LF.withVersion(TomlVersion.V1_0_0)));
    assertEquals(INLINE_TABLE_OVER_LINES, e.getMessage());
  }

  @Test
  void throwsForAnInlineTableAValueHoldingACommentPutsOverLinesForToml100() {
    TomlParseResult result = Toml.parse("t = { b = 1, c = 2 }\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    requireTable(result, "t").set("c", MutableTomlArray.of(1).addComment("note"));

    assertEquals("t = {\n  b = 1,\n  c = [\n    1,\n    # note\n  ]\n}\n", result.toToml(LF));
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> result.toToml(LF.withVersion(TomlVersion.V1_0_0)));
    assertEquals(INLINE_TABLE_OVER_LINES, e.getMessage());
  }

  @Test
  void writesAnArrayACommentPutsOverLinesForToml100() {
    TomlParseResult result = Toml.parse("a = [1, 2]\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    requireArray(result, "a").setCommentAfter(0, "note");

    assertEquals("a = [\n  1,  # note\n  2\n]\n", result.toToml(LF.withVersion(TomlVersion.V1_0_0)));
  }

  @Test
  void throwsForAnEditedInlineTableTheDocumentWroteOverLinesForToml100() {
    TomlParseResult result = Toml.parse("t = {\n  b = 1,\n  c = 2,\n}\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    requireTable(result, "t").set("c", 3);

    assertEquals("t = {\n  b = 1,\n  c = 3,\n}\n", result.toToml(LF));
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> result.toToml(LF.withVersion(TomlVersion.V1_0_0)));
    assertEquals(
        "A newline inside an inline table originally at line 1, column 6 needs TOML 1.1.0 and cannot be written for TOML 1.0.0",
        e.getMessage());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("toml110Constructs")
  void refusesToCopyAConstructToml100CannotWrite(
      String description,
      String input,
      String construct,
      String keepingNotation,
      String keepingNothing) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    String message = construct + " needs TOML 1.1.0 and cannot be written for TOML 1.0.0";

    assertEquals(input, result.toToml(LF));
    // Keeping the layout copies the line the construct is on
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> result.toToml(TOML_100));
    assertEquals(message, e.getMessage());
    // Keeping the notation copies the literal of each key and scalar, but lays an inline table out anew
    if (keepingNotation == null) {
      e = assertThrows(
          IllegalArgumentException.class,
          () -> result.toToml(TOML_100.keep(TomlWriteOptions.Keep.NOTATION)));
      assertEquals(message, e.getMessage());
    } else {
      assertWritesForToml100(result, TOML_100.keep(TomlWriteOptions.Keep.NOTATION), keepingNotation);
    }
    assertWritesForToml100(result, TOML_100.keep(TomlWriteOptions.Keep.NOTHING), keepingNothing);
  }

  static Stream<Arguments> toml110Constructs() {
    return Stream
        .of(
            construct(
                "an escape in a string",
                "a = \"\\e[0m\"\n",
                "The escape sequence '\\e' originally at line 1, column 6",
                null,
                "a = \"\\u001b[0m\"\n"),
            construct(
                "a hex escape in a string",
                "a = \"\\x41\"\n",
                "The escape sequence '\\x41' originally at line 1, column 6",
                null,
                "a = \"A\"\n"),
            construct(
                "an escape in a multi-line string",
                "a = \"\"\"\n\\e\"\"\"\n",
                "The escape sequence '\\e' originally at line 2, column 1",
                null,
                "a = \"\\u001b\"\n"),
            construct(
                "an escape in a key",
                "\"\\e\" = 1\n",
                "The escape sequence '\\e' originally at line 1, column 2",
                null,
                "\"\\u001b\" = 1\n"),
            construct(
                "an escape in a header",
                "[\"\\e\"]\nx = 1\n",
                "The escape sequence '\\e' originally at line 1, column 3",
                null,
                "[\"\\u001b\"]\nx = 1\n"),
            construct(
                "a time without seconds",
                "a = 07:32\n",
                "A time without seconds originally at line 1, column 5",
                null,
                "a = 07:32:00\n"),
            construct(
                "a date-time without seconds",
                "a = 1979-05-27T07:32Z\n",
                "A time without seconds originally at line 1, column 16",
                null,
                "a = 1979-05-27T07:32:00Z\n"),
            construct(
                "a newline inside an inline table",
                "t = {\n  x = 1 }\n",
                "A newline inside an inline table originally at line 1, column 6",
                "t = { x = 1 }\n",
                "[t]\nx = 1\n"),
            construct(
                "a trailing comma in an inline table",
                "t = { x = 1, }\n",
                "A trailing comma in an inline table originally at line 1, column 12",
                "t = { x = 1 }\n",
                "[t]\nx = 1\n"),
            construct(
                "an escape inside an array",
                "a = [1, \"\\e\"]\n",
                "The escape sequence '\\e' originally at line 1, column 10",
                null,
                "a = [1, \"\\u001b\"]\n"),
            construct(
                "a time inside an inline table inside an array",
                "a = [{ t = 07:32 }]\n",
                "A time without seconds originally at line 1, column 12",
                null,
                "[[a]]\nt = 07:32:00\n"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("editsRemovingAToml110Construct")
  void writesForToml100OnceTheConstructIsEditedAway(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlWriteOptions options,
      String expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertThrows(IllegalArgumentException.class, () -> result.toToml(options));
    edit.accept(result);

    assertWritesForToml100(result, options, expected);
  }

  static Stream<Arguments> editsRemovingAToml110Construct() {
    return Stream
        .of(
            edited(
                "the value is replaced",
                "a = 07:32\nb = 1\n",
                result -> result.set("a", LocalTime.of(7, 32)),
                TOML_100,
                "a = 07:32:00\nb = 1\n"),
            edited("the line is removed", "a = \"\\e\"\nb = 1\n", result -> result.remove("a"), TOML_100, "b = 1\n"),
            edited(
                "the value of an entry of an inline table is replaced",
                "t = { a = \"\\e\", b = 1 }\n",
                result -> requireTable(result, "t").set("a", "x"),
                TOML_100,
                "t = { a = \"x\", b = 1 }\n"),
            edited(
                "the entry of an inline table is removed",
                "t = { a = 07:32, b = 1 }\n",
                result -> requireTable(result, "t").remove("a"),
                TOML_100,
                "t = { b = 1 }\n"),
            edited(
                "the element of an array is removed",
                "a = [07:32, 1]\n",
                result -> requireArray(result, "a").remove(0),
                TOML_100,
                "a = [1]\n"),
            edited(
                "the inline table with the trailing comma is reformatted",
                "t = { x = 1, }\n",
                result -> requireTable(result, "t").reformat(TomlWriteOptions.Keep.NOTATION),
                TOML_100,
                "t = { x = 1 }\n"),
            edited(
                "the line with the escape is reformatted keeping nothing",
                "a = \"\\e\"\nb = 1\n",
                result -> result.reformat(TomlWriteOptions.Keep.NOTHING),
                TOML_100,
                "a = \"\\u001b\"\nb = 1\n"));
  }

  private static Arguments construct(
      String description,
      String input,
      String construct,
      String keepingNotation,
      String keepingNothing) {
    return Arguments.of(description, input, construct, keepingNotation, keepingNothing);
  }

  private static void assertWritesForToml100(TomlParseResult result, TomlWriteOptions options, String expected) {
    String written = result.toToml(options);
    assertEquals(expected, written);

    TomlParseResult reparsed = Toml.parse(written, TomlVersion.V1_0_0);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
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
                "x = 1\nt = { a = 0x1 }\naot = [{ b = 2 }]\n"),
            notationKept(
                "the entries of a table are indented like its header when the options align them",
                "[t]\na = 1\n[t.u]\nb = 2\n[[q]]\nz = 1\n",
                notation.withIndent(2).withEntriesAlignedWithHeaders(true),
                "[t]\na = 1\n\n  [t.u]\n  b = 2\n\n[[q]]\nz = 1\n"),
            notationKept(
                "an array is written with a space inside its brackets when the options ask for it",
                "a = [1,0x2]\nb = []\n",
                notation.withSpaceInsideArrays(true),
                "a = [ 1, 0x2 ]\nb = []\n"));
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
  void writesAnEditIntoTheDocument(
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

  @Test
  void keepsAnEditedInlineTableValidForTheVersionItWasParsedAs() {
    // TOML 1.0.0 allows a newline inside an inline table only within a value, so the table has to stay on its line
    String input = "t = { a = \"\"\"\nx\n\"\"\", b = 2 }\n";
    TomlParseResult result = Toml.parse(input, TomlVersion.V1_0_0);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    requireTable(result, "t").set("c", 3);

    String written = result.toToml(LF);
    TomlParseResult reparsed = Toml.parse(written, TomlVersion.V1_0_0);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
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
                "a value replaced by a table made inline keeps its line",
                "a = 1  # note\nb = 2\n",
                result -> result.set("a", MutableTomlTable.createInline().set("k", 1)),
                "a = { k = 1 }  # note\nb = 2\n"),
            edited(
                "a table made inline added to a section goes on a line of it",
                "[t]\na = 1\n[u]\nb = 2\n",
                result -> {
                  requireTable(result, "t").set("p", MutableTomlTable.createInline().set("x", 1));
                  MutableTomlArray points = MutableTomlArray.createInline();
                  points.add(MutableTomlTable.create().set("y", 2));
                  requireTable(result, "u").set("q", points);
                },
                "[t]\na = 1\np = { x = 1 }\n[u]\nb = 2\nq = [{ y = 2 }]\n"),
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
            edited(
                "an array edited in place keeps its line and the comment after it",
                "a = [1, 2]  # note\nb = 3\n",
                result -> requireArray(result, "a").add(3),
                "a = [1, 2, 3]  # note\nb = 3\n"),
            edited(
                "an inline table edited in place keeps its line",
                "t = { p = 1 }  # note\nu = 2\n",
                result -> requireTable(result, "t").set("q", 2),
                "t = { p = 1, q = 2 }  # note\nu = 2\n"),
            edited(
                "an element added to an array laid out over lines goes on a line of its own",
                "a = [\n  1,\n  2,\n]\nb = 3\n",
                result -> requireArray(result, "a").add(3),
                "a = [\n  1,\n  2,\n  3,\n]\nb = 3\n"),
            edited(
                "an element added to an array with no trailing comma follows the last one",
                "a = [\n  1,\n  2\n]\n",
                result -> requireArray(result, "a").add(3),
                "a = [\n  1,\n  2,\n  3\n]\n"),
            edited(
                "an element added to an array from a value read elsewhere keeps that value's literal",
                "a = [1]\nb = [\n  2,\n]\nc = 0x3\n",
                result -> {
                  requireArray(result, "a").add(result.entry("c").value());
                  requireArray(result, "b").add(Toml.parse("d = 'lit'\n").entry("d").value());
                },
                "a = [1, 0x3]\nb = [\n  2,\n  'lit',\n]\nc = 0x3\n"),
            edited(
                "an element inserted into an array goes where it was inserted",
                "a = [1, 3]  # note\n",
                result -> requireArray(result, "a").insertBefore(1, 2),
                "a = [1, 2, 3]  # note\n"),
            edited(
                "an element removed from an array takes the comment that ended its line",
                "a = [\n  1,  # one\n  2,  # two\n]\n",
                result -> requireArray(result, "a").remove(0),
                "a = [\n  2,  # two\n]\n"),
            edited(
                "an element removed from the middle of an array leaves the ones around it",
                "a = [1, 2, 3]  # note\n",
                result -> requireArray(result, "a").remove(1),
                "a = [1, 3]  # note\n"),
            edited(
                "the first element removed from an array takes the spacing that followed it",
                "a = [1, 2, 3]\n",
                result -> requireArray(result, "a").remove(0),
                "a = [2, 3]\n"),
            edited(
                "the first element removed from a spaced array keeps the spacing of the brackets",
                "a = [ 1, 2, 3 ]\n",
                result -> requireArray(result, "a").remove(0),
                "a = [ 2, 3 ]\n"),
            edited(
                "the last element removed from an array takes the comma that separated it",
                "a = [1, 2, 3]\n",
                result -> requireArray(result, "a").remove(2),
                "a = [1, 2]\n"),
            edited(
                "the last element removed from an array written with a trailing comma keeps one",
                "a = [1, 2, 3,]\n",
                result -> requireArray(result, "a").remove(2),
                "a = [1, 2,]\n"),
            edited(
                "the last element removed from an array laid out over lines leaves the closing bracket on its line",
                "a = [\n  1,\n  2,\n]\n",
                result -> requireArray(result, "a").remove(1),
                "a = [\n  1,\n]\n"),
            edited(
                "an element replaced in an array keeps its place and the comments around it",
                "a = [\n  1,  # one\n  2,  # two\n]\n",
                result -> requireArray(result, "a").set(1, 9),
                "a = [\n  1,  # one\n  9,  # two\n]\n"),
            edited(
                "an array left with no elements of the document is written anew",
                "a = [1]  # note\n",
                result -> requireArray(result, "a").remove(0),
                "a = []  # note\n"),
            edited(
                "the only element of an array takes the comment after it when it is removed",
                "a = [\n  1,  # one\n]\n",
                result -> requireArray(result, "a").remove(0),
                "a = []\n"),
            edited(
                "an array inside an array keeps the layout of the array it is in",
                "a = [[1], [2]]\n",
                result -> requireArray(result, "a").getArray(0).add(9),
                "a = [[1, 9], [2]]\n"),
            edited(
                "an element inserted before one with a run above it goes above that run",
                "a = [\n  # above\n  1,\n]\n",
                result -> requireArray(result, "a").insertBefore(0, 0),
                "a = [\n  0,\n  # above\n  1,\n]\n"),
            edited(
                "an element inserted after one with a run above it follows its line",
                "a = [\n  # above\n  1,\n]\n",
                result -> requireArray(result, "a").insertAfter(0, 2),
                "a = [\n  # above\n  1,\n  2,\n]\n"),
            edited(
                "a run set above an element of an array laid out over lines is indented like it",
                "a = [\n  1,\n  2,\n]\n",
                result -> requireArray(result, "a").setCommentAbove(1, "note"),
                "a = [\n  1,\n  # note\n  2,\n]\n"),
            edited(
                "a comment set after an element of an array written on one line lays it out over lines",
                "a = [1, 2]\n",
                result -> requireArray(result, "a").setCommentAfter(0, "note"),
                "a = [\n  1,  # note\n  2\n]\n"),
            edited(
                "a run of two lines set above an element is written above its line",
                "a = [\n  1,\n  2,\n]\n",
                result -> requireArray(result, "a").setCommentAbove(1, "one", "two"),
                "a = [\n  1,\n  # one\n  # two\n  2,\n]\n"),
            edited(
                "a comment set after the last element of an array written on one line lays it out over lines",
                "a = [1, 2]\n",
                result -> requireArray(result, "a").setCommentAfter(1, "note"),
                "a = [\n  1,\n  2  # note\n]\n"),
            edited(
                "a comment set on an element whose comma is written on the next line leaves the comma there",
                "a = [1 # c\n, 2]\n",
                result -> requireArray(result, "a").setCommentAfter(0, "note"),
                "a = [1 # note\n, 2]\n"),
            edited(
                "an element added to an array indented with tabs is indented with them",
                "a = [\n\t1,\n\t2\n]\n",
                result -> requireArray(result, "a").add(3),
                "a = [\n\t1,\n\t2,\n\t3\n]\n"),
            edited(
                "an element added after an unattached comment of an array is separated from it",
                "a = [\n  1,\n  # a run of its own\n]\n",
                result -> requireArray(result, "a").add(2),
                "a = [\n  1,\n  # a run of its own\n\n  2,\n]\n"),
            edited(
                "a comment removed from an element takes the spacing before it",
                "a = [\n  1,  # one\n  2,\n]\n",
                result -> requireArray(result, "a").removeCommentAfter(0),
                "a = [\n  1,\n  2,\n]\n"),
            edited(
                "an unattached comment added to an array is written under its last element",
                "a = [1, 2]\n",
                result -> requireArray(result, "a").addComment("note"),
                "a = [\n  1,\n  2\n  # note\n]\n"),
            edited(
                "an unattached comment inserted before an element is separated from it",
                "a = [1, 2]\n",
                result -> requireArray(result, "a").insertCommentBefore(1, "note"),
                "a = [\n  1,\n  # note\n\n  2\n]\n"),
            edited(
                "an element removed from an array whose comma is written on the next line",
                "a = [1 # c\n, 2]\n",
                result -> requireArray(result, "a").remove(1),
                "a = [1 # c\n]\n"),
            edited(
                "an element added to an array whose comma is written on the next line",
                "a = [1 # c\n, 2]\n",
                result -> requireArray(result, "a").add(3),
                "a = [1 # c\n, 2,\n  3\n]\n"),
            edited(
                "an entry removed from an inline table leaves the entries around it",
                "t = { p = 1, q = 2 }  # note\n",
                result -> result.remove("t.p"),
                "t = { q = 2 }  # note\n"),
            edited(
                "an entry added to an inline table laid out over lines goes on a line of its own",
                "t = {\n  x = 1,\n  y = 2,\n}\n",
                result -> requireTable(result, "t").set("z", 3),
                "t = {\n  x = 1,\n  y = 2,\n  z = 3,\n}\n"),
            edited(
                "an entry added to an inline table from a value read elsewhere keeps that value's literal",
                "t = { x = 1 }\nu = {\n  y = 2,\n}\nc = 0x3\n",
                result -> {
                  requireTable(result, "t").set("z", result.entry("c").value());
                  requireTable(result, "u").set("z", Toml.parse("d = 'lit'\n").entry("d").value());
                },
                "t = { x = 1, z = 0x3 }\nu = {\n  y = 2,\n  z = 'lit',\n}\nc = 0x3\n"),
            edited(
                "an entry added under a dotted key of an inline table stays with that key",
                "t = { a.b = 1, c = 2 }\n",
                result -> requireTable(result, "t.a").set("z", 9),
                "t = { a.b = 1, a.z = 9, c = 2 }\n"),
            edited(
                "a dotted key of an inline table left empty by a removal is written as a table",
                "t = { a.b = 1, c = 2 }\n",
                result -> result.remove("t.a.b"),
                "t = { a = {}, c = 2 }\n"),
            edited(
                "a comment set on an entry an inline table wrote as a dotted key gives it a value of its own",
                "t = { a.b = 1, c = 2 }\n",
                result -> requireTable(result, "t").setCommentAfter("a", "note"),
                "t = {\n  a = { b = 1 },  # note\n  c = 2\n}\n"),
            edited(
                "an inline table left with no entries of the document is written anew",
                "t = { p = 1 }  # note\n",
                result -> result.remove("t.p"),
                "t = {}  # note\n"),
            edited(
                "an entry of an inline table replaced by a table is written inline",
                "t = { p = 1, q = 2 }\n",
                result -> requireTable(result, "t").set("p", MutableTomlTable.create().set("k", 1)),
                "t = { p = { k = 1 }, q = 2 }\n"),
            edited(
                "a value replaced in an inline table laid out over lines is written over lines when too long",
                "t = {\n  b = 1,\n  c = 2,\n}\n",
                result -> requireTable(result, "t").set("c", longArray()),
                "t = {\n  b = 1,\n  c = [\n    1000000000001,\n    1000000000002,\n    1000000000003,\n"
                    + "    1000000000004,\n    1000000000005,\n    1000000000006,\n  ],\n}\n"),
            edited(
                "a value replaced in an inline table on one line stays on the line however long",
                "t = { b = 1, c = 2 }\n",
                result -> requireTable(result, "t").set("c", longArray()),
                "t = { b = 1, c = [1000000000001, 1000000000002, 1000000000003, 1000000000004, 1000000000005, "
                    + "1000000000006] }\n"),
            edited(
                "a value replaced in an inline table on one line keeps the literals it was read with",
                "t = { b = 1, c = 2 }\nx = [0x10, 'lit']\n",
                result -> requireTable(result, "t").set("c", result.get("x")),
                "t = { b = 1, c = [0x10, 'lit'] }\nx = [0x10, 'lit']\n"),
            edited(
                "a value replaced in an inline table on one line stays on the line for TOML 1.0.0",
                "t = { b = 1, c = 2 }\n",
                result -> requireTable(result, "t").set("c", longArray()),
                LF.withVersion(TomlVersion.V1_0_0),
                "t = { b = 1, c = [1000000000001, 1000000000002, 1000000000003, 1000000000004, 1000000000005, "
                    + "1000000000006] }\n"),
            edited(
                "an inline table inside an array is edited in place",
                "a = [{ p = 1 }, 2]\n",
                result -> requireArray(result, "a").getTable(0).set("q", 2),
                "a = [{ p = 1, q = 2 }, 2]\n"),
            edited(
                "the second table of an array of inline tables is edited in place",
                "a = [{ x = 1 }, { y = 2 }]\n",
                result -> requireArray(result, "a").getTable(1).set("z", 3),
                "a = [{ x = 1 }, { y = 2, z = 3 }]\n"),
            edited(
                "an array inside an inline table is edited in place",
                "t = { a = [1, 2] }\n",
                result -> requireArray(result, "t.a").add(3),
                "t = { a = [1, 2, 3] }\n"),
            edited(
                "an array inside an inline table inside an array is edited in place",
                "x = [{ a = [1] }]\n",
                result -> requireArray(result, "x").getTable(0).getArray("a").add(2),
                "x = [{ a = [1, 2] }]\n"),
            edited(
                "an inline table on one line stays on it when a multi-line string in it spans lines",
                "t = { a = \"\"\"\nx\n\"\"\", b = 2 }\n",
                result -> requireTable(result, "t").set("c", 3),
                "t = { a = \"\"\"\nx\n\"\"\", b = 2, c = 3 }\n"),
            edited(
                "an inline table on one line stays on it when an array in it spans lines",
                "t = { a = [\n  1,\n  2,\n], b = 2 }\n",
                result -> requireTable(result, "t").set("c", 3),
                "t = { a = [\n  1,\n  2,\n], b = 2, c = 3 }\n"),
            edited(
                "an inline table on one line stays on it when the entry after a multi-line string goes",
                "t = { a = \"\"\"\nx\n\"\"\", b = 2 }\n",
                result -> requireTable(result, "t").remove("b"),
                "t = { a = \"\"\"\nx\n\"\"\" }\n"),
            edited(
                "an array on one line stays on it when its multi-line strings span lines",
                "l = [ \"\"\"\na\n\"\"\", \"\"\"\nb\n\"\"\" ]\n",
                result -> requireArray(result, "l").add("c"),
                "l = [ \"\"\"\na\n\"\"\", \"\"\"\nb\n\"\"\", \"c\" ]\n"),
            edited(
                "an inline table written over lines keeps its lines when a multi-line string is in it",
                "t = {\n  a = \"\"\"\nx\n\"\"\",\n  b = 2,\n}\n",
                result -> requireTable(result, "t").set("c", 3),
                "t = {\n  a = \"\"\"\nx\n\"\"\",\n  b = 2,\n  c = 3,\n}\n"),
            edited(
                "an array of a copied table is edited in place, keeping the literals it was read with",
                "[a.b]\nx = [0x10, 2]  # kept\n",
                result -> requireArray(result.set("z", requireTable(result, "a.b")), "z.x").add(3),
                "[a.b]\nx = [0x10, 2]  # kept\n\n[z]\nx = [0x10, 2, 3]  # kept\n"),
            edited(
                "an array of a copied table keeps the literals it was read with",
                "[a.b]\nx = [0x10, 2]  # kept\n",
                result -> result.set("z", requireTable(result, "a.b")),
                "[a.b]\nx = [0x10, 2]  # kept\n\n[z]\nx = [0x10, 2]  # kept\n"),
            edited(
                "an array of tables the editing API builds on a line stays on it",
                "a = []\n",
                result -> requireArray(result, "a").add(MutableTomlTable.create().set("k", 1)),
                "a = [{ k = 1 }]\n"),
            edited("a document with no newline at its end", "a = 1", result -> result.set("b", 2), "a = 1\nb = 2\n"),
            edited(
                "a comment left after the last line of its table when the line below it goes is written directly under"
                    + " the line above",
                "[t]\nx = 1\n\n# note\n\ny = 2\n\n[u]\n",
                result -> requireTable(result, "t").remove("y"),
                "[t]\nx = 1\n# note\n\n[u]\n"),
            edited(
                "two comments added to the root after its last section stay separate runs",
                "[t]\nx = 1\n",
                result -> result.addComment("one").addComment("two"),
                "[t]\nx = 1\n\n# one\n\n# two\n"),
            edited("an empty document", "", result -> result.set("a", 1), "a = 1\n"),
            edited(
                "a new table in an empty document",
                "",
                result -> result.getOrCreateTable("t").set("k", 1),
                "[t]\nk = 1\n"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("joinedRuns")
  void joinsTheRunsAfterTheLastLineOfATable(
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

    // The two runs come back as one run of the table they were written in, and nothing of them in the root
    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> written + "\n" + joinErrors(reparsed));
    assertTrue(Toml.equals(result, reparsed), () -> written);
    assertTrue(unattachedComments(reparsed).isEmpty(), () -> written);
    List<TomlComment> comments = unattachedComments(requireTable(reparsed, "t"));
    assertEquals(1, comments.size(), () -> written);
    assertEquals(List.of("one", "", "two"), comments.get(0).lines());
  }

  private static List<TomlComment> unattachedComments(TomlTable table) {
    return table.elements().stream().filter(TomlComment.class::isInstance).map(TomlComment.class::cast).toList();
  }

  static Stream<Arguments> joinedRuns() {
    return Stream
        .of(
            joined(
                "a comment added after the last comment of a table",
                "[t]\nx = 1\n# one\n\n[u]\ny = 2\n",
                result -> requireTable(result, "t").addComment("two"),
                LF,
                "[t]\nx = 1\n# one\n#\n# two\n\n[u]\ny = 2\n"),
            joined(
                "two comments added after the last line of a table",
                "[t]\nx = 1\n",
                result -> requireTable(result, "t").addComment("one").addComment("two"),
                LF,
                "[t]\nx = 1\n# one\n#\n# two\n"),
            joined(
                "two comments added under the header of an empty table",
                "[t]\n\n[u]\n",
                result -> requireTable(result, "t").addComment("one").addComment("two"),
                LF,
                "[t]\n# one\n#\n# two\n\n[u]\n"),
            joined(
                "the comments left after the last line of a table when the line between them goes",
                "[t]\n# one\n\nx = 1\n# two\n\n[u]\n",
                result -> requireTable(result, "t").remove("x"),
                LF,
                "[t]\n# one\n#\n# two\n\n[u]\n"),
            joined(
                "a comment added after the last comment of a table is indented like it",
                "[t]\n  x = 1\n  # one\n",
                result -> requireTable(result, "t").addComment("two"),
                LF,
                "[t]\n  x = 1\n  # one\n  #\n  # two\n"),
            joined(
                "a comment added after the last comment of a table when only the notation is kept",
                "[t]\n  x = 1\n  # one\n\n[u]\n",
                result -> requireTable(result, "t").addComment("two"),
                LF.keep(TomlWriteOptions.Keep.NOTATION),
                "[t]\nx = 1\n# one\n#\n# two\n\n[u]\n"),
            joined(
                "the comments left after the last line of a table when the line between them goes, when only the"
                    + " notation is kept",
                "[t]\n# one\n\nx = 1\n# two\n\n[u]\n",
                result -> requireTable(result, "t").remove("x"),
                LF.keep(TomlWriteOptions.Keep.NOTATION),
                "[t]\n# one\n#\n# two\n\n[u]\n"));
  }

  private static Arguments edited(String description, String input, Consumer<TomlParseResult> edit, String expected) {
    return edited(description, input, edit, LF, expected);
  }

  private static Arguments edited(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlWriteOptions options,
      String expected) {
    return Arguments.of(description, input, edit, options, expected);
  }

  /** An array too long for the default maximum line width when written on one line. */
  private static MutableTomlArray longArray() {
    return MutableTomlArray
        .of(1000000000001L, 1000000000002L, 1000000000003L, 1000000000004L, 1000000000005L, 1000000000006L);
  }

  private static Arguments joined(
      String description,
      String input,
      Consumer<TomlParseResult> edit,
      TomlWriteOptions options,
      String expected) {
    return Arguments.of(description, input, edit, options, expected);
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
