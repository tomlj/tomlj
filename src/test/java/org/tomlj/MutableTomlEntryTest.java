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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MutableTomlEntry} and {@link MutableTomlKeyValue}, through the public API only.
 *
 * <p>
 * A parsed document is obtained with {@link Toml#parse(String)}: a {@link TomlParseResult} is a
 * {@link MutableTomlTable}, and its entries are {@link MutableTomlKeyValue}s.
 */
class MutableTomlEntryTest {

  private static TomlParseResult parse(String document) {
    return Toml.parse(document);
  }

  @Test
  void shouldReplaceAValueAndReturnTheOldOne() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    Object previous = entry.setValue(2L);

    assertEquals(1L, previous);
    assertEquals(2L, table.get("a"));
  }

  @Test
  void shouldKeepPositionPlaceAndCommentsWhenReplacingAPairsValue() {
    TomlParseResult table = parse("# above\na = 1 # after\nb = 2\n");
    TomlPosition originalPosition = table.inputPositionOf("a");
    List<TomlComment> originalComments = table.comments("a");

    table.entry("a").setValue(99L);

    assertEquals(99L, table.get("a"));
    assertEquals(originalPosition, table.inputPositionOf("a"));
    assertEquals(originalComments, table.comments("a"));
    assertTrue(table.isModified("a"));
  }

  @Test
  void shouldHaveNoPositionAfterReplacingAnArrayEntrysValue() {
    TomlParseResult table = parse("a = [\n1 # note\n]\n");
    MutableTomlArray array = (MutableTomlArray) table.get("a");
    List<TomlComment> comments = array.comments(0);

    array.entry(0).setValue(42L);

    assertNull(array.inputPositionOf(0));
    assertEquals(comments, array.comments(0));
    assertEquals(42L, array.get(0));
  }

  @Test
  void shouldDeepCopyATableSetAsAValue() {
    TomlParseResult inner = parse("x = 1\n");
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.entry("a").setValue(inner);

    MutableTomlTable stored = table.getTable("a");
    inner.set("x", 2L);
    assertEquals(1L, stored.get("x"));
  }

  @Test
  void shouldRejectNullAndUnconvertibleValues() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(NullPointerException.class, () -> entry.setValue(null));
    assertThrows(IllegalArgumentException.class, () -> entry.setValue(new Object()));
  }

  @Test
  void shouldSetCommentAboveWithVarargsAndList() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.entry("a").setCommentAbove("line one", "line two");
    assertEquals(List.of("line one", "line two"), table.comments("a").get(0).lines());

    table.entry("a").setCommentAbove(List.of("replaced"));
    assertEquals(List.of("replaced"), table.comments("a").get(0).lines());
  }

  @Test
  void shouldReadBackAboveThenAfterInDocumentOrder() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    entry.setCommentAfter("after");
    entry.setCommentAbove("above");

    List<TomlComment> comments = entry.comments();
    assertEquals(2, comments.size());
    assertEquals(TomlComment.Placement.ABOVE, comments.get(0).placement());
    assertEquals(TomlComment.Placement.AFTER, comments.get(1).placement());
    assertEquals(comments, table.comments("a"));
  }

  @Test
  void shouldRoundTripAnEmptyLineAndTextWithNoLeadingSpace() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    entry.setCommentAbove("first", "", "third");
    assertEquals(List.of("first", "", "third"), entry.comments().get(0).lines());

    entry.setCommentAfter("tight");
    assertEquals("tight", entry.comments().get(1).lines().get(0));
  }

  @Test
  void shouldSetCommentFromTextAndPlacement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    entry.setComment("a\nb", TomlComment.Placement.ABOVE);
    assertEquals(List.of("a", "b"), entry.comments().get(0).lines());

    entry.setComment("", TomlComment.Placement.ABOVE);
    assertEquals(List.of(""), entry.comments().get(0).lines());

    entry.setComment("x", TomlComment.Placement.AFTER);
    assertEquals(List.of("x"), entry.comments().get(1).lines());
  }

  @Test
  void shouldRejectANewlineInAnAfterCommentSetFromTextAndPlacement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(IllegalArgumentException.class, () -> entry.setComment("a\nb", TomlComment.Placement.AFTER));
  }

  @Test
  void shouldRejectNullTextOrPlacement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(NullPointerException.class, () -> entry.setComment(null, TomlComment.Placement.ABOVE));
    assertThrows(NullPointerException.class, () -> entry.setComment("x", null));
  }

  @Test
  void shouldSplitALineContainingANewlineInSetCommentAbove() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    entry.setCommentAbove(List.of("a\nb", "c"));

    assertEquals(List.of("a", "b", "c"), entry.comments().get(0).lines());
  }

  @Test
  void shouldRejectAnEmptyCommentRun() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");
    assertThrows(IllegalArgumentException.class, () -> entry.setCommentAbove(List.of()));
  }

  @Test
  void shouldRejectANullLineInSetCommentAbove() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(NullPointerException.class, () -> entry.setCommentAbove(Arrays.asList("a", null)));
  }

  @Test
  void shouldRoundTripCommentTextThroughSetCommentByPlacement() {
    TomlParseResult table = parse("# first\n# second\n# third\na = 1 # tight\n");
    List<TomlComment> comments = table.comments("a");
    TomlComment above = comments.get(0);
    TomlComment after = comments.get(1);

    MutableTomlTable target = MutableTomlTable.create();
    target.set("above", 1L);
    target.set("after", 2L);

    target.entry("above").setComment(above.text(), above.placement());
    target.entry("after").setComment(after.text(), after.placement());

    assertEquals(above.lines(), target.entry("above").comments().get(0).lines());
    assertEquals(after.lines(), target.entry("after").comments().get(0).lines());
  }

  @Test
  void shouldRejectInvalidCommentCharacters() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(IllegalArgumentException.class, () -> entry.setCommentAfter("line\nbreak"));
    assertThrows(IllegalArgumentException.class, () -> entry.setCommentAfter(String.valueOf((char) 1)));
    assertThrows(IllegalArgumentException.class, () -> entry.setCommentAfter(String.valueOf((char) 0x7f)));
    assertThrows(IllegalArgumentException.class, () -> entry.setCommentAfter("\ud800"));

    // A surrogate pair is one character and is accepted.
    entry.setCommentAfter("😀");
    assertEquals("😀", entry.comments().get(0).lines().get(0));
  }

  @Test
  void shouldRejectSettingAnUnattachedComment() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.addComment("footer");
    TomlComment unattached = (TomlComment) table.elements().get(1);

    assertThrows(IllegalArgumentException.class, () -> table.entry("a").setComment(unattached));
  }

  @Test
  void shouldRejectNullLinesAndText() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");

    assertThrows(NullPointerException.class, () -> entry.setCommentAfter(null));
    assertThrows(NullPointerException.class, () -> entry.setCommentAbove((List<String>) null));
    assertThrows(NullPointerException.class, () -> entry.setCommentAbove((String[]) null));
  }

  @Test
  void shouldRemoveAboveAfterAndByPlacement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");
    entry.setCommentAbove("above");
    entry.setCommentAfter("after");

    entry.removeCommentAbove();
    assertEquals(1, entry.comments().size());
    assertEquals(TomlComment.Placement.AFTER, entry.comments().get(0).placement());

    entry.removeCommentAfter();
    assertTrue(entry.comments().isEmpty());

    entry.setCommentAbove("above again");
    entry.removeComment(TomlComment.Placement.ABOVE);
    assertTrue(entry.comments().isEmpty());
  }

  @Test
  void shouldNotFlagModificationWhenRemovingAnAbsentPlacement() {
    TomlParseResult table = parse("a = 1\n");
    MutableTomlKeyValue entry = table.entry("a");

    entry.removeCommentAbove();

    assertFalse(entry.isModified());
    assertFalse(table.isModified());
  }

  @Test
  void shouldCopyACommentBetweenEntriesKeepingTextAndDroppingPosition() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("source", 1L);
    table.set("target", 2L);
    table.entry("source").setCommentAbove("from source");

    TomlComment sourceComment = table.entry("source").comments().get(0);
    table.entry("target").setComment(sourceComment);

    TomlComment stored = table.entry("target").comments().get(0);
    assertEquals(TomlComment.Placement.ABOVE, stored.placement());
    assertEquals(List.of("from source"), stored.lines());
    assertNull(stored.position());
    assertTrue(table.isModified("target"));

    // The source entry, and the comment object it holds, are unchanged: no aliasing.
    assertEquals(List.of("from source"), table.entry("source").comments().get(0).lines());
    assertEquals(sourceComment.lines(), table.entry("source").comments().get(0).lines());
  }

  @Test
  void shouldCopyACommentAcrossDocumentsKeepingRawText() {
    TomlParseResult source = parse("x = 1 #tight\n");
    TomlParseResult target = parse("y = 2\n");

    TomlComment tight = source.comments("x").get(0);
    target.entry("y").setComment(tight);

    assertEquals(tight.lines(), target.comments("y").get(0).lines());
    assertNull(target.comments("y").get(0).position());
    assertTrue(target.isModified("y"));
    assertFalse(source.isModified());
  }

  @Test
  void shouldFlipIsModifiedOnEntryAndContainerWhenACommentChanges() {
    TomlParseResult table = parse("a = 1\n");
    MutableTomlKeyValue entry = table.entry("a");
    assertFalse(entry.isModified());
    assertFalse(table.isModified());

    entry.setCommentAbove("note");

    assertTrue(entry.isModified());
    assertTrue(table.isModified());
    assertTrue(table.isModified("a"));
  }

  @Test
  void shouldKeepCommentsModifiedSeparateFromValueModified() {
    TomlParseResult table = parse("a = 1\n");
    TomlPosition originalPosition = table.inputPositionOf("a");

    table.entry("a").setCommentAbove("note");

    // The comment changed, but the value itself, and where it was written, did not.
    assertEquals(1L, table.get("a"));
    assertEquals(originalPosition, table.inputPositionOf("a"));
    assertTrue(table.isModified("a"));
  }

  @Test
  void shouldReportUnmodifiedBeforeAnyEdit() {
    TomlParseResult table = parse("a = 1\n");
    assertFalse(table.entry("a").isModified());
  }

  @Test
  void shouldKeepAttachedCommentsAndReportModifiedOnlyThroughEntriesAfterCopy() {
    TomlParseResult original = parse("# above\na = 1 # after\n");

    MutableTomlTable copy = MutableTomlTable.copyOf(original);

    assertEquals(original.comments("a").get(0).text(), copy.comments("a").get(0).text());
    assertNull(copy.comments("a").get(0).position());
    assertTrue(copy.isModified("a"));
    assertTrue(copy.entry("a").isModified());
  }
}
