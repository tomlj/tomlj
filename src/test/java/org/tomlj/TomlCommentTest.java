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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  private static MutableTomlTable parse(String document) {
    return parse(document, TomlParseOptions.defaults());
  }

  private static MutableTomlTable parse(String document, TomlParseOptions options) {
    return Parser.parseTable(CharStreams.fromString(document), options, new AccumulatingErrorListener());
  }

  private static List<TomlParseError> errorsOf(String document) {
    AccumulatingErrorListener errorListener = new AccumulatingErrorListener();
    Parser.parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), errorListener);
    return errorListener.errors();
  }

  private static List<TomlComment> attached(MutableTomlTable table, String... path) {
    return table.comments(List.of(path));
  }

  private static List<TomlComment> unattached(MutableTomlTable table) {
    return table.comments();
  }

  private static List<TomlComment> unattached(MutableTomlArray array) {
    return array.comments();
  }

  private static MutableTomlTable subTable(MutableTomlTable table, String... path) {
    return (MutableTomlTable) table.get(List.of(path));
  }

  private static MutableTomlArray subArray(MutableTomlTable table, String... path) {
    return (MutableTomlArray) table.get(List.of(path));
  }

  private static MutableTomlArray subArray(MutableTomlArray array, int index) {
    return (MutableTomlArray) array.get(index);
  }

  private static void assertComment(TomlComment comment, CommentPlacement placement, String text) {
    assertEquals(placement, comment.placement());
    assertEquals(text, comment.text());
  }

  private static void assertUnattached(TomlComment comment, String text) {
    assertEquals(null, comment.placement());
    assertEquals(text, comment.text());
  }

  private static void assertPosition(TomlComment comment, int line, int column) {
    TomlPosition position = comment.position();
    assertEquals(line, position.line());
    assertEquals(column, position.column());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Attachment at document level
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAbove() {
    MutableTomlTable table = parse("# above\nx = 1\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertTrue(unattached(table).isEmpty());
  }

  @Test
  void shouldAttachCommentAfter() {
    MutableTomlTable table = parse("x = 1 # after\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "after");
  }

  @Test
  void shouldAttachBothAboveAndAfter() {
    MutableTomlTable table = parse("# above\nx = 1 # after\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertComment(comments.get(1), CommentPlacement.AFTER, "after");
  }

  @Test
  void shouldLeaveARunSeparatedByABlankLineUnattached() {
    MutableTomlTable table = parse("# unattached\n\nx = 1\n");
    assertTrue(attached(table, "x").isEmpty());
    List<TomlComment> comments = unattached(table);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "unattached");
  }

  @Test
  void shouldLeaveTheFarOfTwoRunsUnattachedAndAttachTheNearOneAbove() {
    MutableTomlTable table = parse("# far\n\n# near\nx = 1\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "far");

    List<TomlComment> xComments = attached(table, "x");
    assertEquals(1, xComments.size());
    assertComment(xComments.get(0), CommentPlacement.ABOVE, "near");
  }

  @Test
  void shouldAttachCommentsAboveAndOnATableHeaderLine() {
    MutableTomlTable table = parse("# above\n[a] # after\nx = 1\n");
    List<TomlComment> comments = attached(table, "a");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertComment(comments.get(1), CommentPlacement.AFTER, "after");
  }

  @Test
  void shouldReadArrayTableElementHeaderCommentsFromTheArrayNotTheTable() {
    MutableTomlTable table = parse("# above\n[[x]] # after\ny = 1\n");
    // [[x]] has no line of its own to attach to as a path: the header's comments belong to the element it opens.
    assertTrue(attached(table, "x").isEmpty());

    MutableTomlArray array = subArray(table, "x");
    List<TomlComment> comments = array.comments(0);
    assertEquals(2, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertComment(comments.get(1), CommentPlacement.AFTER, "after");
    assertTrue(unattached(array).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Section-boundary cases
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldOwnASectionEndCommentSeparatedByBlankLinesAsTheRootTable() {
    // [a] / x = 1 / blank / # note / blank / [b] -> root table.
    MutableTomlTable table = parse("[a]\nx = 1\n\n# note\n\n[b]\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "note");
    assertTrue(unattached(subTable(table, "a")).isEmpty());
  }

  @Test
  void shouldOwnASectionEndCommentGluedToTheLineAboveAsThatSection() {
    // [a] / x = 1 / # note / blank / [b] -> table a.
    MutableTomlTable table = parse("[a]\nx = 1\n# note\n\n[b]\n");
    assertTrue(unattached(table).isEmpty());
    List<TomlComment> aComments = unattached(subTable(table, "a"));
    assertEquals(1, aComments.size());
    assertUnattached(aComments.get(0), "note");
  }

  @Test
  void shouldOwnACommentAfterAHeaderSeparatedByABlankLineFromTheNextEntryAsTheCurrentSection() {
    // [a] / # note / blank / x = 1 -> table a.
    MutableTomlTable table = parse("[a]\n# note\n\nx = 1\n");
    assertTrue(unattached(table).isEmpty());
    List<TomlComment> aComments = unattached(subTable(table, "a"));
    assertEquals(1, aComments.size());
    assertUnattached(aComments.get(0), "note");
    assertTrue(attached(subTable(table, "a"), "x").isEmpty());
  }

  @Test
  void shouldOwnATrailingCommentAtEndOfDocumentAsTheRootTableWhenSeparatedByABlankLine() {
    // [a] / x = 1 / blank / # end -> root table.
    MutableTomlTable table = parse("[a]\nx = 1\n\n# end\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "end");
    assertTrue(unattached(subTable(table, "a")).isEmpty());
  }

  @Test
  void shouldLeaveARunAtTheVeryStartOfTheDocumentUnattachedInTheRootWhenFollowedByABlankLine() {
    MutableTomlTable table = parse("# start\n\nx = 1\n");
    List<TomlComment> rootComments = unattached(table);
    assertEquals(1, rootComments.size());
    assertUnattached(rootComments.get(0), "start");
    assertTrue(attached(table, "x").isEmpty());
  }

  @Test
  void shouldAttachARunAtTheStartOfTheDocumentAboveTheFirstKeyWhenGlued() {
    MutableTomlTable table = parse("# note\nx = 1\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "note");
    assertTrue(unattached(table).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Arrays
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAboveAnArrayValue() {
    MutableTomlArray array = subArray(parse("a = [\n# above\n1\n]\n"), "a");
    List<TomlComment> comments = array.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertTrue(unattached(array).isEmpty());
  }

  @Test
  void shouldAttachCommentAfterAnArrayValueWhenTheCommaFollowsOnTheSameLine() {
    // "1, # trails one" - the comma precedes the comment, but the comment still trails the value.
    MutableTomlArray array = subArray(parse("a = [1, # trails one\n]\n"), "a");
    List<TomlComment> comments = array.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "trails one");
  }

  @Test
  void shouldAttachCommentAfterAnArrayValueWhenTheCommaFollowsOnTheNextLine() {
    // "1 # c" / ", 2" - the comment precedes the comma, on the value's own line.
    MutableTomlArray array = subArray(parse("a = [1 # c\n, 2]\n"), "a");
    List<TomlComment> first = array.comments(0);
    assertEquals(1, first.size());
    assertComment(first.get(0), CommentPlacement.AFTER, "c");
    assertEquals(2L, array.get(1));
    assertTrue(array.comments(1).isEmpty());
  }

  @Test
  void shouldAttachARunAboveACommaLedLineToTheArrayValueOnThatLine() {
    // "1 # t" / "# ab" / ", 2" - the comma starting the line does not separate 2 from the run above it.
    MutableTomlArray array = subArray(parse("a = [1 # t\n# ab\n, 2]\n"), "a");
    List<TomlComment> first = array.comments(0);
    assertEquals(1, first.size());
    assertComment(first.get(0), CommentPlacement.AFTER, "t");
    List<TomlComment> second = array.comments(1);
    assertEquals(1, second.size());
    assertComment(second.get(0), CommentPlacement.ABOVE, "ab");
    assertTrue(unattached(array).isEmpty());
  }

  @Test
  void shouldLeaveACommentOnTheCommasOwnLineUnattached() {
    // "1" / ", # c" - the comment is written on the comma's line, not the value's.
    MutableTomlArray array = subArray(parse("a = [1\n, # c\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
  }

  @Test
  void shouldLeaveACommentOnTheOpeningBracketsLineUnattached() {
    MutableTomlArray array = subArray(parse("a = [ # c\n1\n]\n"), "a");
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
    assertTrue(array.comments(0).isEmpty());
    assertEquals(1L, array.get(0));
  }

  @Test
  void shouldLeaveARunBeforeTheClosingBracketUnattached() {
    MutableTomlArray array = subArray(parse("a = [\n1\n# trailing run\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "trailing run");
  }

  @Test
  void shouldLeaveARunFollowedByABlankLineThenAValueUnattached() {
    MutableTomlArray array = subArray(parse("a = [\n# note\n\n2\n]\n"), "a");
    assertTrue(array.comments(0).isEmpty());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "note");
    assertEquals(2L, array.get(0));
  }

  @Test
  void shouldRecordAnArrayHoldingOnlyAComment() {
    MutableTomlArray array = subArray(parse("a = [\n# only\n]\n"), "a");
    assertEquals(0, array.size());
    List<TomlComment> comments = unattached(array);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "only");
  }

  @Test
  void shouldAttachACommentAfterTheClosingBracketToTheKeyValuePairNotTheArray() {
    MutableTomlTable table = parse("a = [\n1\n] # c\n");
    MutableTomlArray array = subArray(table, "a");
    assertTrue(unattached(array).isEmpty());
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "c");
  }

  @Test
  void shouldAttachACommentInsideANestedArrayToTheInnerArray() {
    MutableTomlArray outer = subArray(parse("a = [\n[\n# note\n1\n]\n]\n"), "a");
    assertTrue(unattached(outer).isEmpty());
    MutableTomlArray inner = subArray(outer, 0);
    List<TomlComment> comments = inner.comments(0);
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "note");
    assertTrue(unattached(inner).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Inline tables (TOML 1.1.0, multi-line)
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachCommentAboveAnInlineTableEntry() {
    MutableTomlTable inline = subTable(parse("a = {\n# above\nx = 1\n}\n"), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertTrue(unattached(inline).isEmpty());
  }

  @Test
  void shouldAttachCommentAboveAnInlineTableEntryAtVersionHead() {
    MutableTomlTable inline =
        subTable(parse("a = {\n# above\nx = 1\n}\n", TomlParseOptions.defaults().withVersion(TomlVersion.HEAD)), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
  }

  @Test
  void shouldAttachCommentAfterAnInlineTableEntryWhenTheCommaFollowsOnTheSameLine() {
    MutableTomlTable inline = subTable(parse("a = {x = 1, # trails one\n}\n"), "a");
    List<TomlComment> comments = attached(inline, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "trails one");
  }

  @Test
  void shouldAttachCommentAfterAnInlineTableEntryWhenTheCommaFollowsOnTheNextLine() {
    MutableTomlTable inline = subTable(parse("a = {x = 1 # c\n, y = 2}\n"), "a");
    List<TomlComment> xComments = attached(inline, "x");
    assertEquals(1, xComments.size());
    assertComment(xComments.get(0), CommentPlacement.AFTER, "c");
    assertEquals(2L, inline.get(List.of("y")));
    assertTrue(attached(inline, "y").isEmpty());
  }

  @Test
  void shouldLeaveACommentOnTheCommasOwnLineUnattachedInAnInlineTable() {
    MutableTomlTable inline = subTable(parse("a = {x = 1\n, # c\n}\n"), "a");
    assertTrue(attached(inline, "x").isEmpty());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
  }

  @Test
  void shouldLeaveACommentOnTheOpeningBracesLineUnattached() {
    MutableTomlTable inline = subTable(parse("a = { # c\nx = 1\n}\n"), "a");
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "c");
    assertTrue(attached(inline, "x").isEmpty());
  }

  @Test
  void shouldLeaveARunBeforeTheClosingBraceUnattached() {
    MutableTomlTable inline = subTable(parse("a = {\nx = 1\n# trailing run\n}\n"), "a");
    assertTrue(attached(inline, "x").isEmpty());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "trailing run");
  }

  @Test
  void shouldLeaveARunFollowedByABlankLineThenAnEntryUnattachedInAnInlineTable() {
    MutableTomlTable inline = subTable(parse("a = {\n# note\n\ny = 2\n}\n"), "a");
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "note");
    assertEquals(2L, inline.get(List.of("y")));
    assertTrue(attached(inline, "y").isEmpty());
  }

  @Test
  void shouldRecordAnInlineTableHoldingOnlyAComment() {
    MutableTomlTable inline = subTable(parse("a = {\n# only\n}\n"), "a");
    assertEquals(0, inline.size());
    List<TomlComment> comments = unattached(inline);
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "only");
  }

  @Test
  void shouldAttachACommentAfterTheClosingBraceToTheKeyValuePairNotTheInlineTable() {
    MutableTomlTable table = parse("a = {\nx = 1\n} # c\n");
    MutableTomlTable inline = subTable(table, "a");
    assertTrue(unattached(inline).isEmpty());
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "c");
  }

  @Test
  void shouldAttachACommentInsideANestedInlineTableToTheInnerTable() {
    MutableTomlTable outer = subTable(parse("a = {\nb = {\n# note\nx = 1\n}\n}\n"), "a");
    assertTrue(unattached(outer).isEmpty());
    MutableTomlTable inner = subTable(outer, "b");
    List<TomlComment> comments = attached(inner, "x");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "note");
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
    MutableTomlTable table = parse("x = 1 " + written + "\n");
    List<TomlComment> comments = attached(table, "x");
    assertEquals(1, comments.size());
    TomlComment comment = comments.get(0);
    assertEquals(List.of(expected), comment.lines());
    assertEquals(expected, comment.text());
  }

  @Test
  void shouldKeepATabInsideACommentAfterStrippingOneLeadingSpace() {
    MutableTomlTable table = parse("x = 1 # a\tb\n");
    TomlComment comment = attached(table, "x").get(0);
    assertEquals(List.of("a\tb"), comment.lines());
    assertEquals("a\tb", comment.text());
  }

  @Test
  void shouldJoinTheLinesOfAMultiLineRunWithNewlines() {
    MutableTomlTable table = parse("# line one\n# line two\nx = 1\n");
    TomlComment comment = attached(table, "x").get(0);
    assertEquals(List.of("line one", "line two"), comment.lines());
    assertEquals("line one\nline two", comment.text());
  }

  @Test
  void shouldReportThePositionOfTheFirstLineOfARun() {
    MutableTomlTable table = parse("  # note\n# more\nx = 1\n");
    TomlComment comment = attached(table, "x").get(0);
    assertPosition(comment, 1, 3);
  }

  @Test
  void shouldReportThePositionOfAnAfterComment() {
    MutableTomlTable table = parse("x = 1 # note\n");
    TomlComment comment = attached(table, "x").get(0);
    assertPosition(comment, 1, 7);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // No comments at all
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldReturnEmptyListsWhenTheDocumentHasNoComments() {
    MutableTomlTable table = parse("x = 1\na = [1, 2]\n");
    assertTrue(unattached(table).isEmpty());
    assertTrue(attached(table, "x").isEmpty());
    assertTrue(attached(table, "unknown").isEmpty());

    MutableTomlArray array = subArray(table, "a");
    assertTrue(unattached(array).isEmpty());
    assertTrue(array.comments(0).isEmpty());
    assertTrue(array.comments(1).isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // End of input
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldAttachAnAfterCommentAtEndOfInputWithNoTrailingNewline() {
    MutableTomlTable table = parse("a = 1 # c");
    List<TomlComment> comments = attached(table, "a");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.AFTER, "c");
  }

  @Test
  void shouldLeaveAGluedRunAtEndOfInputWithNoTrailingNewlineUnattachedInTheRoot() {
    MutableTomlTable table = parse("a = 1\n# end");
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

    MutableTomlTable table = parse("a = @ # c\n");
    assertFalse(table.keySet().contains("a"), "the erroneous pair should not have been recorded");
    assertTrue(unattached(table).isEmpty(), "the trailing comment should not have been recorded as unattached");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void shouldReadAttachedCommentsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# above\na.b = 1 # after\n");
    List<TomlComment> comments = result.comments("a.b");
    assertEquals(2, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "above");
    assertComment(comments.get(1), CommentPlacement.AFTER, "after");
  }

  @Test
  void shouldReadCommentsForAQuotedDottedKeyThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# c\n\"x y\" = 1\n");
    List<TomlComment> comments = result.comments("\"x y\"");
    assertEquals(1, comments.size());
    assertComment(comments.get(0), CommentPlacement.ABOVE, "c");
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
    assertComment(comments.get(0), CommentPlacement.ABOVE, "h");
  }

  @Test
  void shouldReadUnattachedTableCommentsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n\n# footer\n");
    List<TomlComment> comments = result.comments();
    assertEquals(1, comments.size());
    assertUnattached(comments.get(0), "footer");
  }

  @Test
  void shouldReturnUnmodifiableListsThroughThePublicApi() {
    TomlParseResult result = Toml.parse("# above\na = 1 # after\n\n# footer\n");

    List<TomlComment> attached = result.comments("a");
    assertThrows(UnsupportedOperationException.class, () -> attached.add(attached.get(0)));

    List<TomlComment> unattached = result.comments();
    assertThrows(UnsupportedOperationException.class, () -> unattached.add(unattached.get(0)));

    TomlArray array = Toml.parse("a = [\n1 # after\n]\n").getArray("a");
    List<TomlComment> elementComments = array.comments(0);
    assertThrows(UnsupportedOperationException.class, () -> elementComments.add(elementComments.get(0)));

    List<TomlComment> arrayUnattached = array.comments();
    assertThrows(UnsupportedOperationException.class, () -> arrayUnattached.add(elementComments.get(0)));
  }

  @Test
  void shouldReturnEmptyCommentsForAMissingTableThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    TomlTable missing = result.getTableOrEmpty("nope");
    assertTrue(missing.comments().isEmpty());
    assertTrue(missing.comments("k").isEmpty());
  }

  @Test
  void shouldReturnEmptyCommentsForAMissingArrayThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    TomlArray missing = result.getArrayOrEmpty("nope");
    assertTrue(missing.comments().isEmpty());
    assertThrows(IndexOutOfBoundsException.class, () -> missing.comments(0));
  }

  @Test
  void shouldThrowExceptionForAnUnparseableDottedKeyThroughThePublicApi() {
    TomlParseResult result = Toml.parse("a = 1\n");
    assertThrows(IllegalArgumentException.class, () -> result.comments("a@b"));
  }
}
