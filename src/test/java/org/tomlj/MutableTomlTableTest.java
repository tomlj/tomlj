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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MutableTomlTable}, through the public API only.
 *
 * <p>
 * A parsed document is obtained by parsing a table directly, with {@link #parse(String)}: a {@link LinkedTomlTable} is
 * a {@link MutableTomlTable}.
 */
class MutableTomlTableTest {

  private static LinkedTomlTable parse(String document) {
    return Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
  }

  // Unattached comments held directly in a table's elements(), filtered out from the entries.
  private static List<TomlComment> unattachedComments(TomlTable table) {
    List<TomlComment> comments = new ArrayList<>();
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment comment) {
        comments.add(comment);
      }
    }
    return comments;
  }

  @Test
  void shouldCreateAnEmptyUnmodifiedTable() {
    MutableTomlTable table = MutableTomlTable.create();
    assertTrue(table.isEmpty());
    assertFalse(table.isModified());
  }

  @Test
  void shouldCreateIntermediateTablesWhenSetting() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a.b.c", 1L);
    assertEquals(1L, table.get("a.b.c"));
    assertTrue(table.getTable("a").isTable("b"));
  }

  @Test
  void shouldWidenNumericTypes() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("i", 1);
    table.set("s", (short) 2);
    table.set("b", (byte) 3);
    table.set("f", 4.5f);
    assertEquals(1L, table.get("i"));
    assertEquals(2L, table.get("s"));
    assertEquals(3L, table.get("b"));
    assertEquals(4.5, table.get("f"));
  }

  @Test
  void shouldConvertAMapToATable() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("x", 1);
    map.put("y", "two");
    MutableTomlTable table = MutableTomlTable.create();
    table.set("m", map);
    assertEquals(1L, table.get("m.x"));
    assertEquals("two", table.get("m.y"));
    assertNotSame(map, table.get("m"));
  }

  @Test
  void shouldConvertACollectionToAnArray() {
    List<Object> list = Arrays.asList(1, 2, 3);
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", list);
    assertEquals(Arrays.asList(1L, 2L, 3L), table.getArray("a").toList());
  }

  @Test
  void shouldRejectNull() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(NullPointerException.class, () -> table.set("a", null));
  }

  @Test
  void shouldRejectAnUnsupportedType() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set("a", new Object()));
  }

  @Test
  void shouldRejectAnEmptyPath() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set(List.of(), 1L));
  }

  @Test
  void shouldRejectANullPathElementLeavingTheTableUnchanged() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    assertThrows(NullPointerException.class, () -> table.set(Arrays.asList("b", null), 1L));

    assertEquals(Set.of("a"), table.keySet());
    assertFalse(table.isModified("b"));
  }

  @Test
  void shouldRejectANullKeyInAMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(null, 1L);
    assertThrows(NullPointerException.class, () -> MutableTomlTable.copyOf(map));
  }

  @Test
  void shouldRejectANonStringKeyInARawMap() {
    Map<Object, Object> map = new LinkedHashMap<>();
    map.put(1, "one");
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set("m", map));
  }

  @Test
  void shouldRejectAPathThatIteratesOverItself() {
    MutableTomlTable table = MutableTomlTable.create();
    Path path = Paths.get(".", "a", "b");
    assertThrows(IllegalArgumentException.class, () -> table.set("p", path));
  }

  @Test
  void shouldKeepPositionOrderAndCommentsWhenReplacing() {
    LinkedTomlTable table = parse("# above\na = 1 # after\nb = 2\n");
    TomlPosition originalPosition = table.inputPositionOf("a");
    List<TomlComment> originalComments = table.comments("a");
    List<String> keysBefore = new ArrayList<>(table.keySet());

    table.set("a", 99L);

    assertEquals(99L, table.get("a"));
    assertEquals(originalPosition, table.inputPositionOf("a"));
    assertEquals(originalComments, table.comments("a"));
    assertEquals(keysBefore, new ArrayList<>(table.keySet()));
    assertTrue(table.isModified("a"));
  }

  @Test
  void shouldRejectSettingThroughANonTable() {
    LinkedTomlTable table = parse("a = 1\n");
    TomlInvalidTypeException e = assertThrows(TomlInvalidTypeException.class, () -> table.set("a.b", 1L));
    assertEquals("Value of 'a' is a integer", e.getMessage());
  }

  @Test
  void shouldTreatADottedKeyAndALiteralPathTheSame() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a.b", 1L);
    assertEquals(1L, table.get(List.of("a", "b")));
    table.set(List.of("c", "d"), 2L);
    assertEquals(2L, table.get("c.d"));
  }

  @Test
  void shouldGetOrCreateATableAndReuseIt() {
    MutableTomlTable table = MutableTomlTable.create();
    MutableTomlTable a = table.getOrCreateTable("a");
    assertSame(a, table.getOrCreateTable("a"));
    a.set("x", 1L);
    assertEquals(1L, table.get("a.x"));
  }

  @Test
  void shouldReturnThisForGetOrCreateTableWithEmptyPath() {
    MutableTomlTable table = MutableTomlTable.create();
    assertSame(table, table.getOrCreateTable(List.of()));
  }

  @Test
  void shouldGetOrCreateAnArrayAndReuseIt() {
    MutableTomlTable table = MutableTomlTable.create();
    MutableTomlArray a = table.getOrCreateArray("a");
    assertSame(a, table.getOrCreateArray("a"));
    a.add(1L);
    assertEquals(1L, table.getArray("a").get(0));
  }

  @Test
  void shouldRejectGetOrCreateArrayWithEmptyPath() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.getOrCreateArray(List.of()));
  }

  @Test
  void shouldRejectGetOrCreateTableOnAnExistingNonTable() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    assertThrows(TomlInvalidTypeException.class, () -> table.getOrCreateTable("a"));
  }

  @Test
  void shouldRejectGetOrCreateArrayOnAnExistingNonArray() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    TomlInvalidTypeException e = assertThrows(TomlInvalidTypeException.class, () -> table.getOrCreateArray("a"));
    assertEquals("Value of 'a' is a integer", e.getMessage());
  }

  @Test
  void shouldRemoveAnEntryTakingItsAttachedCommentsButKeepingUnattachedOnes() {
    LinkedTomlTable table = parse("# above\na = 1 # after\n\n# footer\n");
    assertEquals(1, unattachedComments(table).size());

    Object removed = table.remove("a");

    assertEquals(1L, removed);
    assertNull(table.get("a"));
    assertEquals(1, unattachedComments(table).size());
    assertTrue(table.isModified());
  }

  @Test
  void shouldReturnNullWhenRemovingAnAbsentKey() {
    MutableTomlTable table = MutableTomlTable.create();
    assertNull(table.remove("a"));
    assertFalse(table.isModified());
  }

  @Test
  void shouldReturnNullWhenRemovingThroughAMissingTable() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    assertNull(table.remove(List.of("missing", "x")));
  }

  @Test
  void shouldRejectRemovingThroughANonTable() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    TomlInvalidTypeException e = assertThrows(TomlInvalidTypeException.class, () -> table.remove("a.x"));
    assertEquals("Value of 'a' is a integer", e.getMessage());
  }

  @Test
  void shouldClearATableLeavingUnattachedComments() {
    LinkedTomlTable table = parse("a = 1\n\n# footer\n");

    table.clear();

    assertTrue(table.isEmpty());
    assertEquals(1, unattachedComments(table).size());
    assertTrue(table.isModified());
  }

  @Test
  void shouldNotReportClearingAnEmptyTableAsAModification() {
    MutableTomlTable table = MutableTomlTable.create();
    table.clear();
    assertFalse(table.isModified());
  }

  @Test
  void shouldStoreATableAsADeepCopy() {
    LinkedTomlTable t = parse("x = 1\n");
    MutableTomlTable root = MutableTomlTable.create();

    root.set("a", t);

    MutableTomlTable stored = root.getTable("a");
    assertNotSame(t, stored);
    assertNull(stored.inputPositionOf("x"));

    // A later change to t is not seen through root.
    t.set("x", 2L);
    assertEquals(1L, root.get("a.x"));

    // Editing through the stored copy is seen through root.
    stored.set("x", 3L);
    assertEquals(3L, root.get("a.x"));
  }

  @Test
  void shouldCopyAParsedDocumentWhenStored() {
    LinkedTomlTable original = parse("# above\na = 1 # after\n[b]\nc = 2\n");
    TomlPosition originalPosition = original.inputPositionOf("a");
    List<TomlComment> originalComments = original.comments("a");

    MutableTomlTable root = MutableTomlTable.create();
    root.set("t", original);

    assertFalse(original.isModified());
    assertEquals(originalPosition, original.inputPositionOf("a"));
    assertEquals(originalComments, original.comments("a"));

    MutableTomlTable stored = root.getTable("t");
    assertTrue(stored.isModified("a"));
    assertTrue(stored.isModified("b"));
    assertTrue(stored.isModified("b.c"));
    assertNull(stored.inputPositionOf("a"));
  }

  @Test
  void shouldStoreATableIntoItself() {
    MutableTomlTable root = MutableTomlTable.create();
    root.set("x", 1L);
    MutableTomlTable before = MutableTomlTable.copyOf(root);

    root.set("a.b", root);

    MutableTomlTable stored = root.getTable("a.b");
    assertNotSame(root, stored);
    assertTrue(Toml.equals(before, stored));
  }

  @Test
  void shouldDeepCopyAForeignTable() {
    TomlTable foreign = new ForeignTable(Map.of("x", 1L));
    MutableTomlTable table = MutableTomlTable.create();

    table.set("a", foreign);

    assertEquals(1L, table.get("a.x"));
    assertNotSame(foreign, table.get("a"));
  }

  @Test
  void shouldTrackModificationOnAParsedDocument() {
    LinkedTomlTable table = parse("a = 1\n[b]\nc = 2\n");
    assertFalse(table.isModified());
    assertFalse(table.isModified("a"));
    assertFalse(table.isModified("b"));
    assertFalse(table.isModified("b.c"));

    table.set("b.c", 3L);

    assertTrue(table.isModified());
    assertTrue(table.isModified("b"));
    assertTrue(table.isModified("b.c"));
    assertFalse(table.isModified("a"));
    assertFalse(table.isModified("nope"));
  }

  @Test
  void shouldKeepPositionOnReplaceAndHaveNoneOnAdd() {
    LinkedTomlTable table = parse("a = 1\n");
    TomlPosition original = table.inputPositionOf("a");

    table.set("a", 2L);
    assertEquals(original, table.inputPositionOf("a"));

    table.set("b", 3L);
    assertNull(table.inputPositionOf("b"));
  }

  @Test
  void shouldCopyAParsedTableDroppingPositionsAndMarkingEveryEntryModified() {
    LinkedTomlTable original = parse("# above\na = 1 # after\n[b]\nc = 2\n");

    MutableTomlTable copy = MutableTomlTable.copyOf(original);

    assertTrue(Toml.equals(original, copy));
    assertEquals(original.comments("a").get(0).text(), copy.comments("a").get(0).text());
    assertNull(copy.inputPositionOf("a"));
    assertNull(copy.inputPositionOf("b"));
    assertNull(copy.inputPositionOf("b.c"));
    assertTrue(copy.isModified("a"));
    assertTrue(copy.isModified("b"));
    assertTrue(copy.isModified("b.c"));
    assertTrue(copy.isModified());
    assertFalse(original.isModified());

    copy.set("a", 99L);
    assertEquals(1L, original.get("a"));
  }

  @Test
  void shouldCopyCommentsWithoutPositions() {
    LinkedTomlTable original = parse("# above\na = 1 # after\n\n# footer\n");

    MutableTomlTable copy = MutableTomlTable.copyOf(original);

    List<TomlComment> attached = copy.comments("a");
    assertEquals(2, attached.size());
    for (int i = 0; i < 2; i++) {
      TomlComment source = original.comments("a").get(i);
      assertNotSame(source, attached.get(i));
      assertEquals(source.lines(), attached.get(i).lines());
      assertEquals(source.placement(), attached.get(i).placement());
      assertNull(attached.get(i).position());
    }
    List<TomlComment> unattached = unattachedComments(copy);
    assertEquals(1, unattached.size());
    assertNotSame(unattachedComments(original).get(0), unattached.get(0));
    assertEquals(List.of("footer"), unattached.get(0).lines());
    assertNull(unattached.get(0).placement());
    assertNull(unattached.get(0).position());
  }

  @Test
  void shouldReportFalseIsModifiedForAnEmptyCopy() {
    MutableTomlTable copy = MutableTomlTable.copyOf(MutableTomlTable.create());
    assertFalse(copy.isModified());
  }

  @Test
  void shouldCreateATableFromAMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("x", 1);
    map.put("y", "two");

    MutableTomlTable table = MutableTomlTable.copyOf(map);

    assertEquals(1L, table.get("x"));
    assertEquals("two", table.get("y"));
    assertTrue(table.isModified());
    assertTrue(table.isModified("x"));
    assertNull(table.inputPositionOf("x"));
  }

  @Test
  void shouldNavigateThroughMutableViews() {
    LinkedTomlTable table = parse("[x]\ny = [ {z = 1} ]\n");

    table.getTable("x").getArray("y").getTable(0).set("z", 99L);

    TomlArray y = table.getArray("x.y");
    assertEquals(99L, y.getTable(0).get("z"));
  }

  @Test
  void shouldShowTheNewEntryAtTheEndOfElementsAfterAdd() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("b", ((TomlKeyValue) elements.get(1)).key());
  }

  @Test
  void shouldKeepTheSameElementInPlaceAfterReplace() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);
    TomlElement beforeReplace = table.elements().get(0);

    table.set("a", 99L);

    List<TomlElement> elements = table.elements();
    assertSame(beforeReplace, elements.get(0));
    assertEquals(99L, ((TomlKeyValue) elements.get(0)).value().get());
    assertEquals("b", ((TomlKeyValue) elements.get(1)).key());
  }

  @Test
  void shouldRemoveTheEntryFromElementsButKeepUnattachedCommentsInPlaceAfterRemove() {
    LinkedTomlTable table = parse("# first\n\na = 1\n\n# second\n");

    table.remove("a");

    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals("second", ((TomlComment) elements.get(1)).text());
  }

  @Test
  void shouldRemoveOnlyEntriesFromElementsButKeepUnattachedCommentsInPlaceAfterClear() {
    LinkedTomlTable table = parse("# first\n\na = 1\nb = 2\n\n# second\n");

    table.clear();

    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals("second", ((TomlComment) elements.get(1)).text());
  }

  @Test
  void shouldEditAResultWithErrorsAndRoundTripItThroughToml() {
    // "b = @" cannot start a value, so it is the one error; a and c.d still parse and are set.
    AccumulatingErrorListener errorListener = new AccumulatingErrorListener();
    LinkedTomlTable result = Parser
        .parseTable(CharStreams.fromString("a = 1\nb = @\nc.d = 3\n"), TomlParseOptions.defaults(), errorListener);
    assertEquals(1, errorListener.errors().size());

    result.set("c.e", 4L);
    result.remove("a");
    result.getOrCreateTable("f");

    assertEquals(1, errorListener.errors().size());
    assertTrue(result.isModified());

    AccumulatingErrorListener reparsedErrorListener = new AccumulatingErrorListener();
    LinkedTomlTable reparsed =
        Parser.parseTable(CharStreams.fromString(result.toToml()), TomlParseOptions.defaults(), reparsedErrorListener);

    assertTrue(reparsedErrorListener.errors().isEmpty());
    assertFalse(reparsed.isModified());
    assertTrue(Toml.equals(reparsed, result));
  }

  @Test
  void shouldReturnAMutableKeyValueFromEntry() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlKeyValue entry = table.entry("a");
    assertEquals("a", entry.key());
    assertEquals(1L, entry.value().get());
  }

  @Test
  void shouldReturnNullFromEntryForAnUnsetKeyOrTheEmptyPath() {
    MutableTomlTable table = MutableTomlTable.create();
    assertNull(table.entry("nope"));
    assertNull(table.entry(List.of()));
  }

  @Test
  void shouldSetCommentAboveThroughTheDottedKeyAndPathShortcuts() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);
    table.set("c", 3L);
    table.set("d", 4L);

    table.setCommentAbove("a", "line one", "line two");
    table.setCommentAbove("b", List.of("above b"));
    table.setCommentAbove(List.of("c"), "above c");
    table.setCommentAbove(List.of("d"), List.of("above d"));

    assertEquals(List.of("line one", "line two"), table.comments("a").get(0).lines());
    assertEquals(List.of("above b"), table.comments("b").get(0).lines());
    assertEquals(List.of("above c"), table.comments("c").get(0).lines());
    assertEquals(List.of("above d"), table.comments("d").get(0).lines());
  }

  @Test
  void shouldSetCommentAfterAndReadBackAboveThenAfterInOrder() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.setCommentAbove("a", "above");
    table.setCommentAfter("a", "after");

    List<TomlComment> comments = table.comments("a");
    assertEquals(2, comments.size());
    assertEquals(TomlComment.Placement.ABOVE, comments.get(0).placement());
    assertEquals(TomlComment.Placement.AFTER, comments.get(1).placement());
    assertEquals(comments, table.entry("a").comments());
  }

  @Test
  void shouldReplaceAnExistingCommentRunThroughTheShortcut() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.setCommentAbove("a", "first");

    table.setCommentAbove(List.of("a"), List.of("second"));

    List<TomlComment> comments = table.comments("a");
    assertEquals(1, comments.size());
    assertEquals("second", comments.get(0).text());
  }

  @Test
  void shouldSetCommentFromTextAndPlacementThroughTheDottedKeyAndPathShortcuts() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    table.setComment("a", "a\nb", TomlComment.Placement.ABOVE);
    table.setComment(List.of("b"), "line", TomlComment.Placement.AFTER);

    assertEquals(List.of("a", "b"), table.comments("a").get(0).lines());
    assertEquals(List.of("line"), table.comments("b").get(0).lines());
  }

  @Test
  void shouldRejectSetCommentFromTextAndPlacementOnAnUnsetKey() {
    MutableTomlTable table = MutableTomlTable.create();

    assertThrows(NoSuchElementException.class, () -> table.setComment("nope", "x", TomlComment.Placement.ABOVE));
  }

  @Test
  void shouldSetAndCopyAttachedCommentThroughTheShortcut() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("source", 1L);
    table.set("target", 2L);
    table.setCommentAbove("source", "from source");

    TomlComment sourceComment = table.comments("source").get(0);
    table.setComment("target", sourceComment);

    List<TomlComment> targetComments = table.comments("target");
    assertEquals(1, targetComments.size());
    assertEquals(List.of("from source"), targetComments.get(0).lines());
    assertNull(targetComments.get(0).position());
    assertTrue(table.isModified("target"));
  }

  @Test
  void shouldRemoveAttachedCommentsThroughTheShortcuts() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.setCommentAbove("a", "above");
    table.setCommentAfter("a", "after");

    table.removeCommentAbove("a");
    table.removeCommentAfter(List.of("a"));

    assertTrue(table.comments("a").isEmpty());
  }

  @Test
  void shouldRemoveCommentByPlacementThroughTheShortcut() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.setCommentAbove("a", "above");
    table.setCommentAfter("a", "after");

    table.removeComment("a", TomlComment.Placement.ABOVE);

    List<TomlComment> comments = table.comments("a");
    assertEquals(1, comments.size());
    assertEquals(TomlComment.Placement.AFTER, comments.get(0).placement());
  }

  @Test
  void shouldNotFlagModificationWhenShortcutRemovesAnAbsentComment() {
    LinkedTomlTable table = parse("a = 1\n");

    table.removeCommentAbove("a");

    assertFalse(table.isModified("a"));
  }

  @Test
  void shouldRejectCommentShortcutsOnAnUnsetKeyOrTheEmptyPath() {
    MutableTomlTable table = MutableTomlTable.create();
    TomlComment comment = TomlComment.ofLines(List.of("x"), TomlComment.Placement.ABOVE);

    assertThrows(NoSuchElementException.class, () -> table.setCommentAbove("nope", "x"));
    assertThrows(NoSuchElementException.class, () -> table.setCommentAfter("nope", "x"));
    assertThrows(NoSuchElementException.class, () -> table.setComment("nope", comment));
    assertThrows(NoSuchElementException.class, () -> table.removeCommentAbove("nope"));
    assertThrows(NoSuchElementException.class, () -> table.removeCommentAfter("nope"));
    assertThrows(NoSuchElementException.class, () -> table.removeComment("nope", TomlComment.Placement.ABOVE));

    assertThrows(IllegalArgumentException.class, () -> table.setCommentAbove(List.of(), List.of("x")));
    assertThrows(IllegalArgumentException.class, () -> table.setCommentAfter(List.of(), "x"));
    assertThrows(IllegalArgumentException.class, () -> table.setComment(List.of(), comment));
    assertThrows(IllegalArgumentException.class, () -> table.removeCommentAbove(List.of()));
    assertThrows(IllegalArgumentException.class, () -> table.removeCommentAfter(List.of()));
    assertThrows(IllegalArgumentException.class, () -> table.removeComment(List.of(), TomlComment.Placement.ABOVE));
  }

  @Test
  void shouldAddAndRemoveUnattachedComments() {
    MutableTomlTable table = MutableTomlTable.create();

    table.addComment("footer");
    assertEquals(1, unattachedComments(table).size());

    TomlComment comment = unattachedComments(table).get(0);
    assertTrue(table.removeComment(comment));
    assertTrue(unattachedComments(table).isEmpty());
    assertFalse(table.removeComment(comment));

    // The removed comment can be added again.
    table.addComment(comment);
    assertEquals(1, unattachedComments(table).size());
  }

  @Test
  void shouldAppendAnUnattachedCommentAfterTheLastElement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.addComment("footer");

    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("footer", ((TomlComment) elements.get(1)).text());
  }

  @Test
  void shouldRoundTripAddingAndRemovingAnAlreadyUnattachedComment() {
    // An API comment with no placement and no position: addComment(comment) has nothing to change, so it stores
    // comment itself, and removeComment(comment) then finds that same object by identity.
    TomlComment comment = TomlComment.ofLines(List.of("note"), null);
    MutableTomlTable table = MutableTomlTable.create();

    table.addComment(comment);

    assertSame(comment, table.elements().get(0));
    assertTrue(table.removeComment(comment));
  }

  @Test
  void shouldCopyAParsedUnattachedCommentWhenAddedToAnotherTable() {
    // A parsed comment has a position, so addComment always copies it, even though it is already unattached; removing
    // the original object from the destination therefore returns false.
    LinkedTomlTable source = parse("# above\na = 1 # after\n\n# footer\n");
    TomlComment parsedComment = unattachedComments(source).get(0);
    MutableTomlTable target = MutableTomlTable.create();

    target.addComment(parsedComment);

    assertFalse(target.removeComment(parsedComment));
    assertEquals(1, unattachedComments(target).size());
  }

  @Test
  void shouldRejectAddingAnAttachedComment() {
    LinkedTomlTable table = parse("# above\na = 1 # after\n");
    List<TomlComment> attached = table.comments("a");
    assertEquals(2, attached.size());
    assertThrows(IllegalArgumentException.class, () -> table.addComment(attached.get(0)));
    assertThrows(IllegalArgumentException.class, () -> table.addComment(attached.get(1)));

    assertTrue(unattachedComments(table).isEmpty());
    assertFalse(table.isModified());
  }

  @Test
  void shouldReturnFalseWhenRemovingAnAttachedCommentAsUnattached() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.setCommentAbove("a", "note");

    TomlComment attached = table.comments("a").get(0);

    assertFalse(table.removeComment(attached));
  }

  // A minimal TomlTable of another implementation, holding one level of literal keys, for the deep-copy cases.
  private static final class ForeignTable implements TomlTable {
    private final Map<String, Object> values;

    ForeignTable(Map<String, Object> values) {
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
    public Set<String> keySet() {
      return values.keySet();
    }

    @Override
    public Set<List<String>> keyPathSet(boolean includeTables) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      return values.entrySet();
    }

    @Override
    public Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TomlPosition inputPositionOf(List<String> path) {
      return null;
    }

    @Override
    public TomlKeyValue entry(List<String> path) {
      if (path.size() != 1) {
        return null;
      }
      Object value = values.get(path.get(0));
      return (value != null) ? new ForeignKeyValue(path.get(0), value) : null;
    }

    @Override
    public List<TomlElement> elements() {
      List<TomlElement> elements = new ArrayList<>();
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        elements.add(new ForeignKeyValue(entry.getKey(), entry.getValue()));
      }
      return elements;
    }

    @Override
    public Map<String, Object> toMap() {
      return values;
    }
  }

  // A minimal TomlKeyValue for ForeignTable#elements(), with no position and no comments.
  private static final class ForeignKeyValue implements TomlKeyValue {
    private final String key;
    private final Object value;

    ForeignKeyValue(String key, Object value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String key() {
      return key;
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

  // A minimal TomlValue for ForeignKeyValue#value(), with no position.
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
}
