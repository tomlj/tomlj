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

  @ParameterizedTest
  @MethodSource("spanDocuments")
  void aDocumentIsTheTextOfItsSpansInOrder(String document) {
    ParsedTomlTable table = parse(document);
    assertFalse(table.hasErrors(), () -> errorsOf(table));
    assertEquals(document, reassemble(table));
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

  // ---- Parsing with no source kept ----

  @Test
  void aParseWithoutSourceRecordsNothing() {
    ParsedTomlTable table = parse("# a comment\n[a] # after\nb = 1\n", TomlParseOptions.defaults().withoutSource());
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
  void aValueSetFromAnotherEntrySharesTheSpanOfItsLiteral() {
    ParsedTomlTable table = parse("a = 0x10\n");
    Entry.KeyValue original = table.entry(List.of("a"));
    assertNotNull(original);

    LinkedTomlTable other = (LinkedTomlTable) MutableTomlTable.create();
    other.set("b", original.value);
    Entry.KeyValue set = other.entry(List.of("b"));
    assertNotNull(set);
    assertSame(((Value.Scalar) original.value).span, ((Value.Scalar) set.value).span);
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
    List<SourceSpan> spans = new ArrayList<>();
    collect(table, spans);
    spans.sort(Comparator.comparingInt((SourceSpan span) -> span.start));

    Source source = table.source();
    assertNotNull(source);
    StringBuilder written = new StringBuilder();
    for (SourceSpan span : spans) {
      written.append(text(span));
    }
    if (table.trailerStart() >= 0) {
      written.append(source.text(table.trailerStart(), source.length() - 1));
    }
    return written.toString();
  }

  private static String text(SourceSpan span) {
    Source source = span.source;
    if (span.kind == SourceSpan.Kind.COMMENT) {
      return source.text(span.start, span.stop);
    }
    // A line's value ends where its tail begins: the value's own span records its end, but an array or an inline table
    // does not record its brackets yet, so the tail is the only way to find the end of such a value.
    int stop = (span.kind == SourceSpan.Kind.HEADER) ? span.keyStop : (span.tailStart - 1);
    return source.text(span.start, stop) + source.text(span.tailStart, span.stop);
  }

  private static void collect(ElementContainer<?> container, List<SourceSpan> spans) {
    for (TomlElement element : container.elements()) {
      if (element instanceof TomlComment comment) {
        SourceSpan span = comment.span();
        if (span != null) {
          spans.add(span);
        }
        continue;
      }
      Entry entry = (Entry) element;
      if (entry.span != null) {
        spans.add(entry.span);
      }
      Value value = entry.value;
      if (value instanceof LinkedTomlTable table && table.headerSpan != null) {
        spans.add(table.headerSpan);
      }
      if (value instanceof ElementContainer<?> nested) {
        collect(nested, spans);
      }
    }
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
