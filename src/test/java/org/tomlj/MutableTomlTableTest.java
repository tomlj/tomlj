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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
  void shouldStoreWhatAValueReadFromAnEntryHolds() {
    TomlParseResult result = Toml.parse("x = 1\nt = { y = 2 }\nl = [ 3 ]\n");
    assertFalse(result.hasErrors());
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", result.entry("x").value());
    table.set("b", result.entry("t").value());
    table.set("c", result.entry("l").value());
    assertEquals(1L, table.get("a"));
    assertEquals(2L, table.get("b.y"));
    assertEquals(List.of(3L), table.getArray("c").toList());
    assertNotSame(result.get("t"), table.get("b"));
    assertNotSame(result.get("l"), table.get("c"));
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
  void shouldRejectValuesTomlCannotRepresent(Object value) {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set("a", value));
  }

  @Test
  void shouldAcceptBoundaryValuesTomlCanRepresent() {
    MutableTomlTable table = MutableTomlTable.create();
    LocalDate year0 = LocalDate.of(0, 1, 1);
    LocalDate year9999 = LocalDate.of(9999, 12, 31);
    OffsetDateTime offset = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
    String surrogatePair = "surrogate 😀 pair";

    table.set("year0", year0);
    table.set("year9999", year9999);
    table.set("offset", offset);
    table.set("pair", surrogatePair);

    assertEquals(year0, table.get("year0"));
    assertEquals(year9999, table.get("year9999"));
    assertEquals(offset, table.get("offset"));
    assertEquals(surrogatePair, table.get("pair"));
  }

  @Test
  void shouldRejectAKeyWithAnUnpairedSurrogateViaSetList() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set(List.of("bad\uD800key"), 1L));
  }

  @Test
  void shouldRejectAKeyWithAnUnpairedSurrogateViaGetOrCreateTable() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.getOrCreateTable(List.of("bad\uD800key")));
  }

  @Test
  void shouldRejectAKeyWithAnUnpairedSurrogateViaInsertAfter() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    assertThrows(IllegalArgumentException.class, () -> table.insertAfter("a", "bad\uD800key", 1L));
  }

  @Test
  void shouldRejectABadValueNestedInAMap() {
    MutableTomlTable table = MutableTomlTable.create();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("x", "bad\uD800value");
    assertThrows(IllegalArgumentException.class, () -> table.set("m", map));
  }

  @Test
  void shouldRejectABadValueNestedInACollection() {
    MutableTomlTable table = MutableTomlTable.create();
    List<Object> list = Arrays.asList("good", "bad\uD800value");
    assertThrows(IllegalArgumentException.class, () -> table.set("a", list));
  }

  @Test
  void shouldCreateNoIntermediateTableWhenSetIsRejected() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.set("a.b", "bad\uD800value"));
    assertTrue(table.isEmpty());
    assertFalse(table.isModified());
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
    assertEquals(TomlComment.Placement.UNATTACHED, unattached.get(0).placement());
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
  void shouldSplitACommentLineAtANewlineWhereverTheCommentIsSet() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.setCommentAbove("a", "one\ntwo", "three");
    table.addComment("four\nfive");

    assertEquals(List.of("one", "two", "three"), table.comments("a").get(0).lines());
    assertEquals(List.of("four", "five"), unattachedComments(table).get(0).lines());
    assertThrows(IllegalArgumentException.class, () -> table.setCommentAfter("a", "one\ntwo"));
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
  void shouldRejectAnUnattachedPlacementWhenSettingOrRemovingAnAttachedComment() {
    MutableTomlTable table = parse("# above\na = 1\n");

    assertThrows(IllegalArgumentException.class, () -> table.setComment("a", "x", TomlComment.Placement.UNATTACHED));
    assertThrows(IllegalArgumentException.class, () -> table.removeComment("a", TomlComment.Placement.UNATTACHED));
    assertThrows(NullPointerException.class, () -> table.setComment("a", "x", null));
    assertThrows(NullPointerException.class, () -> table.removeComment("a", null));
    assertEquals(List.of("above"), table.comments("a").get(0).lines());
    assertFalse(table.isModified());
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
    TomlComment comment = TomlComment.ofLines(List.of("note"), TomlComment.Placement.UNATTACHED);
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

  @Test
  void shouldInsertAfterAnAnchorThatAnUnattachedCommentFollows() {
    // A comment run needs a blank line after it, before the next entry, to be unattached rather than that entry's
    // ABOVE run; see Comments' class documentation.
    LinkedTomlTable table = parse("a = 1\n# c\n\nb = 2\n");
    TomlComment comment = (TomlComment) table.elements().get(1);

    table.insertAfter("a", "x", 0L);

    List<TomlElement> elements = table.elements();
    assertEquals(4, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("x", ((TomlKeyValue) elements.get(1)).key());
    assertSame(comment, elements.get(2));
    assertEquals("b", ((TomlKeyValue) elements.get(3)).key());
    assertEquals(List.of("a", "x", "b"), new ArrayList<>(table.keySet()));
    List<String> entryKeys = new ArrayList<>();
    for (Map.Entry<String, Object> entry : table.entrySet()) {
      entryKeys.add(entry.getKey());
    }
    assertEquals(List.of("a", "x", "b"), entryKeys);
    assertEquals(0L, table.get("x"));
    assertEquals(3, table.size());
  }

  @Test
  void shouldInsertBeforeAnAnchorThatAnUnattachedCommentPrecedes() {
    LinkedTomlTable table = parse("a = 1\n# c\n\nb = 2\n");

    table.insertBefore("b", "y", 0L);

    List<TomlElement> elements = table.elements();
    assertEquals(4, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("c", ((TomlComment) elements.get(1)).text());
    assertEquals("y", ((TomlKeyValue) elements.get(2)).key());
    assertEquals("b", ((TomlKeyValue) elements.get(3)).key());
    assertEquals(List.of("a", "y", "b"), new ArrayList<>(table.keySet()));
  }

  @Test
  void shouldInsertBeforeTheFirstEntryAndAfterTheLastEntry() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    table.insertBefore("a", "first", 0L);
    table.insertAfter("b", "last", 3L);

    assertEquals(List.of("first", "a", "b", "last"), new ArrayList<>(table.keySet()));
  }

  @Test
  void shouldInsertIntoANestedTableThroughADottedAnchor() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("t.a", 1L);
    table.set("t.b", 2L);

    table.insertBefore("t.b", "a2", 3L);

    assertEquals(List.of("a", "a2", "b"), new ArrayList<>(table.getTable("t").keySet()));
  }

  @Test
  void shouldInsertThroughTheListPathForm() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    table.insertBefore(List.of("b"), "x", 9L);

    assertEquals(List.of("a", "x", "b"), new ArrayList<>(table.keySet()));
  }

  @Test
  void shouldInsertBesideAQuotedAnchorKey() {
    LinkedTomlTable table = parse("\"a.b\" = 1\nc = 2\n");

    table.insertAfter(List.of("a.b"), "x", 9L);

    assertEquals(List.of("a.b", "x", "c"), new ArrayList<>(table.keySet()));
  }

  @Test
  void shouldGiveTheNewEntryNoPositionNoCommentsAndMarkItModified() {
    LinkedTomlTable table = parse("a = 1\nb = 2\n");
    assertFalse(table.isModified());

    table.insertBefore("b", "x", 9L);

    assertNull(table.inputPositionOf("x"));
    assertEquals(List.of(), table.comments("x"));
    assertTrue(table.isModified("x"));
    assertTrue(table.isModified());
  }

  @Test
  void shouldStoreATableValueAsADeepCopyWhenInserting() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    MutableTomlTable original = MutableTomlTable.create();
    original.set("x", 1L);

    table.insertBefore("a", "t", original);

    original.set("x", 2L);
    assertEquals(1L, table.get("t.x"));
  }

  @Test
  void shouldRejectAnEmptyAnchorPathWhenInserting() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.insertBefore(List.of(), "x", 1L));
    assertThrows(IllegalArgumentException.class, () -> table.insertAfter(List.of(), "x", 1L));
  }

  @Test
  void shouldRejectANullAnchorPathElementOrNullKeyLeavingTheTableUnchanged() {
    LinkedTomlTable table = parse("a = 1\n");

    assertThrows(NullPointerException.class, () -> table.insertBefore(Arrays.asList("a", null), "x", 1L));
    assertThrows(NullPointerException.class, () -> table.insertBefore("a", null, 1L));

    assertEquals(1, table.elements().size());
    assertFalse(table.isModified());
  }

  @Test
  void shouldRejectNullAndUnconvertibleValuesWhenInsertingLeavingTheTableUnchanged() {
    LinkedTomlTable table = parse("a = 1\n");

    assertThrows(NullPointerException.class, () -> table.insertBefore("a", "x", null));
    assertThrows(IllegalArgumentException.class, () -> table.insertBefore("a", "x", new Object()));

    assertEquals(1, table.elements().size());
    assertFalse(table.isModified());
  }

  @Test
  void shouldRejectInsertingBesideAnAnchorThatIsNotSet() {
    MutableTomlTable table = MutableTomlTable.create();
    NoSuchElementException e = assertThrows(NoSuchElementException.class, () -> table.insertBefore("nope", "x", 1L));
    assertEquals("nope is not set", e.getMessage());
  }

  @Test
  void shouldRejectInsertingThroughAMissingIntermediateTable() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(NoSuchElementException.class, () -> table.insertBefore("missing.a", "x", 1L));
  }

  @Test
  void shouldRejectInsertingThroughANonTableIntermediate() {
    LinkedTomlTable table = parse("a = 1\n");
    TomlInvalidTypeException e = assertThrows(TomlInvalidTypeException.class, () -> table.insertBefore("a.b", "x", 1L));
    assertEquals("Value of 'a' is a integer", e.getMessage());
  }

  @Test
  void shouldRejectInsertingAnAlreadySetKey() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("t.a", 1L);
    table.set("t.b", 2L);

    TomlKeyAlreadySetException e =
        assertThrows(TomlKeyAlreadySetException.class, () -> table.insertBefore("t.b", "a", 3L));
    assertEquals("a is already set", e.getMessage());
  }

  @Test
  void shouldRejectInsertingTheAnchorsOwnKey() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    assertThrows(TomlKeyAlreadySetException.class, () -> table.insertBefore("a", "a", 2L));
  }

  @Test
  void shouldInsertCommentBeforeAndAfterAnEntryWithVarargsAndList() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    table.insertCommentBefore("b", "above b");
    table.insertCommentAfter(List.of("a"), List.of("after a"));
    // Each insertion goes directly beside its entry, so it lands inside the comment inserted before it.
    table.insertCommentBefore(List.of("b"), "also above b");
    table.insertCommentAfter("a", List.of("also after a"));

    List<TomlElement> elements = table.elements();
    assertEquals(6, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("also after a", ((TomlComment) elements.get(1)).text());
    assertEquals("after a", ((TomlComment) elements.get(2)).text());
    assertEquals("above b", ((TomlComment) elements.get(3)).text());
    assertEquals("also above b", ((TomlComment) elements.get(4)).text());
    assertEquals("b", ((TomlKeyValue) elements.get(5)).key());
  }

  @Test
  void shouldSplitAnInsertedCommentLineAtANewline() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.insertCommentBefore("a", "one\ntwo", "three");
    table.insertCommentAfter(List.of("a"), List.of("four\nfive"));

    List<TomlElement> elements = table.elements();
    assertEquals(List.of("one", "two", "three"), ((TomlComment) elements.get(0)).lines());
    assertEquals(List.of("four", "five"), ((TomlComment) elements.get(2)).lines());
  }

  @Test
  void shouldInsertACommentCopiedFromAnotherDocument() {
    LinkedTomlTable source = parse("# footer\n");
    TomlComment sourceComment = (TomlComment) source.elements().get(0);
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.insertCommentBefore("a", sourceComment);

    TomlComment inserted = (TomlComment) table.elements().get(0);
    assertEquals(TomlComment.Placement.UNATTACHED, inserted.placement());
    assertNull(inserted.position());
    assertEquals(sourceComment.text(), inserted.text());
    assertNotSame(sourceComment, inserted);
    assertEquals(1, unattachedComments(source).size());
  }

  @Test
  void shouldInsertCommentBeforeAnEntryWithAnAboveRunKeepingTheRunAttached() {
    LinkedTomlTable table = parse("# above\na = 1\n");
    List<TomlComment> aboveRun = table.comments("a");

    table.insertCommentBefore("a", "new");

    assertEquals(aboveRun, table.comments("a"));
    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("new", ((TomlComment) elements.get(0)).text());
    assertEquals("a", ((TomlKeyValue) elements.get(1)).key());
    assertTrue(table.isModified());
  }

  @Test
  void shouldFindACommentInsertedBesideAnAnchorWithRemoveComment() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    table.insertCommentBefore("a", "note");

    TomlComment inserted = (TomlComment) table.elements().get(0);
    assertTrue(table.removeComment(inserted));
  }

  @Test
  void shouldRejectInsertingACommentBesideAnAnchorThatIsNotSet() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(NoSuchElementException.class, () -> table.insertCommentBefore("nope", "x"));
    assertThrows(NoSuchElementException.class, () -> table.insertCommentAfter("nope", "x"));
  }

  @Test
  void shouldRejectInsertingACommentWithAnEmptyAnchorPath() {
    MutableTomlTable table = MutableTomlTable.create();
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore(List.of(), List.of("x")));
  }

  @Test
  void shouldRejectInsertingACommentWithEmptyLinesOrAnUnwritableLine() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);

    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore(List.of("a"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore("a", "bad\u0001line"));
  }

  @Test
  void shouldRejectInsertingAnAttachedComment() {
    LinkedTomlTable table = parse("# above\na = 1\n");
    TomlComment attached = table.comments("a").get(0);
    TomlElement anchor = table.entry("a");

    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore("a", attached));
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentAfter(List.of("a"), attached));
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore(anchor, attached));
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentAfter(anchor, attached));

    assertEquals(1, table.elements().size());
    assertFalse(table.isModified());
  }

  @Test
  void shouldRejectInsertingANullComment() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    assertThrows(NullPointerException.class, () -> table.insertCommentBefore(List.of("a"), (TomlComment) null));
  }

  @Test
  void shouldInsertAnEntryBetweenTwoConsecutiveUnattachedComments() {
    LinkedTomlTable table = parse("a = 1\n\n# one\n\n# two\n\nb = 2\n");
    TomlComment two = (TomlComment) table.elements().get(2);

    table.insertBefore(two, "x", 9L);

    List<TomlElement> elements = table.elements();
    assertEquals(5, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("one", ((TomlComment) elements.get(1)).text());
    assertEquals("x", ((TomlKeyValue) elements.get(2)).key());
    assertEquals("two", ((TomlComment) elements.get(3)).text());
    assertEquals("b", ((TomlKeyValue) elements.get(4)).key());
    assertEquals(List.of("a", "x", "b"), new ArrayList<>(table.keySet()));
    assertEquals(2L, table.get("b"));
  }

  @Test
  void shouldInsertACommentBetweenTwoConsecutiveUnattachedCommentsBeforeAndAfterEach() {
    LinkedTomlTable table = parse("a = 1\n\n# one\n\n# two\n\nb = 2\n");
    TomlComment one = (TomlComment) table.elements().get(1);
    TomlComment two = (TomlComment) table.elements().get(2);

    table.insertCommentAfter(one, "after one");
    table.insertCommentBefore(two, "before two");

    List<TomlElement> elements = table.elements();
    assertEquals(6, elements.size());
    assertEquals("a", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("one", ((TomlComment) elements.get(1)).text());
    assertEquals("after one", ((TomlComment) elements.get(2)).text());
    assertEquals("before two", ((TomlComment) elements.get(3)).text());
    assertEquals("two", ((TomlComment) elements.get(4)).text());
    assertEquals("b", ((TomlKeyValue) elements.get(5)).key());
  }

  @Test
  void shouldInsertBeforeAndAfterAnEntryThroughItsElement() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    table.insertBefore(table.entry("b"), "x", 9L);
    table.insertAfter(table.entry("a"), "y", 8L);

    assertEquals(List.of("a", "y", "x", "b"), new ArrayList<>(table.keySet()));
  }

  @Test
  void shouldInsertBeforeAnEntryThroughItsElementKeepingItsAboveRunAttached() {
    LinkedTomlTable table = parse("# above\na = 1\n");
    List<TomlComment> aboveRun = table.comments("a");
    TomlElement anchor = table.elements().get(0);

    table.insertBefore(anchor, "x", 9L);

    assertEquals(aboveRun, table.comments("a"));
    List<TomlElement> elements = table.elements();
    assertEquals(2, elements.size());
    assertEquals("x", ((TomlKeyValue) elements.get(0)).key());
    assertEquals("a", ((TomlKeyValue) elements.get(1)).key());
  }

  @Test
  void shouldRejectAnAnchorFromASubTableACopyOrAnotherDocument() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("t.x", 1L);

    TomlElement subTableAnchor = table.getTable("t").entry("x");
    NoSuchElementException e1 =
        assertThrows(NoSuchElementException.class, () -> table.insertBefore(subTableAnchor, "y", 1L));
    assertEquals("anchor is not an element of this table", e1.getMessage());

    MutableTomlTable copy = MutableTomlTable.copyOf(table);
    TomlElement copyAnchor = copy.entry("a");
    assertThrows(NoSuchElementException.class, () -> table.insertBefore(copyAnchor, "y", 1L));

    MutableTomlTable other = MutableTomlTable.create();
    other.set("z", 1L);
    TomlElement otherAnchor = other.entry("z");
    assertThrows(NoSuchElementException.class, () -> table.insertBefore(otherAnchor, "y", 1L));
  }

  @Test
  void shouldRejectANullAnchorOrNullKeyOrValueThroughTheElementForm() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    TomlElement anchor = table.entry("a");

    assertThrows(NullPointerException.class, () -> table.insertBefore((TomlElement) null, "x", 1L));
    assertThrows(NullPointerException.class, () -> table.insertBefore(anchor, null, 1L));
    assertThrows(NullPointerException.class, () -> table.insertBefore(anchor, "x", null));
  }

  @Test
  void shouldRejectANullAnchorOrNullCommentThroughTheElementForm() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    TomlElement anchor = table.entry("a");

    assertThrows(NullPointerException.class, () -> table.insertCommentBefore((TomlElement) null, "x"));
    assertThrows(NullPointerException.class, () -> table.insertCommentBefore(anchor, (TomlComment) null));
  }

  @Test
  void shouldRejectADuplicateKeyThroughTheElementFormWithTheShortMessage() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set("b", 2L);

    TomlKeyAlreadySetException e =
        assertThrows(TomlKeyAlreadySetException.class, () -> table.insertBefore(table.entry("b"), "a", 9L));
    assertEquals("a is already set", e.getMessage());
  }

  @Test
  void shouldRejectADuplicateKeyThroughTheElementFormWithAQuotedKey() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    table.set(List.of("a b"), 2L);

    TomlKeyAlreadySetException e =
        assertThrows(TomlKeyAlreadySetException.class, () -> table.insertBefore(table.entry("a"), "a b", 9L));
    assertEquals("\"a b\" is already set", e.getMessage());
  }

  @Test
  void shouldRejectADuplicateKeyThroughThePathFormNamingOnlyTheKey() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("t.x", 1L);
    table.set("t.y", 2L);

    TomlKeyAlreadySetException e =
        assertThrows(TomlKeyAlreadySetException.class, () -> table.insertAfter("t.x", "y", 3L));
    assertEquals("y is already set", e.getMessage());
  }

  @Test
  void shouldLeaveTheTableUnchangedWhenElementFormInsertionIsRejected() {
    LinkedTomlTable table = parse("a = 1\nb = 2\n");

    assertThrows(TomlKeyAlreadySetException.class, () -> table.insertBefore(table.entry("a"), "b", 9L));

    assertEquals(2, table.elements().size());
    assertFalse(table.isModified());
  }

  @Test
  void shouldLeaveTheTableUnchangedWhenElementCommentInsertionIsRejected() {
    LinkedTomlTable table = parse("a = 1\n");
    TomlElement foreignAnchor = MutableTomlTable.create().addComment("x").elements().get(0);

    assertThrows(NoSuchElementException.class, () -> table.insertCommentBefore(foreignAnchor, "note"));

    assertEquals(1, table.elements().size());
    assertFalse(table.isModified());
  }

  @Test
  void shouldMarkOnlyTheInsertedEntryModifiedThroughTheElementForm() {
    LinkedTomlTable table = parse("a = 1\nb = 2\n");
    TomlElement anchor = table.entry("b");

    table.insertBefore(anchor, "x", 9L);

    assertTrue(table.isModified("x"));
    assertTrue(table.isModified());
    assertFalse(table.isModified("a"));
    assertFalse(table.isModified("b"));
  }

  @Test
  void shouldMarkTheContainerModifiedWhenInsertingACommentThroughTheElementForm() {
    LinkedTomlTable table = parse("a = 1\n");
    assertFalse(table.isModified());

    table.insertCommentBefore(table.entry("a"), "note");

    assertTrue(table.isModified());
    assertFalse(table.isModified("a"));
  }

  @Test
  void shouldInsertCommentTextFormsThroughTheElementAnchorAndValidateLines() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("a", 1L);
    TomlElement anchor = table.entry("a");

    table.insertCommentBefore(anchor, "above");
    table.insertCommentAfter(anchor, List.of("after"));

    List<TomlElement> elements = table.elements();
    assertEquals(3, elements.size());
    assertEquals("above", ((TomlComment) elements.get(0)).text());
    assertEquals("a", ((TomlKeyValue) elements.get(1)).key());
    assertEquals("after", ((TomlComment) elements.get(2)).text());

    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore(anchor, List.of()));
    String badLine = "bad" + (char) 1 + "line";
    assertThrows(IllegalArgumentException.class, () -> table.insertCommentBefore(anchor, badLine));
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
