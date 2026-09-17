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
}
