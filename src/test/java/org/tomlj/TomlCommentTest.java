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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Enumerated cases for the comment placement rules described in {@link Comments}: what a comment attaches to (ABOVE,
 * AFTER or unattached), which container an unattached comment belongs to, and the shape of the text and position the
 * model records.
 */
class TomlCommentTest {

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------------------------

  private static LinkedTomlTable parse(String document) {
    return parse(document, TomlParseOptions.defaults());
  }

  private static LinkedTomlTable parse(String document, TomlParseOptions options) {
    return Parser.parseTable(CharStreams.fromString(document), options, new AccumulatingErrorListener());
  }

  private static List<TomlParseError> errorsOf(String document) {
    AccumulatingErrorListener errorListener = new AccumulatingErrorListener();
    Parser.parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), errorListener);
    return errorListener.errors();
  }

  private static List<TomlComment> attached(LinkedTomlTable table, String... path) {
    return table.comments(List.of(path));
  }

  private static List<TomlComment> unattached(TomlTable table) {
    return unattachedComments(table.elements());
  }

  private static List<TomlComment> unattached(TomlArray array) {
    return unattachedComments(array.elements());
  }

  private static List<TomlComment> unattachedComments(List<TomlElement> elements) {
    List<TomlComment> comments = new ArrayList<>();
    for (TomlElement element : elements) {
      if (element instanceof TomlComment comment) {
        comments.add(comment);
      }
    }
    return comments;
  }

  private static LinkedTomlTable subTable(LinkedTomlTable table, String... path) {
    return (LinkedTomlTable) table.get(List.of(path));
  }

  private static ListTomlArray subArray(LinkedTomlTable table, String... path) {
    return (ListTomlArray) table.get(List.of(path));
  }

  private static ListTomlArray subArray(ListTomlArray array, int index) {
    return (ListTomlArray) array.get(index);
  }

  private static void assertComment(TomlComment comment, TomlComment.Placement placement, String text) {
    assertEquals(placement, comment.placement());
    assertEquals(text, comment.text());
  }

  private static void assertUnattached(TomlComment comment, String text) {
    assertEquals(TomlComment.Placement.UNATTACHED, comment.placement());
    assertEquals(text, comment.text());
  }

  private static void assertPosition(TomlComment comment, int line, int column) {
    assertPosition(comment.position(), line, column);
  }

  private static void assertPosition(TomlPosition position, int line, int column) {
    assertEquals(line, position.line());
    assertEquals(column, position.column());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Attachment at document level
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAbove() {
    LinkedTomlTable table = parse("# above\nx = 1\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertTrue(unattached(table).isEmpty());
  }

  @Test
  void shouldAttachCommentAfter() {
    LinkedTomlTable table = parse("x = 1 # after\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "after");
  }

  @Test
  void shouldAttachBothAboveAndAfter() {
    LinkedTomlTable table = parse("# above\nx = 1 # after\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after");
  }

  @Test
  void shouldLeaveARunSeparatedByABlankLineUnattached() {
    LinkedTomlTable table = parse("# unattached\n\nx = 1\n");
    assertTrue(attached(table, "x").isEmpty());
    List<TomlComment> comments = unattached(table);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "unattached");
  }

  @Test
  void shouldLeaveTheFarOfTwoRunsUnattachedAndAttachTheNearOneAbove() {
    LinkedTomlTable table = parse("# far\n\n# near\nx = 1\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "far");

    List<TomlComment> xComments = attached(table, "x");
    assertEquals(1, xComments.size());
    assertComment(xComments.get(0), TomlComment.Placement.ABOVE, "near");
  }

  @Test
  void shouldAttachCommentsAboveAndOnATableHeaderLine() {
    LinkedTomlTable table = parse("# above\n[a] # after\nx = 1\n");
    List<TomlComment> comments = attached(table, "a");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after");
  }

  @Test
  void shouldReadArrayTableElementHeaderCommentsFromTheArrayNotTheTable() {
    LinkedTomlTable table = parse("# above\n[[x]] # after\ny = 1\n");
    // [[x]] has no line of its own to attach to as a path: the header's comments belong to the element it opens.
    assertTrue(attached(table, "x").isEmpty());

    ListTomlArray array = subArray(table, "x");
    List<TomlComment> comments = array.comments(0);
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after");
    assertTrue(unattached(array).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Section-boundary cases
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldOwnASectionEndCommentSeparatedByBlankLinesAsTheRootTable() {
    // [a] / x = 1 / blank / # note / blank / [b] -> root table.
    LinkedTomlTable table = parse("[a]\nx = 1\n\n# note\n\n[b]\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "note");
    assertTrue(unattached(subTable(table, "a")).isEmpty());
  }

  @Test
  void shouldOwnASectionEndCommentGluedToTheLineAboveAsThatSection() {
    // [a] / x = 1 / # note / blank / [b] -> table a.
    LinkedTomlTable table = parse("[a]\nx = 1\n# note\n\n[b]\n");
    assertTrue(unattached(table).isEmpty());
    List<TomlComment> aComments = unattached(subTable(table, "a"));
    assertEquals(1, aComments.size());
    assertUnattached(aComments.get(0), "note");
  }

  @Test
  void shouldOwnACommentAfterAHeaderSeparatedByABlankLineFromTheNextEntryAsTheCurrentSection() {
    // [a] / # note / blank / x = 1 -> table a.
    LinkedTomlTable table = parse("[a]\n# note\n\nx = 1\n");
    assertTrue(unattached(table).isEmpty());
    List<TomlComment> aComments = unattached(subTable(table, "a"));
    assertEquals(1, aComments.size());
    assertUnattached(aComments.get(0), "note");
    assertTrue(attached(subTable(table, "a"), "x").isEmpty());
  }

  @Test
  void shouldOwnATrailingCommentAtEndOfDocumentAsTheRootTableWhenSeparatedByABlankLine() {
    // [a] / x = 1 / blank / # end -> root table.
    LinkedTomlTable table = parse("[a]\nx = 1\n\n# end\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "end");
    assertTrue(unattached(subTable(table, "a")).isEmpty());
  }

  @Test
  void shouldLeaveARunAtTheVeryStartOfTheDocumentUnattachedInTheRootWhenFollowedByABlankLine() {
    LinkedTomlTable table = parse("# start\n\nx = 1\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "start");
    assertTrue(attached(table, "x").isEmpty());
  }

  @Test
  void shouldAttachARunAtTheStartOfTheDocumentAboveTheFirstKeyWhenGlued() {
    LinkedTomlTable table = parse("# note\nx = 1\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "note");
    assertTrue(unattached(table).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Arrays
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAboveAnArrayValue() {
    ListTomlArray array = subArray(parse("a = [\n# above\n1\n]\n"), "a");
    List<TomlComment> comments = array.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertTrue(unattached(array).isEmpty());
  }

  @Test
  void shouldAttachCommentAfterAnArrayValueWhenTheCommaFollowsOnTheSameLine() {
    // "1, # trails one" - the comma precedes the comment, but the comment still trails the value.
    ListTomlArray array = subArray(parse("a = [1, # trails one\n]\n"), "a");
    List<TomlComment> comments = array.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "trails one");
  }

  @Test
  void shouldAttachCommentAfterAnArrayValueWhenTheCommaFollowsOnTheNextLine() {
    // "1 # c" / ", 2" - the comment precedes the comma, on the value's own line.
    ListTomlArray array = subArray(parse("a = [1 # c\n, 2]\n"), "a");
    List<TomlComment> first = array.comments(0);
    assertEquals(1, first.size());
    assertComment(first.get(0), TomlComment.Placement.AFTER, "c");
    assertEquals(2L, array.get(1));
    assertTrue(array.comments(1).isEmpty());
  }

  @Test
  void shouldAttachARunAboveACommaLedLineToTheArrayValueOnThatLine() {
    // "1 # t" / "# ab" / ", 2" - the comma starting the line does not separate 2 from the run above it.
    ListTomlArray array = subArray(parse("a = [1 # t\n# ab\n, 2]\n"), "a");
    List<TomlComment> first = array.comments(0);
    assertEquals(1, first.size());
    assertComment(first.get(0), TomlComment.Placement.AFTER, "t");
    List<TomlComment> second = array.comments(1);
    assertEquals(1, second.size());
    assertComment(second.get(0), TomlComment.Placement.ABOVE, "ab");
    assertTrue(unattached(array).isEmpty());
  }

  @Test
  void shouldLeaveACommentOnTheCommasOwnLineUnattached() {
    // "1" / ", # c" - the comment is written on the comma's line, not the value's.
    ListTomlArray array = subArray(parse("a = [1\n, # c\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
  }

  @Test
  void shouldLeaveACommentOnTheOpeningBracketsLineUnattached() {
    ListTomlArray array = subArray(parse("a = [ # c\n1\n]\n"), "a");
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
    assertTrue(array.comments(0).isEmpty());
    assertEquals(1L, array.get(0));
  }

  @Test
  void shouldLeaveARunBeforeTheClosingBracketUnattached() {
    ListTomlArray array = subArray(parse("a = [\n1\n# trailing run\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "trailing run");
  }

  @Test
  void shouldLeaveARunFollowedByABlankLineThenAValueUnattached() {
    ListTomlArray array = subArray(parse("a = [\n# note\n\n2\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "note");
    assertEquals(2L, array.get(0));
  }

  @Test
  void shouldRecordAnArrayHoldingOnlyAComment() {
    ListTomlArray array = subArray(parse("a = [\n# only\n]\n"), "a");
    assertEquals(0, array.size());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "only");
  }

  @Test
  void shouldAttachACommentAfterTheClosingBracketToTheKeyValuePairNotTheArray() {
    LinkedTomlTable table = parse("a = [\n1\n] # c\n");
    ListTomlArray array = subArray(table, "a");
    assertTrue(unattached(array).isEmpty());
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "c");
  }

  @Test
  void shouldAttachACommentInsideANestedArrayToTheInnerArray() {
    ListTomlArray outer = subArray(parse("a = [\n[\n# note\n1\n]\n]\n"), "a");
    assertTrue(unattached(outer).isEmpty());
    ListTomlArray inner = subArray(outer, 0);
    List<TomlComment> comments = inner.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "note");
    assertTrue(unattached(inner).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Inline tables (TOML 1.1.0, multi-line)
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAboveAnInlineTableEntry() {
    LinkedTomlTable inline = subTable(parse("a = {\n# above\nx = 1\n}\n"), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertTrue(unattached(inline).isEmpty());
  }

  @Test
  void shouldAttachCommentAboveAnInlineTableEntryAtVersionHead() {
    LinkedTomlTable inline =
        subTable(parse("a = {\n# above\nx = 1\n}\n", TomlParseOptions.defaults().withVersion(TomlVersion.HEAD)), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
  }

  @Test
  void shouldAttachCommentAfterAnInlineTableEntryWhenTheCommaFollowsOnTheSameLine() {
    LinkedTomlTable inline = subTable(parse("a = {x = 1, # trails one\n}\n"), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "trails one");
  }

  @Test
  void shouldAttachCommentAfterAnInlineTableEntryWhenTheCommaFollowsOnTheNextLine() {
    LinkedTomlTable inline = subTable(parse("a = {x = 1 # c\n, y = 2}\n"), "a");
    List<TomlComment> xComments = attached(inline, "x");
    assertEquals(1, xComments.size());
    assertComment(xComments.get(0), TomlComment.Placement.AFTER, "c");
    assertEquals(2L, inline.get(List.of("y")));
    assertTrue(attached(inline, "y").isEmpty());
  }

  @Test
  void shouldLeaveACommentOnTheCommasOwnLineUnattachedInAnInlineTable() {
    LinkedTomlTable inline = subTable(parse("a = {x = 1\n, # c\n}\n"), "a");
    assertTrue(attached(inline, "x").isEmpty());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
  }

  @Test
  void shouldLeaveACommentOnTheOpeningBracesLineUnattached() {
    LinkedTomlTable inline = subTable(parse("a = { # c\nx = 1\n}\n"), "a");
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
    assertTrue(attached(inline, "x").isEmpty());
  }

  @Test
  void shouldLeaveARunBeforeTheClosingBraceUnattached() {
    LinkedTomlTable inline = subTable(parse("a = {\nx = 1\n# trailing run\n}\n"), "a");
    assertTrue(attached(inline, "x").isEmpty());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "trailing run");
  }

  @Test
  void shouldLeaveARunFollowedByABlankLineThenAnEntryUnattachedInAnInlineTable() {
    LinkedTomlTable inline = subTable(parse("a = {\n# note\n\ny = 2\n}\n"), "a");
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "note");
    assertEquals(2L, inline.get(List.of("y")));
    assertTrue(attached(inline, "y").isEmpty());
  }

  @Test
  void shouldRecordAnInlineTableHoldingOnlyAComment() {
    LinkedTomlTable inline = subTable(parse("a = {\n# only\n}\n"), "a");
    assertEquals(0, inline.size());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "only");
  }

  @Test
  void shouldAttachACommentAfterTheClosingBraceToTheKeyValuePairNotTheInlineTable() {
    LinkedTomlTable table = parse("a = {\nx = 1\n} # c\n");
    LinkedTomlTable inline = subTable(table, "a");
    assertTrue(unattached(inline).isEmpty());
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "c");
  }

  @Test
  void shouldAttachACommentInsideANestedInlineTableToTheInnerTable() {
    LinkedTomlTable outer = subTable(parse("a = {\nb = {\n# note\nx = 1\n}\n}\n"), "a");
    assertTrue(unattached(outer).isEmpty());
    LinkedTomlTable inner = subTable(outer, "b");
    List<TomlComment> comments = attached(inner, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "note");
    assertTrue(unattached(inner).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Text form
  // ---------------------------------------------------------------------------------------------------------------

  private static Stream<Arguments> textFormCases() {
    return Stream
        .of(
            Arguments.of("#foo", "foo"),
            Arguments.of("# foo", "foo"),
            Arguments.of("#  wide", " wide"),
            Arguments.of("#", ""));
  }

  @ParameterizedTest
  @MethodSource("textFormCases")
  void shouldStripAtMostOneLeadingSpaceFromCommentText(String written, String expected) {
    LinkedTomlTable table = parse("x = 1 " + written + "\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    TomlComment comment = comments.get(0);
    assertEquals(List.of(expected), comment.lines());
    assertEquals(expected, comment.text());
  }

  @Test
  void shouldKeepATabInsideACommentAfterStrippingOneLeadingSpace() {
    LinkedTomlTable table = parse("x = 1 # a\tb\n");
    TomlComment comment = attached(table, "x").get(0);
    assertEquals(List.of("a\tb"), comment.lines());
    assertEquals("a\tb", comment.text());
  }

  @Test
  void shouldJoinTheLinesOfAMultiLineRunWithNewlines() {
    LinkedTomlTable table = parse("# line one\n# line two\nx = 1\n");
    TomlComment comment = attached(table, "x").get(0);
    assertEquals(List.of("line one", "line two"), comment.lines());
    assertEquals("line one\nline two", comment.text());
  }

  @Test
  void shouldReportThePositionOfTheFirstLineOfARun() {
    LinkedTomlTable table = parse("  # note\n# more\nx = 1\n");
    TomlComment comment = attached(table, "x").get(0);
    assertPosition(comment, 1, 3);
  }

  @Test
  void shouldReportThePositionOfAnAfterComment() {
    LinkedTomlTable table = parse("x = 1 # note\n");
    TomlComment comment = attached(table, "x").get(0);
    assertPosition(comment, 1, 7);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // No comments at all
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldReadTheCommentAtAPlacement() {
    LinkedTomlTable table =
        parse("# above a\na = 1 # after a\nb = 2 # after b\n[t]\nl = [\n  # above 1\n  1, # after 1\n  2\n]\n");

    assertComment(table.comment("a", TomlComment.Placement.ABOVE), TomlComment.Placement.ABOVE, "above a");
    assertComment(table.comment(List.of("a"), TomlComment.Placement.AFTER), TomlComment.Placement.AFTER, "after a");
    assertSame(table.comments("a").get(1), table.entry(List.of("a")).comment(TomlComment.Placement.AFTER));
    assertNull(table.comment("b", TomlComment.Placement.ABOVE));
    assertNull(table.comment("missing", TomlComment.Placement.ABOVE));
    assertNull(table.comment(List.of(), TomlComment.Placement.ABOVE));

    TomlArray l = table.getArray("t.l");
    assertComment(l.comment(0, TomlComment.Placement.ABOVE), TomlComment.Placement.ABOVE, "above 1");
    assertComment(l.comment(0, TomlComment.Placement.AFTER), TomlComment.Placement.AFTER, "after 1");
    assertNull(l.comment(1, TomlComment.Placement.ABOVE));
    assertNull(l.entry(1).comment(TomlComment.Placement.AFTER));
  }

  @Test
  void shouldRejectAnUnattachedOrNullPlacementWhenReadingAComment() {
    LinkedTomlTable table = parse("a = 1\nl = [1]\n");
    TomlArray l = table.getArray("l");

    assertThrows(IllegalArgumentException.class, () -> table.comment("a", TomlComment.Placement.UNATTACHED));
    assertThrows(IllegalArgumentException.class, () -> table.comment("missing", TomlComment.Placement.UNATTACHED));
    assertThrows(IllegalArgumentException.class, () -> l.comment(0, TomlComment.Placement.UNATTACHED));
    assertThrows(IllegalArgumentException.class, () -> l.entry(0).comment(TomlComment.Placement.UNATTACHED));
    assertThrows(NullPointerException.class, () -> table.comment("a", null));
    assertThrows(NullPointerException.class, () -> l.comment(0, null));
    assertThrows(IndexOutOfBoundsException.class, () -> l.comment(1, TomlComment.Placement.ABOVE));
  }

  @Test
  void shouldReturnEmptyListsWhenTheDocumentHasNoComments() {
    LinkedTomlTable table = parse("x = 1\na = [1, 2]\n");
    assertTrue(unattached(table).isEmpty());
    assertTrue(attached(table, "x").isEmpty());
    assertTrue(attached(table, "unknown").isEmpty());

    ListTomlArray array = subArray(table, "a");
    assertTrue(unattached(array).isEmpty());
    assertTrue(array.comments(0).isEmpty());
    assertTrue(array.comments(1).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // End of input
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachAnAfterCommentAtEndOfInputWithNoTrailingNewline() {
    LinkedTomlTable table = parse("a = 1 # c");
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.AFTER, "c");
  }

  @Test
  void shouldLeaveAGluedRunAtEndOfInputWithNoTrailingNewlineUnattachedInTheRoot() {
    LinkedTomlTable table = parse("a = 1\n# end");
    List<TomlComment> comments = unattached(table);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "end");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Dropped with an erroneous expression
  // ---------------------------------------------------------------------------------------------------------------

  // Accepted limitation: when an expression is discarded because it failed to parse (here, "@" cannot start a
  // value), any comment written above it or trailing it is discarded along with it, rather than falling back to
  // being recorded as unattached. The rule under test is documented in Comments: only ABOVE, AFTER and
  // unattached-to-a-container are modelled, and an erroneous expression's comments simply vanish.
  @Test
  void shouldRecordNoCommentForAnErroneousExpression() {
    assertFalse(errorsOf("a = @ # c\n").isEmpty(), "expected the malformed value to be reported as an error");

    LinkedTomlTable table = parse("a = @ # c\n");
    assertFalse(table.keySet().contains("a"), "the erroneous pair should not have been recorded");
    assertTrue(unattached(table).isEmpty(), "the trailing comment should not have been recorded as unattached");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Document order
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldSequenceRootTableElementsInDocumentOrder() {
    LinkedTomlTable table = parse(
        "a = 1\n"
            + "# separated from b by the blank line\n"
            + "\n"
            + "b = 2 # after b\n"
            + "# glued to the line above, so unattached and here\n");
    List<TomlElement> elements = table.elements();
    assertEquals(4, elements.size());

    assertTrue(elements.get(0) instanceof TomlKeyValue);
    TomlKeyValue a = (TomlKeyValue) elements.get(0);
    assertEquals("a", a.key());
    assertEquals(1L, a.value().get());
    assertTrue(a.comments().isEmpty());

    assertTrue(elements.get(1) instanceof TomlComment);
    assertUnattached((TomlComment) elements.get(1), "separated from b by the blank line");

    assertTrue(elements.get(2) instanceof TomlKeyValue);
    TomlKeyValue b = (TomlKeyValue) elements.get(2);
    assertEquals("b", b.key());
    assertEquals(2L, b.value().get());
    assertEquals(1, b.comments().size());
    assertComment(b.comments().get(0), TomlComment.Placement.AFTER, "after b");

    assertTrue(elements.get(3) instanceof TomlComment);
    assertUnattached((TomlComment) elements.get(3), "glued to the line above, so unattached and here");
  }

  @Test
  void shouldSequenceASectionTablesElementsSeparatelyFromTheRoot() {
    LinkedTomlTable table = parse("# above t\n[t]\nx = 1\n# unattached\n");

    List<TomlElement> rootElements = table.elements();
    assertEquals(1, rootElements.size());
    assertTrue(rootElements.get(0) instanceof TomlKeyValue);
    TomlKeyValue t = (TomlKeyValue) rootElements.get(0);
    assertEquals("t", t.key());
    assertEquals(1, t.comments().size());
    assertComment(t.comments().get(0), TomlComment.Placement.ABOVE, "above t");

    List<TomlElement> tElements = subTable(table, "t").elements();
    assertEquals(2, tElements.size());

    assertTrue(tElements.get(0) instanceof TomlKeyValue);
    TomlKeyValue x = (TomlKeyValue) tElements.get(0);
    assertEquals("x", x.key());
    assertEquals(1L, x.value().get());
    assertTrue(x.comments().isEmpty());

    assertTrue(tElements.get(1) instanceof TomlComment);
    assertUnattached((TomlComment) tElements.get(1), "unattached");
  }

  @Test
  void shouldInterleaveArrayValuesAndUnattachedCommentsInDocumentOrder() {
    ListTomlArray array = subArray(parse("a = [1,\n# note\n\n2\n]\n"), "a");
    List<TomlElement> elements = array.elements();
    assertEquals(3, elements.size());

    assertTrue(elements.get(0) instanceof TomlEntry);
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());

    assertTrue(elements.get(1) instanceof TomlComment);
    assertUnattached((TomlComment) elements.get(1), "note");

    assertTrue(elements.get(2) instanceof TomlEntry);
    assertEquals(2L, ((TomlEntry) elements.get(2)).value().get());

    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "note");
  }

  @Test
  void shouldGiveEachNestedArrayValueItsOwnSequenceAndPosition() {
    ListTomlArray outer = subArray(parse("a = [[1, 2], [3]]\n"), "a");
    assertEquals(2, outer.size());

    ListTomlArray first = subArray(outer, 0);
    assertEquals(2, first.size());
    assertEquals(1L, first.get(0));
    assertEquals(2L, first.get(1));
    assertPosition(first.position(), 1, 6);

    ListTomlArray second = subArray(outer, 1);
    assertEquals(1, second.size());
    assertEquals(3L, second.get(0));
    assertPosition(second.position(), 1, 14);
  }

  @Test
  void shouldReportKeyValueAndScalarPositionsSeparately() {
    LinkedTomlTable table = parse("key = \"v\"\n");
    TomlKeyValue keyValue = (TomlKeyValue) table.elements().get(0);
    assertPosition(keyValue.position(), 1, 1);
    assertPosition(keyValue.value().position(), 1, 7);
  }

  @Test
  void shouldReadAValueThroughItsTypedAccessors() {
    LinkedTomlTable table = parse("key = \"v\" # after\n");
    TomlKeyValue pair = (TomlKeyValue) table.elements().get(0);
    TomlValue value = pair.value();
    assertTrue(value.isString());
    assertFalse(value.isLong());
    assertEquals("v", value.getString());
    assertThrows(TomlInvalidTypeException.class, value::getLong);
    assertEquals(1, pair.comments().size());
    assertComment(pair.comments().get(0), TomlComment.Placement.AFTER, "after");
  }

  @Test
  void shouldReportATableHeaderPositionAsTheTablesOwnPosition() {
    LinkedTomlTable table = subTable(parse("[t]\n"), "t");
    assertPosition(table.position(), 1, 1);
  }

  @Test
  void shouldReportEachArrayTableElementsOwnHeaderPosition() {
    LinkedTomlTable table = parse("[[x]]\na = 1\n[[x]]\nb = 2\n");
    ListTomlArray array = subArray(table, "x");
    assertPosition(array.position(), 1, 1);
    assertPosition(((LinkedTomlTable) array.get(0)).position(), 1, 1);
    assertPosition(((LinkedTomlTable) array.get(1)).position(), 3, 1);
  }

  @Test
  void shouldReportAnInlineTablesPositionAsItsOpeningBrace() {
    LinkedTomlTable inline = subTable(parse("k = { a = 1 }\n"), "k");
    assertPosition(inline.position(), 1, 5);
  }

  @Test
  void shouldPlaceTheRootTableAtTheStartOfTheDocument() {
    LinkedTomlTable table = parse("\n# leading comment\n[t]\n");
    assertPosition(table.position(), 1, 1);
    assertEquals(TomlPosition.positionAt(1, 1), table.inputPositionOf(List.of()));
    assertEquals(TomlPosition.positionAt(3, 1), subTable(table, "t").inputPositionOf(List.of()));
  }

  @Test
  void shouldLeaveAnImplicitlyCreatedTableWithNoPositionUntilAHeaderDefinesIt() {
    // [a.b] creates "a" implicitly, as the leading key of its header path, with no position of its own; [a] later
    // defines "a" directly, taking over the spot its implicit creation already holds in the root table's sequence.
    assertNull(subTable(parse("[a.b]\nx = 1\n"), "a").position());

    LinkedTomlTable table = parse("[a.b]\nx = 1\n[a]\nc = 2\n");
    LinkedTomlTable a = subTable(table, "a");
    assertEquals(TomlPosition.positionAt(3, 1), a.position());
    assertEquals(TomlPosition.positionAt(3, 1), table.inputPositionOf(List.of("a")));
  }

  @Test
  void shouldAttachCommentsToAnArrayMembersValueRatherThanTheArray() {
    ListTomlArray array = subArray(parse("a = [ # after bracket\n  # above one\n  1, # after one\n]\n"), "a");
    List<TomlComment> comments = array.comments(0);
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above one");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after one");

    List<TomlComment> arrayComments = unattached(array);
    assertEquals(1, arrayComments.size());
    assertUnattached(arrayComments.get(0), "after bracket");
  }

  @Test
  void shouldSequenceInlineTableElementsInDocumentOrder() {
    LinkedTomlTable inline = subTable(parse("t = { a = 1, # after a\n# unattached\n\nb = 2 }\n"), "t");
    List<TomlElement> elements = inline.elements();
    assertEquals(3, elements.size());

    assertTrue(elements.get(0) instanceof TomlKeyValue);
    TomlKeyValue a = (TomlKeyValue) elements.get(0);
    assertEquals("a", a.key());
    assertEquals(1L, a.value().get());
    assertEquals(1, a.comments().size());
    assertComment(a.comments().get(0), TomlComment.Placement.AFTER, "after a");

    assertTrue(elements.get(1) instanceof TomlComment);
    assertUnattached((TomlComment) elements.get(1), "unattached");

    assertTrue(elements.get(2) instanceof TomlKeyValue);
    TomlKeyValue b = (TomlKeyValue) elements.get(2);
    assertEquals("b", b.key());
    assertEquals(2L, b.value().get());
  }

  @Test
  void shouldGiveAnEntryTheSameCommentsAsTheTableLookup() {
    LinkedTomlTable table = parse("# above a\na = 1 # after a\n");
    TomlKeyValue a = (TomlKeyValue) table.elements().get(0);
    assertEquals(table.comments("a"), a.comments());
    assertEquals(table.inputPositionOf("a"), a.position());
  }

  @Test
  void shouldGiveAnArrayEntryTheSameCommentsAndPositionAsTheIndexLookup() {
    ListTomlArray array = subArray(parse("a = [\n# above\n1, # after\n]\n"), "a");
    TomlEntry entry = (TomlEntry) array.elements().get(0);
    assertEquals(1L, entry.value().get());
    assertEquals(array.comments(0), entry.comments());
    assertEquals(array.inputPositionOf(0), entry.position());
  }

  @Test
  void shouldGiveAnArrayEntryItsCommentsAndValue() {
    ListTomlArray array = subArray(parse("a = [\n# above\n1, # after\n]\n"), "a");
    TomlEntry entry = array.entry(0);
    assertSame(array.elements().get(0), entry);
    List<TomlComment> comments = entry.comments();
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after");
    assertEquals(1L, entry.value().getLong());
  }

  @Test
  void shouldSequenceAnArrayOfTablesAsEntriesOfTheArray() {
    LinkedTomlTable table = parse("[[x]]\na = 1\n# between\n\n[[x]]\nb = 2\n");

    List<TomlElement> rootElements = table.elements();
    assertEquals(1, rootElements.size());
    assertTrue(rootElements.get(0) instanceof TomlKeyValue);
    TomlKeyValue x = (TomlKeyValue) rootElements.get(0);
    assertEquals("x", x.key());
    assertTrue(x.value().isArray());

    TomlArray array = x.value().getArray();
    List<TomlElement> arrayElements = array.elements();
    assertEquals(2, arrayElements.size());

    assertTrue(arrayElements.get(0) instanceof TomlEntry);
    TomlTable first = ((TomlEntry) arrayElements.get(0)).value().getTable();
    assertEquals(1L, first.getLong("a"));

    assertTrue(arrayElements.get(1) instanceof TomlEntry);
    TomlTable second = ((TomlEntry) arrayElements.get(1)).value().getTable();
    assertEquals(2L, second.getLong("b"));

    // "# between" ends the first [[x]] section, so, like a run at the end of any section, it lands in that table's
    // own elements rather than the array's.
    List<TomlElement> firstElements = first.elements();
    assertEquals(2, firstElements.size());
    assertTrue(firstElements.get(1) instanceof TomlComment);
    assertUnattached((TomlComment) firstElements.get(1), "between");
  }

  @Test
  void shouldReportNoPositionForATableCreatedByADottedKeyUntilAHeaderDefinesIt() {
    LinkedTomlTable table = parse("a.b = 1\n");
    TomlKeyValue a = (TomlKeyValue) table.elements().get(0);
    assertNull(a.value().position());
    assertPosition(table.inputPositionOf("a"), 1, 1);

    LinkedTomlTable defined = parse("[a.b]\n[a]\n");
    assertPosition(((TomlValue) defined.getTable("a")).position(), 2, 1);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldReadAttachedCommentsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# above\na.b = 1 # after\n");
    List<TomlComment> comments = result.comments("a.b");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "above");
    assertComment(comments.get(1), TomlComment.Placement.AFTER, "after");
  }

  @Test
  void shouldReadCommentsForAQuotedDottedKeyThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# c\n\"x y\" = 1\n");
    List<TomlComment> comments = result.comments("\"x y\"");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "c");
  }

  @Test
  void shouldReturnAnEmptyListForAnUnsetOrUncommentedKeyThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    assertTrue(result.comments("unset").isEmpty());
    assertTrue(result.comments("a").isEmpty());
  }

  @Test
  void shouldReadArrayTableHeaderCommentsFromTheArrayThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# h\n[[x]]\na = 1\n");
    assertTrue(result.comments("x").isEmpty());

    TomlArray array = result.getArray("x");
    List<TomlComment> comments = array.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), TomlComment.Placement.ABOVE, "h");
  }

  @Test
  void shouldReadUnattachedTableCommentsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n\n# footer\n");
    List<TomlComment> comments = unattached(result);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "footer");
  }

  @Test
  void shouldReturnUnmodifiableListsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# above\na = 1 # after\n\n# footer\n");

    List<TomlComment> attached = result.comments("a");
    assertThrows(UnsupportedOperationException.class, () -> attached.add(attached.get(0)));

    List<TomlElement> elements = result.elements();
    assertThrows(UnsupportedOperationException.class, () -> elements.add(elements.get(0)));

    TomlArray array = Toml.parse("a = [\n1 # after\n]\n").getArray("a");
    List<TomlComment> elementComments = array.comments(0);
    assertThrows(UnsupportedOperationException.class, () -> elementComments.add(elementComments.get(0)));

    List<TomlElement> arrayElements = array.elements();
    assertThrows(UnsupportedOperationException.class, () -> arrayElements.add(arrayElements.get(0)));
  }

  @Test
  void shouldReturnEmptyCommentsForAMissingTableThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    TomlTable missing = result.getTableOrEmpty("nope");
    assertTrue(missing.comments("k").isEmpty());
    assertTrue(missing.elements().isEmpty());
  }

  @Test
  void shouldReturnEmptyCommentsForAMissingArrayThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    TomlArray missing = result.getArrayOrEmpty("nope");
    assertThrows(IndexOutOfBoundsException.class, () -> missing.comments(0));
    assertTrue(missing.elements().isEmpty());
  }

  @Test
  void shouldThrowExceptionForAnUnparseableDottedKeyThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    assertThrows(IllegalArgumentException.class, () -> result.comments("a@b"));
  }
}
