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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks what a parse records about where each line of a document was written.
 *
 * <p>
 * The oracle is the document itself: the text every span covers, written out in order and followed by the blank lines
 * the document ends with, is the document, with the lines the parser rejected left out. That holds a span to both ends
 * at once - a span that reaches too far overlaps its neighbour, one that reaches too little drops text - so it checks
 * the whole set rather than one offset at a time.
 */
class SourceSpanTest {

  // ---- Source ----

  @Test
  void textIsReadByCodePointOffset() {
    Source source = new Source("a😀b\n");
    assertEquals(4, source.length());
    assertEquals("a", source.text(0, 0));
    assertEquals("😀", source.text(1, 1));
    assertEquals("b", source.text(2, 2));
    assertEquals("😀b", source.text(1, 2));
    assertEquals("a😀b\n", source.text(0, 3));
    assertEquals("", source.text(2, 1));
  }

  @Test
  void lengthAndTextOfAnEmptySource() {
    Source source = new Source("");
    assertEquals(0, source.length());
    assertEquals("", source.text(0, -1));
  }

  @Test
  void lineSeparatorIsReadFromTheFirstNewline() {
    assertEquals("\n", new Source("a = 1\nb = 2\r\n").lineSeparator());
    assertEquals("\r\n", new Source("a = 1\r\nb = 2\n").lineSeparator());
    assertNull(new Source("a = 1").lineSeparator());
  }

  @Test
  void leadingStartWalksBackOverIndentation() {
    // "a = 1\n  b = 2\n": the indentation of the second line starts at 6.
    assertEquals(6, new Source("a = 1\n  b = 2\n").leadingStart(8));
    assertEquals(6, new Source("a = 1\n\t\tb = 2\n").leadingStart(8));
  }

  @Test
  void leadingStartWalksBackOverBlankLines() {
    // "a = 1\n\n\n  b\n": the two blank lines and the indentation below them start at 6.
    assertEquals(6, new Source("a = 1\n\n\n  b\n").leadingStart(10));
    // A blank line of spaces is blank.
    assertEquals(6, new Source("a = 1\n   \nb\n").leadingStart(10));
  }

  @Test
  void leadingStartStopsAtALineThatHoldsAnything() {
    assertEquals(6, new Source("a = 1\nb = 2\n").leadingStart(6));
    // A carriage return on its own is content, not a newline.
    assertEquals(4, new Source("a\rb\n\nc\n").leadingStart(5));
  }

  @Test
  void leadingStartReadsCarriageReturnLineEndings() {
    // "a\r\n\r\n  b": the blank line and the indentation below it start at 3.
    assertEquals(3, new Source("a\r\n\r\n  b").leadingStart(7));
  }

  @Test
  void leadingStartAtTheStartOfTheText() {
    assertEquals(0, new Source("a = 1\n").leadingStart(0));
    assertEquals(0, new Source("  a = 1\n").leadingStart(2));
    assertEquals(0, new Source("\n\n  a = 1\n").leadingStart(4));
  }

  @Test
  void leadingStartCountsCodePoints() {
    // "😀 = 1\n  b = 2\n": the emoji is one code point, so the indentation starts at 6.
    assertEquals(6, new Source("😀 = 1\n  b = 2\n").leadingStart(8));
  }

  // ---- Reassembly of a document from its spans ----

  static Stream<String> spanDocuments() {
    return Stream
        .of(
            "",
            "\n",
            "\n\n\n",
            "   \n\t\n",
            "# just a comment\n",
            "# no final newline",
            "a = 1\n",
            "a = 1",
            "a = 1 # trailing",
            "a = 1\nb = 2\nc = 3\n",
            "a.b.c = 1\nd = 2\n\"quoted key\" = 3\n'literal key' = 4\n\"x\".'y' = 5\n",
            "s = \"basic\"\nl = 'literal'\ni = 42\nh = 0xdeadBEEF\no = 0o755\nb = 0b1011\n",
            "f = 3.14\ng = -2e-3\nn = nan\ni = inf\nt = true\nu = false\n",
            "odt = 1979-05-27T07:32:00Z\nldt = 1979-05-27T07:32:00\nld = 1979-05-27\nlt = 07:32:00\n",
            "a = [1, 2, 3]\nb = []\nc = [ [1], { d = 2 } ]\n",
            "t = { a = 1, b = 'two' }\nu = {}\n",
            "ml = \"\"\"\nline one\nline two\n\"\"\"\nafter = 1\n",
            "ml = '''\nliteral\nlines\n'''\nafter = 1\n",
            "[a]\nb = 1\n\n[a.c]\nd = 2\n\n[[e]]\nf = 3\n\n[[e]]\ng = 4\n",
            "[[a.b]]\nc = 1\n",
            "[\"quoted.header\"]\nx = 1\n",
            "# above the header\n[a] # after the header\n# above the line\nb = 1 # after the line\n",
            "# above\n# a run\n[[a]] # after\nb = 1\n",
            "a = 1\n# glued to the line above\n\n# separated from it\nb = 2\n",
            "a = 1\n\n# trailing run\n# of two lines\n",
            "a = 1\n# trailing run\n",
            "\n\na = 1\n\n\nb = 2\n\n\n",
            "   \n  a = 1   \n\t\tb = 2\t\n   \n",
            "a = 1\r\nb = 2\r\n",
            "# comment\r\n[a]\r\nb = 1 # after\r\n\r\nc = 2\r\n",
            "a = 1\r\n\r\n# separated\r\n\r\nb = 2\r\n",
            "# a comment then nothing else\n\n\n",
            "\"😀\" = 1\nb = \"😀 in a value\"\n# 😀 in a comment\n\nc = 2\n",
            "a = \"😀\"\n[\"😀\"]\n  b = 2 # 😀\n\n# 😀\n",
            "a = '''\n😀\n'''\nb = 2\n",
            "[a]\n# only a comment in this section\n\n[b]\n",
            "[a]\n\n[b]\n\n[c]\n");
  }

  static Stream<String> containerDocuments() {
    return Stream
        .of(
            "a = [1,2,3]\n",
            "a = [ 1, 2, 3 ]\n",
            "a = [1, 2, 3,]\n",
            "a = [ 1 , 2 , 3 , ]\n",
            "a = []\n",
            "a = [ ]\n",
            "a = [\n]\n",
            "a = {}\n",
            "a = { }\n",
            "a = { b = 1 }\n",
            "a = {b=1,c=2}\n",
            "a = { b.c = 1, d = 2, b.e = 3 }\n",
            "a = [\n  1,\n  2,\n]\n",
            "a = [ # on the bracket line\n  # above the first\n  1, # after the first\n  2 # after the last\n"
                + "  # before the closing bracket\n]\n",
            "a = [\n  1,\n\n  # a run of its own\n\n  2,\n]\n",
            "a = [\n  # only a comment\n]\n",
            "a = [1 # after the value\n, 2]\n",
            "a = [1\n, 2]\n",
            "a = [ [1, 2], [ ], [[3]] ]\n",
            "a = [ { b = 1 }, { c = [2] } ]\n",
            "a = {\n  b = 1, # after\n  # above\n  c = 2,\n}\n",
            "a = [\r\n  1,\r\n  2\r\n]\r\n",
            "a = [\n\t1,\n\t2\n]\n",
            "a = [\"😀\", { \"😀\" = \"😀\" }] # 😀\n",
            "a = [ 'x' ]\nb = [ '''\nml\n''' ]\n");
  }

  static Stream<String> allDocuments() {
    return Stream.concat(spanDocuments(), containerDocuments());
  }

  @ParameterizedTest
  @MethodSource("allDocuments")
  void aDocumentIsTheTextOfItsSpansInOrder(String document) {
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));
    assertEquals(document, reassemble(table));
  }

  @ParameterizedTest
  @MethodSource("allDocuments")
  void aContainerIsTheTextOfItsElementsInOrder(String document) {
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));
    assertContainersReassemble(table);
  }

  static Stream<Arguments> skippedLineDocuments() {
    return Stream
        .of(
            // A line the parser could not read is not copied, and nor is the run above it.
            Arguments.of("a = 1\nb = !!\nc = 2\n", "a = 1\nc = 2\n"),
            Arguments.of("a = 1\n# above the bad line\nb = !!\nc = 2\n", "a = 1\nc = 2\n"),
            // A header whose key error recovery changed does not open a section, so its line is not copied.
            Arguments.of("[a]\n[b c]\nd = 1\n", "[a]\nd = 1\n"),
            // Input the parser skipped after a header is not copied, but the header's line is.
            Arguments.of("[a] junk\nb = 1\n", "[a]\nb = 1\n"),
            Arguments.of("[a] junk # after\nb = 1\n", "[a]\nb = 1\n"),
            // "[a]]" ends with the "]]" of an array-of-tables header, which is not the bracket "[a" opened.
            Arguments.of("[a]]\nb = 1\n", "b = 1\n"),
            // A rejected line at the end of a document that ends without a newline.
            Arguments.of("a = 1\nbad!!", "a = 1\n"),
            // The blank lines above a rejected line go with it; the ones below it start the next line.
            Arguments.of("a = 1\n\nb = !!\n\nc = 2\n", "a = 1\n\nc = 2\n"));
  }

  @ParameterizedTest
  @MethodSource("skippedLineDocuments")
  void aRejectedLineIsNotCopied(String document, String expected) {
    ParsedTomlTable table = parse(document);
    assertTrue(table.hasErrors(), "the document was expected to be rejected in part");
    assertEquals(expected, reassemble(table));
  }

  // ---- The offsets of one document ----

  @Test
  void aLineRecordsItsKeyValueCommentsAndNewline() {
    String document = "# above\n# a run\na.b.c = 1 # after\n";
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));

    SourceSpan span = spanOf(table, "a", "b", "c");
    assertEquals(SourceSpan.Kind.LINE, span.kind);
    assertEquals(0, span.start);
    assertEquals("# above\n# a run\n", span.source.text(span.aboveStart, span.aboveStop));
    assertEquals("a.b.c", span.source.text(span.keyStart, span.keyStop));
    assertEquals(3, span.keyParts);
    assertEquals("1", span.source.text(span.valueStart, span.valueStart));
    assertEquals("# after", span.source.text(span.afterStart, span.afterStop));
    assertEquals(span.valueStart + 1, span.tailStart);
    assertEquals("\n", span.source.text(span.newlineStart, span.stop));
    assertEquals(document, span.source.text(0, span.source.length() - 1));
  }

  @Test
  void aLineWithoutACommentOrARunRecordsNeither() {
    ParsedTomlTable table = parse("a = 1\n");
    SourceSpan span = spanOf(table, "a");
    assertEquals(-1, span.aboveStart);
    assertEquals(-1, span.aboveStop);
    assertEquals(-1, span.afterStart);
    assertEquals(-1, span.afterStop);
    assertEquals(1, span.keyParts);
  }

  @Test
  void theLastLineOfADocumentWithNoFinalNewlineStopsBeforeIt() {
    ParsedTomlTable table = parse("a = 1");
    SourceSpan span = spanOf(table, "a");
    assertEquals(5, span.newlineStart);
    assertEquals(span.newlineStart - 1, span.stop);
    assertEquals(-1, table.trailerStart());
  }

  @Test
  void aHeaderRecordsItsWholeLine() {
    String document = "# above\n[a.b] # after\nc = 1\n";
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));

    SourceSpan span = headerSpanOf(table, "a", "b");
    assertEquals(SourceSpan.Kind.HEADER, span.kind);
    assertEquals(0, span.start);
    assertEquals("[a.b]", span.source.text(span.keyStart, span.keyStop));
    assertEquals(2, span.keyParts);
    assertEquals(-1, span.valueStart);
    assertEquals("# after", span.source.text(span.afterStart, span.afterStop));
    assertEquals(span.keyStop + 1, span.tailStart);
    assertEquals(document, span.source.text(0, span.source.length() - 1));
    assertNull(headerSpanOf(table, "a"), "no header names the table a dotted header walked through");
  }

  @Test
  void eachElementOfAnArrayOfTablesRecordsItsOwnHeader() {
    String document = "[[a]]\nb = 1\n[[a]]\nb = 2\n";
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));

    ListTomlArray array = (ListTomlArray) table.getArray("a");
    assertNotNull(array);
    assertEquals(0, headerSpanOf((LinkedTomlTable) array.get(0)).keyStart);
    assertEquals(12, headerSpanOf((LinkedTomlTable) array.get(1)).keyStart);
  }

  @Test
  void aHeaderTheParserSkippedInputAfterCopiesNothingOfThatInput() {
    ParsedTomlTable table = parse("[a] junk\nb = 1\n");
    SourceSpan span = headerSpanOf(table, "a");
    assertEquals(span.newlineStart, span.tailStart);
    assertEquals(-1, span.afterStart);
  }

  @Test
  void anUnattachedCommentRecordsItsOwnLines() {
    String document = "a = 1\n\n# one\n# two\n\nb = 2\n";
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));

    SourceSpan span = commentSpans(table).get(0);
    assertEquals(SourceSpan.Kind.COMMENT, span.kind);
    assertEquals(6, span.start);
    assertEquals("# one\n# two\n", span.source.text(span.aboveStart, span.aboveStop));
    assertEquals(span.aboveStop, span.stop);
    assertEquals(-1, span.keyStart);
    assertEquals(0, span.keyParts);
    assertEquals(document, span.source.text(0, span.source.length() - 1));
  }

  @Test
  void theBlankLinesADocumentEndsWithAreItsTrailer() {
    ParsedTomlTable table = parse("a = 1\n\n\n");
    assertEquals(6, table.trailerStart());
    assertEquals("\n\n", table.source().text(table.trailerStart(), table.source().length() - 1));

    assertEquals(-1, parse("a = 1\n").trailerStart());
    assertEquals(0, parse("\n\n").trailerStart());
    assertEquals(-1, parse("").trailerStart());
  }

  @Test
  void aScalarRecordsTheLiteralItWasWrittenAs() {
    String document = "a = 0x1F\nb = \"two\"\n";
    ParsedTomlTable table = parse(document);
    ValueSpan a = scalarSpanOf(table, "a");
    assertEquals("0x1F", a.source.text(a.start, a.stop));
    assertEquals(-1, a.trailerStart);
    ValueSpan b = scalarSpanOf(table, "b");
    assertEquals("\"two\"", b.source.text(b.start, b.stop));
    assertEquals(document, b.source.text(0, b.source.length() - 1));
  }

  // ---- The offsets of the elements of one container ----

  @Test
  void anElementRecordsTheCommaThatFollowsIt() {
    SourceSpan first = elementSpanOf(parse("a = [1, 2]\n"), 0);
    assertEquals(SourceSpan.Kind.ELEMENT, first.kind);
    assertEquals(",", first.source.text(first.commaOffset, first.commaOffset));
    assertEquals(6, first.commaOffset);
    assertEquals(-1, first.keyStart);
    assertEquals(0, first.keyParts);

    assertEquals(7, elementSpanOf(parse("a = [1 ,2]\n"), 0).commaOffset);
    assertEquals(9, elementSpanOf(parse("a = [1, 2,]\n"), 1).commaOffset);
    assertEquals(-1, elementSpanOf(parse("a = [1, 2]\n"), 1).commaOffset);
  }

  @Test
  void anElementRecordsTheCommentAfterItWhicheverSideOfTheCommaItIsOn() {
    SourceSpan afterComma = elementSpanOf(parse("a = [1, # after\n  2]\n"), 0);
    assertEquals("# after", afterComma.source.text(afterComma.afterStart, afterComma.afterStop));
    assertTrue(afterComma.commaOffset < afterComma.afterStart);
    assertEquals("1, # after\n", afterComma.source.text(afterComma.start, afterComma.stop));

    // A comma written on the next line separates the elements but does not end the first one's line.
    SourceSpan beforeComma = elementSpanOf(parse("a = [1 # after\n, 2]\n"), 0);
    assertEquals("# after", beforeComma.source.text(beforeComma.afterStart, beforeComma.afterStop));
    assertEquals(-1, beforeComma.commaOffset);
    assertEquals("1 # after\n", beforeComma.source.text(beforeComma.start, beforeComma.stop));
  }

  @Test
  void anElementRecordsTheRunAboveIt() {
    String document = "a = [\n  # above\n  1,\n]\n";
    SourceSpan span = elementSpanOf(parse(document), 0);
    assertEquals("# above\n", span.source.text(span.aboveStart, span.aboveStop));
    assertEquals(5, span.start);
    assertEquals("\n  # above\n  1,\n", span.source.text(span.start, span.stop));
  }

  @Test
  void anEntryOfAnInlineTableRecordsItsKey() {
    ParsedTomlTable table = parse("a = { b.c = 1 }\n");
    Entry.KeyValue entry = table.entry(List.of("a", "b", "c"));
    assertNotNull(entry);
    SourceSpan span = entry.span;
    assertNotNull(span);
    assertEquals(SourceSpan.Kind.ELEMENT, span.kind);
    assertEquals("b.c", span.source.text(span.keyStart, span.keyStop));
    assertEquals(2, span.keyParts);
    assertEquals("1", span.source.text(span.valueStart, span.valueStart));
    assertEquals(span.valueStart + 1, span.tailStart);
  }

  @Test
  void aContainerRecordsItsBracketsAndTheWhitespaceBeforeTheClosingOne() {
    ValueSpan brackets = bracketSpanOf(parse("a = [1, 2 ]\n"), "a");
    assertEquals("[", brackets.source.text(brackets.start, brackets.start));
    assertEquals("]", brackets.source.text(brackets.stop, brackets.stop));
    assertEquals(" ", brackets.source.text(brackets.trailerStart, brackets.stop - 1));

    ValueSpan tight = bracketSpanOf(parse("a = [1, 2]\n"), "a");
    assertEquals(tight.stop, tight.trailerStart);

    ValueSpan empty = bracketSpanOf(parse("a = []\n"), "a");
    assertEquals(empty.start + 1, empty.trailerStart);
    assertEquals(empty.stop, empty.trailerStart);

    ValueSpan braces = bracketSpanOf(parse("a = { b = 1 }\n"), "a");
    assertEquals("{", braces.source.text(braces.start, braces.start));
    assertEquals("}", braces.source.text(braces.stop, braces.stop));
    assertEquals(" ", braces.source.text(braces.trailerStart, braces.stop - 1));
  }

  @Test
  void aCommentInsideBracketsRecordsItsOwnLines() {
    String document = "a = [\n  1,\n  # a run\n  # of two lines\n]\n";
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));

    ListTomlArray array = (ListTomlArray) table.getArray("a");
    assertNotNull(array);
    SourceSpan span = commentSpans(array).get(0);
    assertEquals(SourceSpan.Kind.COMMENT, span.kind);
    assertEquals("# a run\n# of two lines\n", span.source.text(span.aboveStart, span.aboveStop).replace("  ", ""));
    assertEquals(span.aboveStop, span.stop);
    assertEquals("  # a run\n  # of two lines\n", span.source.text(span.start, span.stop));
  }

  // ---- Parsing with no source kept ----

  @Test
  void aParseWithoutSourceRecordsNothing() {
    ParsedTomlTable table =
        parse("# a comment\n[a] # after\nb = 1\nc = [2]\n", TomlParseOptions.defaults().withoutSource());
    assertNull(table.source());
    assertEquals(-1, table.trailerStart());
    assertNull(spanOf(table, "a", "b"));
    assertNull(headerSpanOf(table, "a"));
    assertNull(scalarSpanOf(table, "a", "b"));
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment comment) {
        assertNull(comment.span());
      }
    }
    ListTomlArray array = (ListTomlArray) table.getArray(List.of("a", "c"));
    assertNotNull(array);
    assertNull(array.bracketSpan);
    assertNull(array.entry(0).span);
  }

  // ---- Copies ----

  @Test
  void aCopySharesTheSpansOfTheEntriesItCopies() {
    ParsedTomlTable table = parse("# a comment\n[a]\nb = 1\n");
    Entry.KeyValue original = table.entry(List.of("a", "b"));
    assertNotNull(original);

    MutableTomlTable copy = MutableTomlTable.copyOf(table);
    Entry.KeyValue copied = ((LinkedTomlTable) copy).entry(List.of("a", "b"));
    assertNotNull(copied);
    assertSame(original.span, copied.span);
    assertSame(((Value.Scalar) original.value).span, ((Value.Scalar) copied.value).span);
  }

  @Test
  void aCopyKeepsTheBracketsAndElementsOfAnArrayItCopies() {
    ParsedTomlTable table = parse("a = [1, { b = 2 } ]\n");
    ListTomlArray original = (ListTomlArray) table.getArray("a");
    assertNotNull(original);

    ListTomlArray copied = (ListTomlArray) MutableTomlTable.copyOf(table).getArray("a");
    assertNotNull(copied);
    assertSame(original.bracketSpan, copied.bracketSpan);
    assertSame(original.entry(0).span, copied.entry(0).span);
    assertSame(((Value.Scalar) original.entry(0).value).span, ((Value.Scalar) copied.entry(0).value).span);
    assertSame(
        ((ElementContainer<?>) original.entry(1).value).bracketSpan,
        ((ElementContainer<?>) copied.entry(1).value).bracketSpan);
  }

  @Test
  void aCopiedTableHasNoHeaderOfItsOwn() {
    ParsedTomlTable table = parse("[a]\nb = 1\n");
    assertNotNull(headerSpanOf(table, "a"));

    MutableTomlTable copy = MutableTomlTable.copyOf(table);
    LinkedTomlTable copied = (LinkedTomlTable) copy.getTable("a");
    assertNotNull(copied);
    assertNull(copied.headerSpan);
  }

  @Test
  void aCopiedCommentLosesWhereItWasWritten() {
    ParsedTomlTable table = parse("a = 1\n\n# a comment\n\nb = 2\n");
    TomlComment original = (TomlComment) table.elements().get(1);
    assertNotNull(original.span());
    assertNull(original.withoutPosition().span());

    MutableTomlTable copy = MutableTomlTable.copyOf(table);
    TomlComment copied = (TomlComment) copy.elements().get(1);
    assertNull(copied.span());
  }

  // ---- Reassembly ----

  /**
   * Write out the text every span of a document covers, in order, followed by the blank lines the document ends with.
   *
   * @param table The parsed document.
   * @return The document as its spans record it, which is the document itself unless the parser rejected a line.
   */
  static String reassemble(ParsedTomlTable table) {
    List<Chunk> chunks = new ArrayList<>();
    collect(table, chunks);
    chunks.sort(Comparator.comparingInt(chunk -> chunk.start));

    Source source = table.source();
    assertNotNull(source);
    StringBuilder written = new StringBuilder();
    for (Chunk chunk : chunks) {
      written.append(chunk.text);
    }
    if (table.trailerStart() >= 0) {
      written.append(source.text(table.trailerStart(), source.length() - 1));
    }
    return written.toString();
  }

  /** The text one line, header or unattached comment of a section covers, with the offset it starts at. */
  private static final class Chunk {
    final int start;
    final String text;

    Chunk(SourceSpan span, int valueStop) {
      this.start = span.start;
      if (span.kind == SourceSpan.Kind.COMMENT) {
        this.text = span.source.text(span.start, span.stop);
      } else {
        this.text = span.source.text(span.start, valueStop) + span.source.text(span.tailStart, span.stop);
      }
    }
  }

  private static void collect(ElementContainer<?> container, List<Chunk> chunks) {
    for (TomlElement element : container.elements()) {
      if (element instanceof TomlComment comment) {
        SourceSpan span = comment.span();
        if (span != null) {
          chunks.add(new Chunk(span, -1));
        }
        continue;
      }
      Entry entry = (Entry) element;
      Value value = entry.value;
      if (entry.span != null && entry.span.kind == SourceSpan.Kind.LINE) {
        int valueStop = valueStop(value);
        assertEquals(entry.span.tailStart - 1, valueStop, "a line's value ends where its tail begins");
        chunks.add(new Chunk(entry.span, valueStop));
      }
      if (value instanceof LinkedTomlTable table && table.headerSpan != null) {
        chunks.add(new Chunk(table.headerSpan, table.headerSpan.keyStop));
      }
      // What is written between brackets is part of its own line, not a line of a section: only a table a header
      // opened, an array of such tables, and a table a dotted key opened hold lines of their own.
      if (value instanceof ElementContainer<?> nested && nested.bracketSpan == null) {
        collect(nested, chunks);
      }
    }
  }

  /**
   * Check every array and inline table written between brackets in a document, and everything nested in one: the text
   * between its brackets is the opening bracket, then each of its elements and comments in the order they were written,
   * then the whitespace before the closing bracket, then the closing bracket.
   *
   * @param container The container to check, and to walk for the ones written in it.
   */
  static void assertContainersReassemble(ElementContainer<?> container) {
    ValueSpan brackets = container.bracketSpan;
    if (brackets != null) {
      Source source = brackets.source;
      StringBuilder written = new StringBuilder(source.text(brackets.start, brackets.start));
      for (SourceSpan span : elementSpans(container)) {
        written.append(source.text(span.start, span.stop));
      }
      written.append(source.text(brackets.trailerStart, brackets.stop - 1));
      written.append(source.text(brackets.stop, brackets.stop));
      assertEquals(source.text(brackets.start, brackets.stop), written.toString());
    }
    for (TomlElement element : container.elements()) {
      if (element instanceof Entry entry && entry.value instanceof ElementContainer<?> nested) {
        assertContainersReassemble(nested);
      }
    }
  }

  /** The spans of what an array or inline table holds, in the order it was written. */
  private static List<SourceSpan> elementSpans(ElementContainer<?> container) {
    List<SourceSpan> spans = new ArrayList<>();
    addElementSpans(container, spans);
    spans.sort(Comparator.comparingInt(span -> span.start));
    return spans;
  }

  private static void addElementSpans(ElementContainer<?> container, List<SourceSpan> spans) {
    for (TomlElement element : container.elements()) {
      if (element instanceof TomlComment comment) {
        if (comment.span() != null) {
          spans.add(comment.span());
        }
        continue;
      }
      Entry entry = (Entry) element;
      if (entry.span != null) {
        spans.add(entry.span);
      } else if (entry.value instanceof ElementContainer<?> dotted && dotted.bracketSpan == null) {
        // A dotted key in an inline table opens a table of its own, and the entry written between the braces is the
        // one the key ends at.
        addElementSpans(dotted, spans);
      }
    }
  }

  /** The last offset of a value as written: the literal of a scalar, or the closing bracket of a container. */
  private static int valueStop(Value value) {
    if (value instanceof Value.Scalar scalar) {
      assertNotNull(scalar.span);
      return scalar.span.stop;
    }
    ValueSpan brackets = ((ElementContainer<?>) value).bracketSpan;
    assertNotNull(brackets);
    return brackets.stop;
  }

  // ---- Reaching into the model ----

  private static ParsedTomlTable parse(String document) {
    return parse(document, TomlParseOptions.defaults());
  }

  private static ParsedTomlTable parse(String document, TomlParseOptions options) {
    return Parser.parseTable(CharStreams.fromString(document), options, new AccumulatingErrorListener());
  }

  private static SourceSpan spanOf(ParsedTomlTable table, String... path) {
    Entry.KeyValue entry = table.entry(List.of(path));
    assertNotNull(entry);
    return entry.span;
  }

  private static ValueSpan scalarSpanOf(ParsedTomlTable table, String... path) {
    Entry.KeyValue entry = table.entry(List.of(path));
    assertNotNull(entry);
    return ((Value.Scalar) entry.value).span;
  }

  private static SourceSpan headerSpanOf(ParsedTomlTable table, String... path) {
    return headerSpanOf((LinkedTomlTable) table.getTable(List.of(path)));
  }

  private static SourceSpan headerSpanOf(LinkedTomlTable table) {
    assertNotNull(table);
    return table.headerSpan;
  }

  private static SourceSpan elementSpanOf(ParsedTomlTable table, int index) {
    ListTomlArray array = (ListTomlArray) table.getArray("a");
    assertNotNull(array);
    SourceSpan span = array.entry(index).span;
    assertNotNull(span);
    return span;
  }

  private static ValueSpan bracketSpanOf(ParsedTomlTable table, String... path) {
    Entry.KeyValue entry = table.entry(List.of(path));
    assertNotNull(entry);
    ValueSpan brackets = ((ElementContainer<?>) entry.value).bracketSpan;
    assertNotNull(brackets);
    return brackets;
  }

  private static List<SourceSpan> commentSpans(ElementContainer<?> container) {
    return container
        .elements()
        .stream()
        .filter(element -> element instanceof TomlComment)
        .map(element -> ((TomlComment) element).span())
        .filter(span -> span != null)
        .collect(Collectors.toList());
  }

  private static String errorsOf(ParsedTomlTable table) {
    return table.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("\n"));
  }
}
