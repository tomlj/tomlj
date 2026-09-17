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

import static org.junit.jupiter.api.Assertions.*;
import static org.tomlj.EmptyTomlArray.EMPTY_ARRAY;
import static org.tomlj.EmptyTomlTable.EMPTY_TABLE;
import static org.tomlj.TomlPosition.positionAt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LinkedTomlTableTest {

  private static LinkedTomlTable parse(String document) {
    return Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
  }

  @Test
  void emptyTableIsEmpty() {
    TomlTable table = new LinkedTomlTable();
    assertTrue(table.isEmpty());
    assertEquals(0, table.size());
  }

  @Test
  void getMissingPropertyReturnsNull() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(1, 1));
    table.setParsed("foo.baz", "two", positionAt(1, 1));
    assertNull(table.get("baz"));
    assertNull(table.get("foo.bar"));
    assertNull(table.get("foo.bar.baz"));
  }

  @Test
  void getStringProperty() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("foo.bar", "one", positionAt(1, 1));
    assertTrue(table.isString("foo.bar"));
    assertEquals("one", table.getString("foo.bar"));
  }

  @Test
  void shouldCreateParentTables() {
    LinkedTomlTable table = new LinkedTomlTable();
    List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> intermediates =
        table.setParsed("foo.bar", "one", positionAt(1, 1));
    assertTrue(table.isTable("foo"));
    assertNotNull(table.getTable("foo"));
    LinkedTomlTable firstIntermediate = intermediates.get(0).getKey();
    assertEquals(table.get("foo"), firstIntermediate);
    assertFalse(firstIntermediate.isDefined());
  }

  @Test
  void cannotReplaceProperty() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("foo.bar", "one", positionAt(1, 3));
    TomlParseError e = assertThrows(TomlParseError.class, () -> table.setParsed("foo.bar", "two", positionAt(2, 5)));
    assertEquals("foo.bar previously defined at line 1, column 3", e.getMessage());
  }

  @ParameterizedTest
  @MethodSource("quotesComplexKeyInErrorSupplier")
  void quotesComplexKeysInError(List<String> path, String expected) {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed(path, "one", positionAt(1, 3));
    TomlParseError e = assertThrows(TomlParseError.class, () -> table.setParsed(path, "two", positionAt(2, 5)));
    assertEquals(expected + " previously defined at line 1, column 3", e.getMessage());
  }

  static Stream<Arguments> quotesComplexKeyInErrorSupplier() {
    return Stream
        .of(
            Arguments.of(Arrays.asList("", "bar"), "\"\".bar"),
            Arguments.of(Arrays.asList("foo ", "bar"), "\"foo \".bar"),
            Arguments.of(Arrays.asList("foo\n", "bar"), "\"foo\\n\".bar"));
  }

  @Test
  void cannotTreatNonTableAsTable() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("foo.bar", "one", positionAt(5, 3));
    TomlParseError e =
        assertThrows(TomlParseError.class, () -> table.setParsed("foo.bar.baz", "two", positionAt(2, 5)));
    assertEquals("foo.bar is not a table (previously defined at line 5, column 3)", e.getMessage());
  }

  @Test
  void ignoresWhitespaceAroundUnquotedKeys() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("foo.bar", 4, positionAt(5, 3));
    assertEquals(Long.valueOf(4), table.getLong(" foo . bar"));
    table.setParsed(Arrays.asList(" Bar ", " B A Z "), 9, positionAt(5, 3));
    assertEquals(Long.valueOf(9), table.getLong("' Bar '.  \" B A Z \""));
  }

  @Test
  void throwsForInvalidKey() {
    LinkedTomlTable table = new LinkedTomlTable();
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> table.get("foo.=bar"));
    assertEquals("Invalid key: Unexpected '=', expected a key" + TomlTest.INVALID_KEY_HINT, e.getMessage());
  }

  @Test
  void shouldReturnInputPosition() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(4, 3));
    table.setParsed("foo.baz", "two", positionAt(15, 2));
    assertEquals(positionAt(4, 3), table.inputPositionOf("bar"));
    assertEquals(positionAt(15, 2), table.inputPositionOf("foo.baz"));
    assertNull(table.inputPositionOf("baz"));
    assertNull(table.inputPositionOf("foo.bar"));
    assertNull(table.inputPositionOf("foo.bar.baz"));
  }

  @Test
  void shouldReturnKeySet() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(4, 3));
    table.setParsed("foo.baz", "two", positionAt(15, 2));
    assertEquals(new HashSet<>(Arrays.asList("bar", "foo")), table.keySet());
  }

  @Test
  void shouldReturnDottedKeySet() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(4, 3));
    table.setParsed("foo.baz", "two", positionAt(15, 2));
    table.setParsed("foo.buz.bar", "three", positionAt(15, 2));
    assertEquals(
        new HashSet<>(Arrays.asList("bar", "foo", "foo.baz", "foo.buz", "foo.buz.bar")),
        table.dottedKeySet(true));
    assertEquals(new HashSet<>(Arrays.asList("bar", "foo.baz", "foo.buz.bar")), table.dottedKeySet());
  }

  @Test
  void shouldReturnEntrySet() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(4, 3));
    table.setParsed("foo.baz", "two", positionAt(15, 2));
    assertEquals(
        new HashSet<>(
            Arrays
                .asList(
                    new AbstractMap.SimpleEntry<>("bar", "one"),
                    new AbstractMap.SimpleEntry<>("foo", table.get("foo")))),
        table.entrySet());
  }

  @Test
  void shouldReturnDottedEntrySet() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(4, 3));
    table.setParsed("foo.baz", "two", positionAt(15, 2));
    table.setParsed("foo.buz.bar", "three", positionAt(15, 2));
    assertEquals(
        new HashSet<>(
            Arrays
                .asList(
                    new AbstractMap.SimpleEntry<>("bar", "one"),
                    new AbstractMap.SimpleEntry<>("foo", table.get("foo")),
                    new AbstractMap.SimpleEntry<>("foo.baz", "two"),
                    new AbstractMap.SimpleEntry<>("foo.buz", table.get("foo.buz")),
                    new AbstractMap.SimpleEntry<>("foo.buz.bar", "three"))),
        table.dottedEntrySet(true));
    assertEquals(
        new HashSet<>(
            Arrays.<AbstractMap
                .SimpleEntry<String, Object>>asList(
                    new AbstractMap.SimpleEntry<>("bar", "one"),
                    new AbstractMap.SimpleEntry<>("foo.baz", "two"),
                    new AbstractMap.SimpleEntry<>("foo.buz.bar", "three"))),
        table.dottedEntrySet());
  }

  @Test
  void shouldSerializeToJSON() {
    LinkedTomlTable table = new LinkedTomlTable();
    table.setParsed("bar", "one", positionAt(2, 1));
    table.setParsed("foo.baz", "two", positionAt(3, 2));
    table.setParsed("foo.buz", EMPTY_ARRAY, positionAt(3, 2));
    table.setParsed("foo.foo", EMPTY_TABLE, positionAt(3, 2));
    ListTomlArray array = new ListTomlArray(false, positionAt(1, 1));
    array.appendParsed("hello\nthere", positionAt(5, 2));
    array.appendParsed("goodbye", positionAt(5, 2));
    table.setParsed("foo.blah", array, positionAt(5, 2));
    table.setParsed("buz", OffsetDateTime.parse("1937-07-18T03:25:43-04:00"), positionAt(5, 2));
    table.setParsed("glad", LocalDateTime.parse("1937-07-18T03:25:43"), positionAt(5, 2));
    table.setParsed("zoo", LocalDate.parse("1937-07-18"), positionAt(5, 2));
    table.setParsed("alpha", LocalTime.parse("03:25:43"), positionAt(5, 2));
    String expected = "{\n"
        + "  \"bar\" : \"one\",\n"
        + "  \"foo\" : {\n"
        + "    \"baz\" : \"two\",\n"
        + "    \"buz\" : [],\n"
        + "    \"foo\" : {},\n"
        + "    \"blah\" : [\n"
        + "      \"hello\\nthere\",\n"
        + "      \"goodbye\"\n"
        + "    ]\n"
        + "  },\n"
        + "  \"buz\" : \"1937-07-18T03:25:43-04:00\",\n"
        + "  \"glad\" : \"1937-07-18T03:25:43\",\n"
        + "  \"zoo\" : \"1937-07-18\",\n"
        + "  \"alpha\" : \"03:25:43\"\n"
        + "}\n";
    assertEquals(expected.replace("\n", System.lineSeparator()), table.toJson());
  }

  @Test
  void shouldReturnTheEntryForAKey() {
    LinkedTomlTable table = parse("a = 1\n[t]\nb = \"x\" # after\n");
    assertSame(table.elements().get(0), table.entry("a"));
    TomlKeyValue entry = table.entry(List.of("t", "b"));
    assertSame(table.getTable("t").elements().get(0), entry);
    assertEquals("b", entry.key());
    assertEquals("x", entry.value().getString());
    assertEquals(1, entry.comments().size());
    assertEquals(TomlComment.Placement.AFTER, entry.comments().get(0).placement());
    assertEquals(table.inputPositionOf("t.b"), entry.position());
  }

  @Test
  void shouldReturnNullForAMissingEntry() {
    LinkedTomlTable table = parse("a = 1\n[t]\nb = \"x\"\n");
    assertNull(table.entry("missing"));
    assertNull(table.entry(List.of("t", "missing")));
    assertNull(table.entry(List.of()));
    assertNull(table.entry(List.of("a", "x")));
    assertNull(EMPTY_TABLE.entry(List.of("a")));
  }

  @Test
  void shouldReadShortcutsThroughTheEntry() {
    LinkedTomlTable table = parse("a = 1\n[t]\nb = \"x\" # after\n");
    assertEquals(table.entry("t.b").value().get(), table.get("t.b"));
    assertEquals(table.entry("t.b").comments(), table.comments("t.b"));
    assertEquals(table.entry("t.b").position(), table.inputPositionOf("t.b"));
    assertSame(table, table.get(List.of()));
  }

  @Test
  void shouldReturnTheEntryAtAnArrayIndex() {
    LinkedTomlTable table = parse("a = [ 1, # one\n 2 ]\n");
    TomlArray array = table.getArray("a");
    assertSame(array.elements().get(0), array.entry(0));
    assertEquals(2L, array.entry(1).getLong());
    assertEquals(array.comments(0), array.entry(0).comments());
    assertEquals(array.inputPositionOf(0), array.entry(0).position());
    assertThrows(IndexOutOfBoundsException.class, () -> array.entry(2));
    assertThrows(IndexOutOfBoundsException.class, () -> array.entry(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> EMPTY_ARRAY.entry(0));
  }
}
