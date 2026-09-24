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
import java.util.List;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("madeValues")
  void aValueMadeWithANotationIsWrittenInItAndReadsBack(String description, TomlValue value, String expected) {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("k", value);
    String written = doc.toToml(LF);
    assertEquals("k = " + expected + "\n", written);

    TomlParseResult reparsed = Toml.parse(written);
    assertFalse(reparsed.hasErrors(), () -> reparsed.errors().toString());
    assertEquals(value.get(), reparsed.get("k"));

    // Every notation a factory writes is TOML 1.0.0
    assertEquals(written, doc.toToml(LF.withVersion(TomlVersion.V1_0_0)));
    TomlParseResult reparsedAs100 = Toml.parse(written, TomlVersion.V1_0_0);
    assertFalse(reparsedAs100.hasErrors(), () -> reparsedAs100.errors().toString());
  }

  @Test
  void aParsedValueIsNotWrittenForAVersionThatCannotReadIt() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("t", TomlValue.parse("07:32"));
    TomlWriteOptions toml100 = LF.withVersion(TomlVersion.V1_0_0);

    assertEquals("t = 07:32\n", doc.toToml(LF));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> doc.toToml(toml100));
    assertEquals(
        "A time without seconds originally at line 1, column 1 needs TOML 1.1.0 and cannot be written for TOML 1.0.0",
        e.getMessage());
    assertThrows(IllegalArgumentException.class, () -> doc.toToml(toml100.keep(TomlWriteOptions.Keep.NOTATION)));
    assertEquals("t = 07:32:00\n", doc.toToml(toml100.keep(TomlWriteOptions.Keep.NOTHING)));
  }

  @Test
  void aValueCopiedFromADocumentCarriesTheVersionItNeeds() {
    // The trailing comma is recorded before the escape written ahead of it
    TomlParseResult source = Toml.parse("t = { a = \"\\e\", }\n");
    assertFalse(source.hasErrors());
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("x", source.entry("t.a").value());
    TomlWriteOptions toml100 = LF.withVersion(TomlVersion.V1_0_0);

    assertEquals("x = \"\\e\"\n", doc.toToml(LF));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> doc.toToml(toml100));
    assertEquals(
        "The escape sequence '\\e' originally at line 1, column 12 needs TOML 1.1.0 and cannot be written for TOML 1.0.0",
        e.getMessage());
    assertEquals("x = \"\\u001b\"\n", doc.toToml(toml100.keep(TomlWriteOptions.Keep.NOTHING)));
  }

  @Test
  void aValueBuiltFromAJavaValueIsWrittenForToml100() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("t", LocalTime.of(7, 32));
    doc.set("e", "\u001b[0m");
    doc.set(List.of("\u001b"), 1);

    assertEquals("t = 07:32:00\ne = \"\\u001b[0m\"\n\"\\u001b\" = 1\n", doc.toToml(LF.withVersion(TomlVersion.V1_0_0)));
  }

  static Stream<Arguments> madeValues() {
    return Stream
        .of(
            Arguments.of("hex", TomlValue.hex(255), "0xFF"),
            Arguments.of("hex zero", TomlValue.hex(0), "0x0"),
            Arguments.of("hex of the largest long", TomlValue.hex(Long.MAX_VALUE), "0x7FFFFFFFFFFFFFFF"),
            Arguments.of("lowercase hex", TomlValue.hexLowercase(255), "0xff"),
            Arguments.of("octal", TomlValue.octal(493), "0o755"),
            Arguments.of("binary", TomlValue.binary(10), "0b1010"),
            Arguments.of("grouped", TomlValue.grouped(1_000_000), "1_000_000"),
            Arguments.of("grouped negative", TomlValue.grouped(-1_234_567), "-1_234_567"),
            Arguments.of("grouped two digits", TomlValue.grouped(12), "12"),
            Arguments.of("grouped three digits", TomlValue.grouped(123), "123"),
            Arguments.of("grouped four digits", TomlValue.grouped(1234), "1_234"),
            Arguments.of("grouped zero", TomlValue.grouped(0), "0"),
            Arguments.of("grouped smallest long", TomlValue.grouped(Long.MIN_VALUE), "-9_223_372_036_854_775_808"),
            Arguments.of("grouped largest long", TomlValue.grouped(Long.MAX_VALUE), "9_223_372_036_854_775_807"),
            Arguments.of("literal with backslashes", TomlValue.literal("C:\\Users\\x"), "'C:\\Users\\x'"),
            Arguments.of("literal with quotes", TomlValue.literal("say \"hi\""), "'say \"hi\"'"),
            Arguments.of("literal with a tab", TomlValue.literal("a\tb"), "'a\tb'"),
            Arguments.of("literal beyond ASCII", TomlValue.literal("日本 😀"), "'日本 😀'"),
            Arguments.of("empty literal", TomlValue.literal(""), "''"),
            Arguments.of("multi-line literal", TomlValue.multilineLiteral("a\\d+\nb"), "'''\na\\d+\nb'''"),
            Arguments
                .of("multi-line literal ending in an apostrophe", TomlValue.multilineLiteral("it'"), "'''\nit''''"),
            Arguments
                .of("multi-line literal ending in two apostrophes", TomlValue.multilineLiteral("it''"), "'''\nit'''''"),
            Arguments.of("multi-line literal on one line", TomlValue.multilineLiteral("plain"), "'''\nplain'''"),
            Arguments.of("multi-line literal ending in a newline", TomlValue.multilineLiteral("a\n"), "'''\na\n'''"),
            Arguments
                .of("multi-line literal starting with a newline", TomlValue.multilineLiteral("\na"), "'''\n\na'''"),
            Arguments.of("empty multi-line literal", TomlValue.multilineLiteral(""), "'''\n'''"));
  }

  @Test
  void rejectsAValueItsNotationCannotWrite() {
    assertThrows(IllegalArgumentException.class, () -> TomlValue.hex(-1));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.hexLowercase(-1));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.octal(-1));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.binary(-1));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.literal("it's"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.literal("a\nb"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.literal("a\u0001b"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.literal("a\u007Fb"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.literal("bad\uD800"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.multilineLiteral("it'''"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.multilineLiteral("a\r\nb"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.multilineLiteral("a\u0001b"));
    assertThrows(IllegalArgumentException.class, () -> TomlValue.multilineLiteral("bad\uD800"));
    assertThrows(NullPointerException.class, () -> TomlValue.literal(null));
    assertThrows(NullPointerException.class, () -> TomlValue.multilineLiteral(null));
  }

  @Test
  void aValueMadeWithANotationIsWrittenInTheDefaultStyleWhenNothingIsKept() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("mask", TomlValue.hex(255));
    doc.set("path", TomlValue.literal("C:\\Users"));
    doc.set("text", TomlValue.multilineLiteral("a\nb"));

    assertEquals("mask = 0xFF\npath = 'C:\\Users'\ntext = '''\na\nb'''\n", doc.toToml(LF));
    assertEquals(
        "mask = 255\npath = \"C:\\\\Users\"\ntext = \"\"\"\na\nb\"\"\"\n",
        doc.toToml(LF.keep(TomlWriteOptions.Keep.NOTHING)));
  }

  @Test
  void aValueMadeWithANotationSetInAParsedDocumentIsWrittenInIt() {
    TomlParseResult result = Toml.parse("mask = 1  # c\nl = [ 1 ]\n");
    assertFalse(result.hasErrors());
    result.set("mask", TomlValue.hex(255));
    result.getArray("l").add(TomlValue.binary(5));

    assertEquals("mask = 0xFF  # c\nl = [ 1, 0b101 ]\n", result.toToml(LF));
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
