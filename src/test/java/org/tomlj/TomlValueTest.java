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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the values {@link TomlValue#parse} reads from text, and that a value so made is written with the notation it
 * was given.
 */
class TomlValueTest {

  private static final TomlWriteOptions LF = TomlWriteOptions.defaults().withLineSeparator("\n");

  @ParameterizedTest(name = "{0}")
  @MethodSource("parsedValues")
  void parsesOneValue(String text, Object expected) {
    assertEquals(expected, TomlValue.parse(text).get());
  }

  static Stream<Arguments> parsedValues() {
    return Stream
        .of(
            Arguments.of("0xFF", 255L),
            Arguments.of("0o755", 493L),
            Arguments.of("0b1010", 10L),
            Arguments.of("1_000", 1000L),
            Arguments.of("-1", -1L),
            Arguments.of("1e3", 1000.0),
            Arguments.of("+inf", Double.POSITIVE_INFINITY),
            Arguments.of("true", true),
            Arguments.of("\"a\\tb\"", "a\tb"),
            Arguments.of("'C:\\path'", "C:\\path"),
            Arguments.of("\"\"\"\nline\n\"\"\"", "line\n"),
            Arguments.of("'''\nraw\\n'''", "raw\\n"),
            Arguments.of("1979-05-27 07:32:00Z", OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 0, ZoneOffset.UTC)),
            Arguments.of("1979-05-27T07:32:00", LocalDateTime.of(1979, 5, 27, 7, 32)),
            Arguments.of("1979-05-27", LocalDate.of(1979, 5, 27)),
            Arguments.of("07:32:00", LocalTime.of(7, 32)),
            Arguments.of("  0xFF  ", 255L));
  }

  @Test
  void parsesAnInlineTableAndAnArray() {
    TomlValue table = TomlValue.parse("{ a = 1, b.c = 'x' }");
    assertTrue(table.isTable());
    assertEquals(1L, table.getTable().get("a"));
    assertEquals("x", table.getTable().get("b.c"));
    assertTrue(TomlValue.parse("{}").getTable().isEmpty());

    TomlValue array = TomlValue.parse("[ 1, [2], { d = 3 } ]");
    assertTrue(array.isArray());
    assertEquals(3, array.getArray().size());
    assertEquals(2L, array.getArray().getArray(1).get(0));
    assertEquals(3L, array.getArray().getTable(2).get("d"));
    assertTrue(TomlValue.parse("[]").getArray().isEmpty());
  }

  @Test
  void keepsACommentInsideAnArrayOrAnInlineTable() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("l", TomlValue.parse("[\n  1,  # one\n  2,\n]"));
    doc.set("t", TomlValue.parse("{\n  # above\n  a = 1,\n}"));

    assertEquals("l = [\n  1,  # one\n  2,\n]\nt = {\n  # above\n  a = 1,\n}\n", doc.toToml(LF));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("textThatIsNotOneValue")
  void rejectsTextThatIsNotOneValue(String text) {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TomlValue.parse(text));
    assertTrue(e.getMessage().startsWith("Invalid value: "), e.getMessage());
  }

  static Stream<String> textThatIsNotOneValue() {
    return Stream
        .of(
            "",
            "1 2",
            "0xFF\n",
            "0xFF # comment",
            "# comment\n0xFF",
            "0xFF\n# comment",
            "[1,",
            "{ a = 1, a = 2 }",
            "k = 1",
            "0x1_0000_0000_0000_0000",
            "\"\\q\"",
            "1979-02-30");
  }

  @Test
  void parsesForTheVersionGiven() {
    assertEquals(LocalTime.of(7, 32), TomlValue.parse("07:32", TomlVersion.V1_1_0).get());
    assertThrows(IllegalArgumentException.class, () -> TomlValue.parse("07:32", TomlVersion.V1_0_0));
    assertEquals(1L, TomlValue.parse("{\n  a = 1 }", TomlVersion.V1_1_0).getTable().get("a"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.parse("{\n  a = 1 }", TomlVersion.V1_0_0));
  }

  @Test
  void rejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> TomlValue.parse(null));
    assertThrows(NullPointerException.class, () -> TomlValue.parse("1", null));
  }

  @Test
  void aParsedValueIsWrittenAsItWasParsedUnlessNothingIsKept() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("mask", TomlValue.parse("0xFF"));
    doc.set("big", TomlValue.parse("1_000_000"));
    doc.set("f", TomlValue.parse("1e3"));
    doc.set("path", TomlValue.parse("'C:\\Users'"));
    doc.set("when", TomlValue.parse("1979-05-27 07:32:00Z"));
    doc.set("t", TomlValue.parse("{ a = 0x1, b = 'x' }"));
    doc.set("aot", TomlValue.parse("[ { a = 1 } ]"));
    doc.set("plain", 255);

    String asParsed = "mask = 0xFF\nbig = 1_000_000\nf = 1e3\npath = 'C:\\Users'\nwhen = 1979-05-27 07:32:00Z\n"
        + "t = { a = 0x1, b = 'x' }\naot = [{ a = 1 }]\nplain = 255\n";
    assertEquals(asParsed, doc.toToml(LF));
    assertEquals(asParsed, doc.toToml(LF.keep(TomlWriteOptions.Keep.NOTATION)));
    assertEquals(
        "mask = 255\nbig = 1000000\nf = 1000.0\npath = \"C:\\\\Users\"\nwhen = 1979-05-27T07:32:00Z\nplain = 255\n\n"
            + "[t]\na = 1\nb = \"x\"\n\n[[aot]]\na = 1\n",
        doc.toToml(LF.keep(TomlWriteOptions.Keep.NOTHING)));
  }

  @Test
  void aParsedInlineTableOrArrayFilledLaterKeepsItsBrackets() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("t", TomlValue.parse("{}"));
    doc.getTable("t").set("a", 1).set("b", MutableTomlTable.create().set("c", 2));
    doc.set("l", TomlValue.parse("[]"));
    doc.getArray("l").add(MutableTomlTable.create().set("x", 1));

    // A sub-table made with create() inside an inline table is written with dotted keys, as the default style writes it
    assertEquals("t = { a = 1, b.c = 2 }\nl = [{ x = 1 }]\n", doc.toToml(LF));
  }

  @Test
  void aParsedValueSetInAParsedDocumentIsWrittenAsItWasParsed() {
    TomlParseResult result = Toml.parse("x = 1  # c\n[t]\ny = 2\n");
    assertFalse(result.hasErrors());
    result.set("x", TomlValue.parse("0xFF"));
    result.getTable("t").set("z", TomlValue.parse("{ a = 1 }"));

    assertEquals("x = 0xFF  # c\n[t]\ny = 2\nz = { a = 1 }\n", result.toToml(LF));
  }
}
