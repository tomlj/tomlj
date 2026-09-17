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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link MutableTomlArray}, through the public API only.
 *
 * <p>
 * A parsed document is obtained by parsing a table directly, with {@link #parse(String)}: a {@link LinkedTomlTable} is
 * a {@link MutableTomlTable}, and the {@link ListTomlArray} values inside it are {@link MutableTomlArray}s.
 */
class MutableTomlArrayTest {

  private static LinkedTomlTable parse(String document) {
    return Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
  }

  // Unattached comments held directly in an array's elements(), filtered out from the entries.
  private static List<TomlComment> unattachedComments(TomlArray array) {
    List<TomlComment> comments = new ArrayList<>();
    for (TomlElement element : array.elements()) {
      if (element instanceof TomlComment comment) {
        comments.add(comment);
      }
    }
    return comments;
  }

  // A minimal, genuinely foreign TomlArray: not one of our own, so a value stored through it must be deep-copied
  // rather than shared.
  private static final class ForeignArray implements TomlArray {
    private final List<Object> values;

    ForeignArray(List<Object> values) {
      this.values = values;
    }

    @Override
    public int size() {
      return values.size();
    }

    @Override
    public boolean isEmpty() {
      return values.isEmpty();
    }

    @Override
    public TomlEntry entry(int index) {
      return new ForeignEntry(values.get(index));
    }

    @Override
    public List<TomlElement> elements() {
      List<TomlElement> elements = new ArrayList<>();
      for (Object value : values) {
        elements.add(new ForeignEntry(value));
      }
      return elements;
    }

    @Override
    public List<Object> toList() {
      return values;
    }
  }

  // A minimal TomlEntry for ForeignArray, with no position and no comments.
  private static final class ForeignEntry implements TomlEntry {
    private final Object value;

    ForeignEntry(Object value) {
      this.value = value;
    }

    @Override
    public TomlValue value() {
      return new ForeignValue(value);
    }

    @Override
    public List<TomlComment> comments() {
      return List.of();
    }

    @Override
    public TomlPosition position() {
      return null;
    }
  }

  // A minimal TomlValue for ForeignEntry#value(), with no position.
  private static final class ForeignValue implements TomlValue {
    private final Object value;

    ForeignValue(Object value) {
      this.value = value;
    }

    @Override
    public Object get() {
      return value;
    }

    @Override
    public TomlPosition position() {
      return null;
    }
  }

  @Test
  void shouldCreateAnEmptyUnmodifiedArray() {
    MutableTomlArray array = MutableTomlArray.create();
    assertTrue(array.isEmpty());
    assertFalse(array.isModified());
  }

  @Test
  void shouldCreateFromValues() {
    MutableTomlArray array = MutableTomlArray.of(1, "two", 3.0);
    assertEquals(List.of(1L, "two", 3.0), array.toList());
    assertTrue(array.isModified());
  }

  @Test
  void shouldCreateFromAnIterable() {
    Set<Integer> values = new LinkedHashSet<>(List.of(1, 2, 3));
    MutableTomlArray array = MutableTomlArray.copyOf(values);
    assertEquals(List.of(1L, 2L, 3L), array.toList());
  }

  @Test
  void shouldAppendAValue() {
    MutableTomlArray array = MutableTomlArray.create();
    array.add(1L);
    array.add(2L);
    assertEquals(List.of(1L, 2L), array.toList());
  }

  @Test
  void shouldReplaceInPlace() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);
    array.set(0, 99L);
    assertEquals(List.of(99L, 2L), array.toList());
  }

  @Test
  void shouldStoreWhatAValueReadFromAnEntryHolds() {
    TomlParseResult result = Toml.parse("x = 1\nl = [ 2 ]\n");
    assertFalse(result.hasErrors());
    MutableTomlArray array = MutableTomlArray.create();
    array.add(result.entry("x").value());
    array.add(result.entry("l").value());
    assertEquals(1L, array.get(0));
    assertEquals(List.of(2L), array.getArray(1).toList());
    assertNotSame(result.get("l"), array.get(1));
  }

  @Test
  void shouldRemoveAnElement() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L, 3L);
    Object removed = array.remove(1);
    assertEquals(2L, removed);
    assertEquals(List.of(1L, 3L), array.toList());
    assertTrue(array.isModified());
  }

  @Test
  void shouldRejectOutOfBoundsIndexOperations() {
    MutableTomlArray array = MutableTomlArray.create();
    assertThrows(IndexOutOfBoundsException.class, () -> array.get(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.set(0, 1L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.remove(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.isModified(0));
  }

  @Test
  void shouldRejectNullAndUnsupportedValues() {
    MutableTomlArray array = MutableTomlArray.create();
    assertThrows(NullPointerException.class, () -> array.add(null));
    assertThrows(IllegalArgumentException.class, () -> array.add(new Object()));
  }

  // A String with a lone high surrogate, an OffsetDateTime whose offset has a seconds part, and a LocalDateTime and a
  // LocalDate with a year TOML cannot write.
  private static Stream<Object> badValues() {
    return Stream
        .of(
            "bad\uD800value",
            OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutesSeconds(5, 0, 30)),
            LocalDateTime.of(10000, 1, 1, 0, 0),
            LocalDate.of(10000, 1, 1));
  }

  @ParameterizedTest
  @MethodSource("badValues")
  void shouldRejectValuesTomlCannotRepresentThroughAdd(Object value) {
    MutableTomlArray array = MutableTomlArray.create();
    assertThrows(IllegalArgumentException.class, () -> array.add(value));
  }

  @ParameterizedTest
  @MethodSource("badValues")
  void shouldRejectValuesTomlCannotRepresentThroughSet(Object value) {
    MutableTomlArray array = MutableTomlArray.of(1L);
    assertThrows(IllegalArgumentException.class, () -> array.set(0, value));
  }

  @ParameterizedTest
  @MethodSource("badValues")
  void shouldRejectValuesTomlCannotRepresentThroughInsertBefore(Object value) {
    MutableTomlArray array = MutableTomlArray.of(1L);
    assertThrows(IllegalArgumentException.class, () -> array.insertBefore(0, value));
  }

  @Test
  void shouldClearAnArrayLeavingUnattachedComments() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# footer\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.clear();

    assertTrue(array.isEmpty());
    assertEquals(1, unattachedComments(array).size());
    assertTrue(array.isModified());
  }

  @Test
  void shouldNotReportClearingAnEmptyArrayAsAModification() {
    MutableTomlArray array = MutableTomlArray.create();
    array.clear();
    assertFalse(array.isModified());
  }

  @Test
  void shouldStoreAnArrayAsADeepCopy() {
    LinkedTomlTable table = parse("a = [1]\n");
    ListTomlArray a = (ListTomlArray) table.get("a");
    MutableTomlArray root = MutableTomlArray.create();

    root.add(a);

    MutableTomlArray stored = root.getArray(0);
    assertNotSame(a, stored);
    assertNull(stored.inputPositionOf(0));

    // A later change to a is not seen through root.
    a.set(0, 2L);
    assertEquals(1L, root.getArray(0).get(0));

    // Editing through the stored copy is seen through root.
    stored.set(0, 3L);
    assertEquals(3L, root.getArray(0).get(0));
  }

  @Test
  void shouldStoreAnArrayIntoItself() {
    MutableTomlArray root = MutableTomlArray.create();
    root.add(1L);
    MutableTomlArray before = MutableTomlArray.copyOf(root);

    root.add(root);

    MutableTomlArray stored = root.getArray(1);
    assertNotSame(root, stored);
    assertTrue(Toml.equals(before, stored));
  }

  @Test
  void shouldDeepCopyAForeignArray() {
    TomlArray foreign = new ForeignArray(List.of(1L, 2L));
    MutableTomlArray array = MutableTomlArray.create();

    array.add(foreign);

    TomlArray stored = (TomlArray) array.get(0);
    assertNotSame(foreign, stored);
    assertEquals(List.of(1L, 2L), stored.toList());
  }

  @Test
  void shouldTrackModificationOnAParsedArray() {
    LinkedTomlTable table = parse("a = [1, 2]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    assertFalse(array.isModified());
    assertFalse(array.isModified(0));

    array.set(0, 99L);

    assertTrue(array.isModified());
    assertTrue(array.isModified(0));
    assertFalse(array.isModified(1));
  }

  @Test
  void shouldKeepPlaceAndCommentsWhenReplacingButHaveNoPosition() {
    LinkedTomlTable table = parse("a = [\n1 # note\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    List<TomlComment> comments = array.comments(0);
    TomlElement beforeReplace = array.elements().get(0);

    array.set(0, 42L);

    // An array entry's position is its value's, so the replacement, a value with no position of its own, leaves
    // inputPositionOf(0) null, even though the entry itself, with its place and its comments, is unchanged.
    assertNull(array.inputPositionOf(0));
    assertEquals(comments, array.comments(0));
    List<TomlElement> elements = array.elements();
    assertEquals(1, elements.size());
    assertSame(beforeReplace, elements.get(0));
    assertEquals(42L, ((TomlEntry) elements.get(0)).value().get());
  }

  @Test
  void shouldCopyAParsedArrayDroppingPositionsAndMarkingEveryEntryModified() {
    LinkedTomlTable table = parse("a = [\n1, # note\n2\n]\n");
    ListTomlArray original = (ListTomlArray) table.get("a");

    MutableTomlArray copy = MutableTomlArray.copyOf(original);

    assertTrue(Toml.equals(original, copy));
    assertEquals(original.comments(0).get(0).text(), copy.comments(0).get(0).text());
    assertNull(copy.inputPositionOf(0));
    assertNull(copy.inputPositionOf(1));
    assertTrue(copy.isModified(0));
    assertTrue(copy.isModified(1));
    assertTrue(copy.isModified());
    assertFalse(original.isModified());

    copy.set(0, 99L);
    assertEquals(1L, original.get(0));
  }

  @Test
  void shouldCopyCommentsWithoutPositions() {
    LinkedTomlTable table = parse("a = [\n  # above\n  1, # after\n  # trailing\n]\n");
    ListTomlArray original = (ListTomlArray) table.get("a");

    MutableTomlArray copy = MutableTomlArray.copyOf(original);

    List<TomlComment> attached = copy.comments(0);
    assertEquals(2, attached.size());
    for (int i = 0; i < 2; i++) {
      TomlComment source = original.comments(0).get(i);
      assertNotSame(source, attached.get(i));
      assertEquals(source.lines(), attached.get(i).lines());
      assertEquals(source.placement(), attached.get(i).placement());
      assertNull(attached.get(i).position());
    }
    List<TomlComment> unattached = unattachedComments(copy);
    assertEquals(1, unattached.size());
    assertNotSame(unattachedComments(original).get(0), unattached.get(0));
    assertEquals(List.of("trailing"), unattached.get(0).lines());
    assertEquals(TomlComment.Placement.UNATTACHED, unattached.get(0).placement());
    assertNull(unattached.get(0).position());
  }

  @Test
  void shouldReportFalseIsModifiedForAnEmptyCopy() {
    MutableTomlArray copy = MutableTomlArray.copyOf(MutableTomlArray.create());
    assertFalse(copy.isModified());
  }

  @Test
  void shouldNavigateThroughMutableViews() {
    LinkedTomlTable table = parse("a = [ {x = 1} ]\n");
    MutableTomlArray array = (MutableTomlArray) table.get("a");

    array.getTable(0).set("x", 99L);

    assertEquals(99L, table.getArray("a").getTable(0).get("x"));
  }

  @Test
  void shouldShowTheNewEntryAtTheEndOfElementsAfterAdd() {
    MutableTomlArray array = MutableTomlArray.create();
    array.add(1L);
    array.add(2L);

    List<TomlElement> elements = array.elements();
    assertEquals(2, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals(2L, ((TomlEntry) elements.get(1)).value().get());
  }

  @Test
  void shouldRemoveTheEntryFromElementsButKeepUnattachedCommentsInPlaceAfterRemove() {
    LinkedTomlTable table = parse("a = [\n# first\n\n1,\n\n# second\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.remove(0);

    List<TomlElement> elements = array.elements();
    assertEquals(2, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals("second", ((TomlComment) elements.get(1)).text());
  }

  @Test
  void shouldReturnAMutableEntryFromEntry() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);
    MutableTomlEntry entry = array.entry(0);
    assertEquals(1L, entry.value().get());
  }

  @Test
  void shouldSetCommentAboveAfterAndReadBackInOrderThroughTheShortcuts() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.setCommentAbove(0, "above");
    array.setCommentAfter(0, "after");

    List<TomlComment> comments = array.comments(0);
    assertEquals(2, comments.size());
    assertEquals(TomlComment.Placement.ABOVE, comments.get(0).placement());
    assertEquals(TomlComment.Placement.AFTER, comments.get(1).placement());
  }

  @Test
  void shouldSetCommentAboveWithAListThroughTheShortcut() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.setCommentAbove(0, List.of("line one", "line two"));

    assertEquals(List.of("line one", "line two"), array.comments(0).get(0).lines());
  }

  @Test
  void shouldSplitACommentLineAtANewlineWhereverTheCommentIsSet() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.setCommentAbove(0, "one\ntwo", "three");
    array.addComment("four\nfive");

    assertEquals(List.of("one", "two", "three"), array.comments(0).get(0).lines());
    assertEquals(List.of("four", "five"), unattachedComments(array).get(0).lines());
    assertThrows(IllegalArgumentException.class, () -> array.setCommentAfter(0, "one\ntwo"));
  }

  @Test
  void shouldSetCommentFromTextAndPlacementThroughTheShortcut() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.setComment(0, "x", TomlComment.Placement.AFTER);

    assertEquals(List.of("x"), array.comments(0).get(0).lines());
  }

  @Test
  void shouldRejectSetCommentFromTextAndPlacementOutOfBounds() {
    MutableTomlArray array = MutableTomlArray.create();

    assertThrows(IndexOutOfBoundsException.class, () -> array.setComment(0, "x", TomlComment.Placement.AFTER));
  }

  @Test
  void shouldSetAndCopyAttachedCommentThroughTheShortcut() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);
    array.setCommentAbove(0, "from source");

    TomlComment sourceComment = array.comments(0).get(0);
    array.setComment(1, sourceComment);

    List<TomlComment> targetComments = array.comments(1);
    assertEquals(1, targetComments.size());
    assertEquals(List.of("from source"), targetComments.get(0).lines());
    assertNull(targetComments.get(0).position());
    assertTrue(array.isModified(1));
  }

  @Test
  void shouldRemoveCommentAboveAfterAndByPlacementThroughTheShortcuts() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    array.setCommentAbove(0, "above");
    array.setCommentAfter(0, "after");

    array.removeCommentAbove(0);
    assertEquals(1, array.comments(0).size());
    assertEquals(TomlComment.Placement.AFTER, array.comments(0).get(0).placement());

    array.removeCommentAfter(0);
    assertTrue(array.comments(0).isEmpty());

    array.setCommentAbove(0, "above again");
    array.removeComment(0, TomlComment.Placement.ABOVE);
    assertTrue(array.comments(0).isEmpty());
  }

  @Test
  void shouldNotFlagModificationWhenShortcutRemovesAnAbsentComment() {
    LinkedTomlTable table = parse("a = [1]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.removeCommentAbove(0);

    assertFalse(array.isModified(0));
  }

  @Test
  void shouldRejectCommentShortcutsOutOfBounds() {
    MutableTomlArray array = MutableTomlArray.create();
    TomlComment comment = TomlComment.ofLines(List.of("x"), TomlComment.Placement.ABOVE);

    assertThrows(IndexOutOfBoundsException.class, () -> array.setCommentAbove(0, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.setCommentAfter(0, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.setComment(0, comment));
    assertThrows(IndexOutOfBoundsException.class, () -> array.removeCommentAbove(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.removeCommentAfter(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.removeComment(0, TomlComment.Placement.ABOVE));
  }

  @Test
  void shouldAddAndRemoveUnattachedComments() {
    MutableTomlArray array = MutableTomlArray.create();

    array.addComment("footer");
    assertEquals(1, unattachedComments(array).size());

    TomlComment comment = unattachedComments(array).get(0);
    assertTrue(array.removeComment(comment));
    assertTrue(unattachedComments(array).isEmpty());
    assertFalse(array.removeComment(comment));

    // The removed comment can be added again.
    array.addComment(comment);
    assertEquals(1, unattachedComments(array).size());
  }

  @Test
  void shouldAppendAnUnattachedCommentAfterTheLastElement() {
    MutableTomlArray array = MutableTomlArray.create();
    array.add(1L);

    array.addComment("footer");

    List<TomlElement> elements = array.elements();
    assertEquals(2, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals("footer", ((TomlComment) elements.get(1)).text());
  }

  @Test
  void shouldRejectAddingAnAttachedComment() {
    ListTomlArray array = (ListTomlArray) parse("a = [\n# above\n1 # after\n]\n").get("a");
    List<TomlComment> attached = array.comments(0);
    assertEquals(2, attached.size());
    assertThrows(IllegalArgumentException.class, () -> array.addComment(attached.get(0)));
    assertThrows(IllegalArgumentException.class, () -> array.addComment(attached.get(1)));

    assertTrue(unattachedComments(array).isEmpty());
    assertFalse(array.isModified());
  }

  @Test
  void shouldReturnFalseWhenRemovingAnAttachedCommentAsUnattached() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    array.setCommentAbove(0, "note");

    TomlComment attached = array.comments(0).get(0);

    assertFalse(array.removeComment(attached));
  }

  @Test
  void shouldInsertAtTheStartMiddleAndEndOfAnArray() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);

    array.insertBefore(0, 0L);
    array.insertAfter(1, 9L);
    array.insertAfter(array.size() - 1, 3L);

    assertEquals(List.of(0L, 1L, 9L, 2L, 3L), array.toList());
  }

  @Test
  void shouldInsertBeforeAnEntryThatAnUnattachedCommentPrecedes() {
    // A comment run needs a blank line after it, before the next entry, to be unattached rather than that entry's
    // ABOVE run; see Comments' class documentation.
    LinkedTomlTable table = parse("a = [\n  1,\n  # c\n\n  2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlComment comment = (TomlComment) array.elements().get(1);
    Entry.Indexed entryForTwo = array.entry(1);
    TomlPosition positionOfTwo = array.inputPositionOf(1);

    array.insertBefore(1, 9L);

    assertEquals(List.of(1L, 9L, 2L), array.toList());
    assertEquals(9L, array.get(1));
    List<TomlElement> elements = array.elements();
    assertEquals(4, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertSame(comment, elements.get(1));
    assertEquals(9L, ((TomlEntry) elements.get(2)).value().get());
    assertSame(entryForTwo, elements.get(3));
    assertSame(entryForTwo, array.entry(2));
    assertNull(array.inputPositionOf(1));
    assertEquals(positionOfTwo, array.inputPositionOf(2));
  }

  @Test
  void shouldMarkOnlyTheNewEntryModifiedWhenInsertingIntoAParsedArray() {
    LinkedTomlTable table = parse("a = [1, 2]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.insertBefore(1, 9L);

    assertTrue(array.isModified(1));
    assertFalse(array.isModified(0));
    assertFalse(array.isModified(2));
    assertTrue(array.isModified());
  }

  @Test
  void shouldStoreATableValueAsADeepCopyWhenInsertingIntoAnArray() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    MutableTomlTable original = MutableTomlTable.create();
    original.set("x", 1L);

    array.insertBefore(0, original);

    original.set("x", 2L);
    assertEquals(1L, array.getTable(0).get("x"));
  }

  @Test
  void shouldRejectOutOfBoundsIndexWhenInsertingAtAnIndex() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);

    assertThrows(IndexOutOfBoundsException.class, () -> array.insertBefore(-1, 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertBefore(array.size(), 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertBefore(array.size() + 1, 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertAfter(-1, 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertAfter(array.size(), 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertAfter(array.size() + 1, 9L));

    assertEquals(List.of(1L, 2L), array.toList());

    MutableTomlArray empty = MutableTomlArray.create();
    assertThrows(IndexOutOfBoundsException.class, () -> empty.insertBefore(0, 9L));
    assertThrows(IndexOutOfBoundsException.class, () -> empty.insertAfter(0, 9L));
  }

  @Test
  void shouldRejectNullAndUnconvertibleValuesWhenInsertingIntoAnArray() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);

    assertThrows(NullPointerException.class, () -> array.insertBefore(1, null));
    assertThrows(IllegalArgumentException.class, () -> array.insertBefore(1, new Object()));

    assertEquals(List.of(1L, 2L), array.toList());
  }

  @Test
  void shouldInsertCommentsAtTheStartMiddleAndEndOfAnArray() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);

    array.insertCommentBefore(0, "first");
    array.insertCommentBefore(1, List.of("middle"));
    array.insertCommentAfter(array.size() - 1, TomlComment.ofLines(List.of("last"), TomlComment.Placement.UNATTACHED));

    List<TomlElement> elements = array.elements();
    assertEquals(5, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals(1L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("middle", ((TomlComment) elements.get(2)).text());
    assertEquals(2L, ((TomlEntry) elements.get(3)).value().get());
    assertEquals("last", ((TomlComment) elements.get(4)).text());
  }

  @Test
  void shouldSplitAnInsertedCommentLineAtANewline() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.insertCommentBefore(0, "one\ntwo", "three");
    array.insertCommentAfter(0, List.of("four\nfive"));

    List<TomlElement> elements = array.elements();
    assertEquals(List.of("one", "two", "three"), ((TomlComment) elements.get(0)).lines());
    assertEquals(List.of("four", "five"), ((TomlComment) elements.get(2)).lines());
  }

  @Test
  void shouldInsertAParsedUnattachedCommentBeforeAnEntryWithoutItsPosition() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# note\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlComment parsed = (TomlComment) array.elements().get(1);
    assertNotNull(parsed.position());

    array.insertCommentBefore(0, parsed);

    TomlComment inserted = (TomlComment) array.elements().get(0);
    assertEquals(3, array.elements().size());
    assertEquals(TomlComment.Placement.UNATTACHED, inserted.placement());
    assertNull(inserted.position());
    assertEquals(parsed.lines(), inserted.lines());
    assertNotSame(parsed, inserted);
    assertTrue(array.isModified());
  }

  @Test
  void shouldFindACommentInsertedBeforeAnEntryWithRemoveComment() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.insertCommentBefore(0, "note");

    TomlComment inserted = (TomlComment) array.elements().get(0);
    assertTrue(array.removeComment(inserted));
  }

  @Test
  void shouldRejectOutOfBoundsIndexEmptyLinesAndNullCommentWhenInsertingACommentAtAnIndex() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentBefore(-1, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentBefore(array.size(), "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentBefore(array.size() + 1, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentAfter(-1, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentAfter(array.size(), "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> array.insertCommentAfter(array.size() + 1, "x"));
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(0, List.of()));
    assertThrows(NullPointerException.class, () -> array.insertCommentBefore(0, (TomlComment) null));

    MutableTomlArray empty = MutableTomlArray.create();
    assertThrows(IndexOutOfBoundsException.class, () -> empty.insertCommentBefore(0, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> empty.insertCommentAfter(0, "x"));
  }

  @Test
  void shouldInsertBeforeALeadingUnattachedComment() {
    LinkedTomlTable table = parse("a = [\n# lead\n\n1,\n2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlElement lead = array.elements().get(0);

    array.insertBefore(lead, 0L);

    List<TomlElement> elements = array.elements();
    assertEquals(4, elements.size());
    assertEquals(0L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals("lead", ((TomlComment) elements.get(1)).text());
    assertEquals(1L, ((TomlEntry) elements.get(2)).value().get());
    assertEquals(2L, ((TomlEntry) elements.get(3)).value().get());
    assertEquals(3, array.size());
    assertEquals(List.of(0L, 1L, 2L), array.toList());
    assertEquals(0L, array.get(0));
    assertEquals(1L, array.get(1));
    assertEquals(2L, array.get(2));
  }

  @Test
  void shouldInsertAfterTheLastEntryWhenATrailingCommentFollowsIt() {
    LinkedTomlTable table = parse("a = [\n1,\n2,\n\n# trail\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    Entry.Indexed lastEntry = array.entry(1);

    array.insertAfter(lastEntry, 9L);

    List<TomlElement> elements = array.elements();
    assertEquals(4, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals(2L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals(9L, ((TomlEntry) elements.get(2)).value().get());
    assertEquals("trail", ((TomlComment) elements.get(3)).text());
    assertEquals(3, array.size());
    assertEquals(List.of(1L, 2L, 9L), array.toList());
    assertEquals(1L, array.get(0));
    assertEquals(2L, array.get(1));
    assertEquals(9L, array.get(2));
  }

  @Test
  void shouldInsertBetweenTwoConsecutiveUnattachedComments() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# one\n\n# two\n\n2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlElement two = array.elements().get(2);

    array.insertBefore(two, 9L);

    List<TomlElement> elements = array.elements();
    assertEquals(5, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals("one", ((TomlComment) elements.get(1)).text());
    assertEquals(9L, ((TomlEntry) elements.get(2)).value().get());
    assertEquals("two", ((TomlComment) elements.get(3)).text());
    assertEquals(2L, ((TomlEntry) elements.get(4)).value().get());
    assertEquals(List.of(1L, 9L, 2L), array.toList());
    assertEquals(1L, array.get(0));
    assertEquals(9L, array.get(1));
    assertEquals(2L, array.get(2));
  }

  @Test
  void shouldInsertACommentBeforeAndAfterALeadingUnattachedComment() {
    LinkedTomlTable table = parse("a = [\n# lead\n\n1,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlElement lead = array.elements().get(0);

    array.insertCommentBefore(lead, "before lead");
    array.insertCommentAfter(lead, "after lead");

    List<TomlElement> elements = array.elements();
    assertEquals(4, elements.size());
    assertEquals("before lead", ((TomlComment) elements.get(0)).text());
    assertEquals("lead", ((TomlComment) elements.get(1)).text());
    assertEquals("after lead", ((TomlComment) elements.get(2)).text());
    assertEquals(1L, ((TomlEntry) elements.get(3)).value().get());
  }

  @Test
  void shouldInsertACommentBeforeAndAfterTheLastEntryWhenATrailingCommentFollowsIt() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# trail\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    Entry.Indexed lastEntry = array.entry(0);

    array.insertCommentAfter(lastEntry, "right after");
    array.insertCommentBefore(lastEntry, "right before");

    List<TomlElement> elements = array.elements();
    assertEquals(4, elements.size());
    assertEquals("right before", ((TomlComment) elements.get(0)).text());
    assertEquals(1L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("right after", ((TomlComment) elements.get(2)).text());
    assertEquals("trail", ((TomlComment) elements.get(3)).text());
  }

  @Test
  void shouldInsertACommentBetweenTwoConsecutiveUnattachedCommentsBeforeAndAfterEach() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# one\n\n# two\n\n2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    TomlElement one = array.elements().get(1);
    TomlElement two = array.elements().get(2);

    array.insertCommentAfter(one, "after one");
    array.insertCommentBefore(two, "before two");

    List<TomlElement> elements = array.elements();
    assertEquals(6, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals("one", ((TomlComment) elements.get(1)).text());
    assertEquals("after one", ((TomlComment) elements.get(2)).text());
    assertEquals("before two", ((TomlComment) elements.get(3)).text());
    assertEquals("two", ((TomlComment) elements.get(4)).text());
    assertEquals(2L, ((TomlEntry) elements.get(5)).value().get());
  }

  @Test
  void shouldInsertBeforeAndAfterAnEntryThroughItsElement() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);

    array.insertBefore(array.entry(1), 9L);
    array.insertAfter(array.elements().get(0), 8L);

    assertEquals(List.of(1L, 8L, 9L, 2L), array.toList());
  }

  @Test
  void shouldRejectAForeignAnchorFromANestedArrayOrACopy() {
    LinkedTomlTable table = parse("a = [ [1], 2 ]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    ListTomlArray nested = (ListTomlArray) array.get(0);
    TomlElement nestedAnchor = nested.elements().get(0);

    NoSuchElementException e1 = assertThrows(NoSuchElementException.class, () -> array.insertBefore(nestedAnchor, 9L));
    assertEquals("anchor is not an element of this array", e1.getMessage());

    MutableTomlArray copy = MutableTomlArray.copyOf(array);
    TomlElement copyAnchor = copy.elements().get(0);
    assertThrows(NoSuchElementException.class, () -> array.insertBefore(copyAnchor, 9L));
  }

  @Test
  void shouldRejectANullAnchorOrNullValueThroughTheElementForm() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    TomlElement anchor = array.elements().get(0);

    assertThrows(NullPointerException.class, () -> array.insertBefore(null, 1L));
    assertThrows(NullPointerException.class, () -> array.insertBefore(anchor, null));
  }

  @Test
  void shouldRejectInsertingAnAttachedComment() {
    ListTomlArray array = (ListTomlArray) parse("a = [\n# above\n1\n]\n").get("a");
    TomlComment attached = array.comments(0).get(0);
    TomlElement anchor = array.entry(0);

    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(0, attached));
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentAfter(0, attached));
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(anchor, attached));
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentAfter(anchor, attached));

    assertEquals(1, array.elements().size());
    assertFalse(array.isModified());
  }

  @Test
  void shouldRejectANullAnchorOrNullCommentThroughTheElementForm() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    TomlElement anchor = array.elements().get(0);

    assertThrows(NullPointerException.class, () -> array.insertCommentBefore(null, "x"));
    assertThrows(NullPointerException.class, () -> array.insertCommentBefore(anchor, (TomlComment) null));
  }

  @Test
  void shouldMarkOnlyTheInsertedEntryModifiedThroughTheElementFormAndNotShiftedEntries() {
    LinkedTomlTable table = parse("a = [1, 2]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    Entry.Indexed anchor = array.entry(0);

    array.insertBefore(anchor, 9L);

    assertTrue(array.isModified(0));
    assertFalse(array.isModified(1));
    assertFalse(array.isModified(2));
    assertTrue(array.isModified());
  }

  @Test
  void shouldMarkTheArrayModifiedWhenInsertingACommentThroughTheElementForm() {
    LinkedTomlTable table = parse("a = [1]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    assertFalse(array.isModified());

    array.insertCommentBefore(array.entry(0), "note");

    assertTrue(array.isModified());
    assertFalse(array.isModified(0));
  }

  @Test
  void shouldInsertCommentTextFormsThroughTheElementAnchorAndValidateLines() {
    MutableTomlArray array = MutableTomlArray.of(1L);
    TomlElement anchor = array.elements().get(0);

    array.insertCommentBefore(anchor, "above");
    array.insertCommentAfter(anchor, List.of("after"));

    List<TomlElement> elements = array.elements();
    assertEquals(3, elements.size());
    assertEquals("above", ((TomlComment) elements.get(0)).text());
    assertEquals(1L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("after", ((TomlComment) elements.get(2)).text());

    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(anchor, List.of()));
    String badLine = "bad" + (char) 1 + "line";
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(anchor, badLine));
  }

  @Test
  void shouldInsertBeforeAndAfterEntriesAroundAnUnattachedComment() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# note\n\n2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    // insertBefore(1, ...) first, so that index 0 still names the entry for 1 when insertAfter(0, ...) runs: an
    // insertAfter(0, ...) done first would place the new entry at index 1, ahead of insertBefore(1, ...)'s target.
    array.insertBefore(1, 8L);
    array.insertAfter(0, 9L);

    List<TomlElement> elements = array.elements();
    assertEquals(5, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals(9L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("note", ((TomlComment) elements.get(2)).text());
    assertEquals(8L, ((TomlEntry) elements.get(3)).value().get());
    assertEquals(2L, ((TomlEntry) elements.get(4)).value().get());
    assertEquals(List.of(1L, 9L, 8L, 2L), array.toList());
    assertEquals(1L, array.get(0));
    assertEquals(9L, array.get(1));
    assertEquals(8L, array.get(2));
    assertEquals(2L, array.get(3));
  }

  @Test
  void shouldInsertAfterTheLastEntryBeforeATrailingCommentUnlikeAdd() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# trail\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.insertAfter(array.size() - 1, 9L);

    List<TomlElement> elements = array.elements();
    assertEquals(3, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals(9L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("trail", ((TomlComment) elements.get(2)).text());

    LinkedTomlTable freshTable = parse("a = [\n1,\n\n# trail\n]\n");
    ListTomlArray fresh = (ListTomlArray) freshTable.get("a");

    fresh.add(9L);

    List<TomlElement> freshElements = fresh.elements();
    assertEquals(3, freshElements.size());
    assertEquals(1L, ((TomlEntry) freshElements.get(0)).value().get());
    assertEquals("trail", ((TomlComment) freshElements.get(1)).text());
    assertEquals(9L, ((TomlEntry) freshElements.get(2)).value().get());
  }

  @Test
  void shouldInsertCommentsBeforeAndAfterEntriesAroundAnUnattachedComment() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# note\n\n2,\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.insertCommentAfter(0, "x");
    array.insertCommentBefore(1, "y");

    List<TomlElement> elements = array.elements();
    assertEquals(5, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals("x", ((TomlComment) elements.get(1)).text());
    assertEquals("note", ((TomlComment) elements.get(2)).text());
    assertEquals("y", ((TomlComment) elements.get(3)).text());
    assertEquals(2L, ((TomlEntry) elements.get(4)).value().get());
  }

  @Test
  void shouldMarkOnlyTheInsertedEntryModifiedThroughInsertAfterAndNotShiftedEntries() {
    LinkedTomlTable table = parse("a = [1, 2]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.insertAfter(0, 9L);

    assertFalse(array.isModified(0));
    assertTrue(array.isModified(1));
    assertFalse(array.isModified(2));
    assertTrue(array.isModified());
  }

  @Test
  void shouldMarkTheArrayModifiedWhenInsertingACommentThroughInsertCommentAfter() {
    LinkedTomlTable table = parse("a = [1]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    assertFalse(array.isModified());

    array.insertCommentAfter(0, "note");

    assertTrue(array.isModified());
    assertFalse(array.isModified(0));
  }

  @Test
  void shouldInsertCommentTextFormsAtAnIndexForBothDirectionsAndValidateLines() {
    MutableTomlArray array = MutableTomlArray.of(1L);

    array.insertCommentBefore(0, "above");
    array.insertCommentAfter(0, List.of("after"));

    List<TomlElement> elements = array.elements();
    assertEquals(3, elements.size());
    assertEquals("above", ((TomlComment) elements.get(0)).text());
    assertEquals(1L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("after", ((TomlComment) elements.get(2)).text());

    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(0, List.of()));
    String badLine = "bad" + (char) 1 + "line";
    assertThrows(IllegalArgumentException.class, () -> array.insertCommentBefore(0, badLine));
  }
}
