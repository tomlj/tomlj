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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.tomlj.TomlType.ARRAY;
import static org.tomlj.TomlType.TABLE;
import static org.tomlj.TomlType.typeFor;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TomlTest {

  @Test
  void shouldParseEmptyDocument() {
    TomlParseResult result = Toml.parse("\n");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
  }

  @Test
  void shouldParseSimpleKey() {
    TomlParseResult result = Toml.parse("foo = 'bar'");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals("bar", result.getString("foo"));
  }

  @Test
  void shouldParseQuotedKey() {
    TomlParseResult result = Toml.parse("\"foo\\nba\\\"r\" = 0b11111111");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(255), result.getLong(Collections.singletonList("foo\nba\"r")));
  }

  @Test
  void shouldParseDottedKey() {
    TomlParseResult result = Toml.parse(" foo  . \" bar\\t\" . -baz = 0x000a");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Long.valueOf(10), result.getLong(Arrays.asList("foo", " bar\t", "-baz")));
  }

  @Test
  void shouldNotParseDottedKeysAtV0_4_0OrEarlier() {
    TomlParseResult result = Toml.parse("[foo]\n bar.baz = 1", TomlVersion.V0_4_0);
    assertTrue(result.hasErrors());
    TomlParseError error = result.errors().get(0);
    assertEquals("Dotted keys are not supported", error.getMessage());
    assertEquals(2, error.position().line());
    assertEquals(2, error.position().column());
  }

  @ParameterizedTest
  @MethodSource("stringSupplier")
  void shouldParseString(String input, String expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected.replace("\n", System.lineSeparator()), result.getString("foo"));
  }

  static Stream<Arguments> stringSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of(
                "foo = \"\"",
                ""),
        Arguments.of(
                "foo = \"\\\"\"",
                "\""),
        Arguments.of(
                "foo = \"bar \\b \\f \\n \\\\ \\u0053 \\U0010FfFf baz\"",
                "bar \b \f \n \\ S \uDBFF\uDFFF baz"),
        Arguments.of(
                "foo = \"I'm a string. \\\"You can quote me\\\". Name\tJos\\u00E9\\nLocation\\tSF.\"",
                "I'm a string. \"You can quote me\". Name\tJosé\nLocation\tSF."),
        Arguments.of(
                "foo = \"\"\"\"\"\"",
                ""),
        Arguments.of(
                "foo = \"\"\"  foo\nbar\"\"\"",
                "  foo\nbar"),
        Arguments.of(
                "foo = \"\"\"\n  foobar\"\"\"",
                "  foobar"),
        Arguments.of(
                "foo = \"\"\"\n  foo\nbar\"\"\"",
                "  foo\nbar"),
        Arguments.of(
                "foo = \"\"\"\\n  foo\nbar\"\"\"",
                "\n  foo\nbar"),
        Arguments.of(
                "foo = \"\"\"\n\n  foo\nbar\"\"\"",
                "\n  foo\nbar"),
        Arguments.of(
                "foo = \"\"\"  foo \\  \nbar\"\"\"",
                "  foo \nbar"),
        Arguments.of(
                "foo = \"\"\"  foo \\\nbar\"\"\"",
                "  foo \nbar"),
        Arguments.of(
                "foo = \"\"\"  foo \\       \nbar\"\"\"",
                "  foo \nbar"),
        Arguments.of(
                "foo = \"foobar#\" # comment",
                "foobar#"),
        Arguments.of(
                "foo = \"foobar#\"",
                "foobar#"),
        Arguments.of(
                "foo = \"foobar #baz\"",
                "foobar #baz"),
        Arguments.of(
                "foo = \"foo \\\" bar #\" # \"baz\"",
                "foo \" bar #"),
        Arguments.of(
                "foo = ''",
                ""),
        Arguments.of(
                "foo = '\"'",
                "\""),
        Arguments.of(
                "foo = 'foobar \\'",
                "foobar \\"),
        Arguments.of(
                "foo = '''foobar \n'''",
                "foobar \n"),
        Arguments.of(
                "foo = '''\nfoobar \n'''",
                "foobar \n"),
        Arguments.of(
                "foo = '''\nfoobar \\    \n'''",
                "foobar \\    \n"),
        Arguments.of(
                "# I am a comment. Hear me roar. Roar.\n" +
                "foo = \"value\" # Yeah, you can do this.",
                "value"),
        Arguments.of(
                "foo = \"I'm a string. \\\"You can quote me\\\". Name\\tJos\\u00E9\\nLocation\\tSF.\"",
                "I'm a string. \"You can quote me\". Name\tJosé\nLocation\tSF."),
        Arguments.of(
                "foo=\"\"\"\nRoses are red\nViolets are blue\"\"\"",
                "Roses are red\nViolets are blue")
        );
    // @formatter:on
  }

  @Test
  void shouldFailForInvalidUnicodeEscape() {
    TomlParseResult result = Toml.parse("foo = \"\\UFFFF00FF\"");
    assertTrue(result.hasErrors());
    TomlParseError error = result.errors().get(0);
    assertEquals("Invalid unicode escape sequence", error.getMessage());
    assertEquals(1, error.position().line());
    assertEquals(8, error.position().column());
  }

  @ParameterizedTest
  @MethodSource("integerSupplier")
  void shouldParseInteger(String input, Long expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getLong("foo"));
  }

  static Stream<Arguments> integerSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 1", 1L),
        Arguments.of("foo = 0", 0L),
        Arguments.of("foo = 100", 100L),
        Arguments.of("foo = -9876", -9876L),
        Arguments.of("foo = +5_433", 5433L),
        Arguments.of("foo = 0xff", 255L),
        Arguments.of("foo = 0xffbccd34", 4290563380L),
        Arguments.of("foo = 0o7656", 4014L),
        Arguments.of("foo = 0o0007_6543_21", 2054353L),
        Arguments.of("foo = 0b11111100010101_0100000000111111111", 8466858495L),
        Arguments.of("foo = 0b0000000_00000000000000000000000000", 0L),
        Arguments.of("foo = 0b111111111111111111111111111111111", 8589934591L)
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("floatSupplier")
  void shouldParseFloat(String input, Double expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getDouble("foo"));
  }

  static Stream<Arguments> floatSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 0.0", 0D),
        Arguments.of("foo = 0E100", 0D),
        Arguments.of("foo = 0.00e+100", 0D),
        Arguments.of("foo = 0.00e-100", 0D),
        Arguments.of("foo = +0.0", 0D),
        Arguments.of("foo = -0.0", -0D),
        Arguments.of("foo = 0.000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000", 0D),
        Arguments.of("foo = -0.0E999999999999999999999999999999999999999", -0D),
        Arguments.of("foo = 1.0", 1D),
        Arguments.of("foo = 43.55E34", 43.55E34D),
        Arguments.of("foo = 43.55E+34", 43.55E34D),
        Arguments.of("foo = 43.55E+034", 43.55E34D),
        Arguments.of("foo = 43.55E+0034", 43.55E34D),
        Arguments.of("foo = 43.557_654E-34", 43.557654E-34D),
        Arguments.of("foo = 43.557_654E-034", 43.557654E-34D),
        Arguments.of("foo = 43.557_654E-0034", 43.557654E-34D),
        Arguments.of("foo = 1e06", 1E6),
        Arguments.of("foo = 1e006", 1E6),
        Arguments.of("foo = 1e+06", 1E6),
        Arguments.of("foo = 1e+006", 1E6),
        Arguments.of("foo = inf", Double.POSITIVE_INFINITY),
        Arguments.of("foo = +inf", Double.POSITIVE_INFINITY),
        Arguments.of("foo = -inf", Double.NEGATIVE_INFINITY),
        Arguments.of("foo = nan", Double.NaN),
        Arguments.of("foo = +nan", Double.NaN),
        Arguments.of("foo = -nan", Double.NaN)
    );
    // @formatter:on
  }

  @Test
  void shouldParseBoolean() {
    TomlParseResult result = Toml.parse("foo = true");
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(Boolean.TRUE, result.getBoolean("foo"));
    TomlParseResult result2 = Toml.parse("\nfoo=false");
    assertFalse(result2.hasErrors(), () -> joinErrors(result2));
    assertEquals(Boolean.FALSE, result2.getBoolean("foo"));
  }

  @ParameterizedTest
  @MethodSource("offsetDateSupplier")
  void shouldParseOffsetDateTime(String input, OffsetDateTime expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getOffsetDateTime("foo"));
  }

  static Stream<Arguments> offsetDateSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 1937-07-18T03:25:43-04:00", OffsetDateTime.parse("1937-07-18T03:25:43-04:00")),
        Arguments.of("foo = 1937-07-18 11:44:02+18:00", OffsetDateTime.parse("1937-07-18T11:44:02+18:00")),
        Arguments.of("foo = 0000-07-18 11:44:02.00+18:00", OffsetDateTime.parse("0000-07-18T11:44:02+18:00")),
        Arguments.of("foo = 1979-05-27T07:32:00Z\nbar = 1979-05-27T00:32:00-07:00\n",
            OffsetDateTime.parse("1979-05-27T07:32:00Z")),
        Arguments.of("bar = 1979-05-27T07:32:00Z\nfoo = 1979-05-27T00:32:00-07:00\n",
            OffsetDateTime.parse("1979-05-27T00:32:00-07:00")),
        Arguments.of("foo = 1937-07-18 11:44:02.334543+18:00",
            OffsetDateTime.parse("1937-07-18T11:44:02.334543+18:00")),
        Arguments.of("foo = 1937-07-18 11:44:02Z", OffsetDateTime.parse("1937-07-18T11:44:02+00:00"))
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("localDateTimeSupplier")
  void shouldParseLocalDateTime(String input, LocalDateTime expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getLocalDateTime("foo"));
  }

  static Stream<Arguments> localDateTimeSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 1937-07-18T03:25:43", LocalDateTime.parse("1937-07-18T03:25:43")),
        Arguments.of("foo = 1937-07-18 11:44:02", LocalDateTime.parse("1937-07-18T11:44:02")),
        Arguments.of("foo = 0000-07-18 11:44:02.00", LocalDateTime.parse("0000-07-18T11:44:02")),
        Arguments.of("foo = 1937-07-18 11:44:02.334543", LocalDateTime.parse("1937-07-18T11:44:02.334543")),
        Arguments.of("foo = 1937-07-18 11:44:02", LocalDateTime.parse("1937-07-18T11:44:02"))
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("localDateSupplier")
  void shouldParseLocalDate(String input, LocalDate expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getLocalDate("foo"));
  }

  static Stream<Arguments> localDateSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 1937-07-18", LocalDate.parse("1937-07-18")),
        Arguments.of("foo = 1937-07-18", LocalDate.parse("1937-07-18")),
        Arguments.of("foo = 0000-07-18", LocalDate.parse("0000-07-18")),
        Arguments.of("foo = 1937-07-18", LocalDate.parse("1937-07-18")),
        Arguments.of("foo = 1937-07-18", LocalDate.parse("1937-07-18"))
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("localTimeSupplier")
  void shouldParseLocalTime(String input, LocalTime expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.getLocalTime("foo"));
  }

  static Stream<Arguments> localTimeSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = 03:25:43", LocalTime.parse("03:25:43")),
        Arguments.of("foo = 11:44:02", LocalTime.parse("11:44:02")),
        Arguments.of("foo = 11:44:02.00", LocalTime.parse("11:44:02")),
        Arguments.of("foo = 11:44:02.334543", LocalTime.parse("11:44:02.334543")),
        Arguments.of("foo = 11:44:02", LocalTime.parse("11:44:02"))
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("arraySupplier")
  void shouldParseArray(String input, Object[] expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    TomlArray array = result.getArray("foo");
    assertNotNull(array);
    assertEquals(expected.length, array.size());
    assertTomlArrayEquals(expected, array);
  }

  static Stream<Arguments> arraySupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = []", new Object[0]),
        Arguments.of("foo = [\n]", new Object[0]),
        Arguments.of("foo = [1]", new Object[] {1L}),
        Arguments.of("foo = [ \"bar\"\n]", new Object[] {"bar"}),
        Arguments.of("foo = [11:44:02]", new Object[] {LocalTime.parse("11:44:02")}),
        Arguments.of("foo = [1993-08-04]", new Object[] {LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [11:44:02,]", new Object[] {LocalTime.parse("11:44:02")}),
        Arguments.of("foo = [\n'bar', #baz\n]", new Object[] {"bar"}),
        Arguments.of("foo = ['bar', 'baz']", new Object[] {"bar", "baz"}),
        Arguments.of("foo = [1993-08-04,1993-08-04]",
                new Object[] {LocalDate.of(1993, 8, 4), LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [1993-08-04,1993-08-04,]",
                new Object[] {LocalDate.of(1993, 8, 4), LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [1993-08-04,1993-08-04   ]",
                new Object[] {LocalDate.of(1993, 8, 4), LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [ 1993-08-04 , 1993-08-04   ]",
                new Object[] {LocalDate.of(1993, 8, 4), LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [ 1993-08-04 , 1993-08-04   , ]",
                new Object[] {LocalDate.of(1993, 8, 4), LocalDate.of(1993, 8, 4)}),
        Arguments.of("foo = [\n'''bar\nbaz''',\n'baz'\n]", new Object[] {"bar" + System.lineSeparator() + "baz", "baz"}),
        Arguments.of("foo = [['bar']]", new Object[] {new Object[] {"bar"}}));
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("heterogeneousArraySupplier")
  void shouldParseHeterogeneousArray(String input, Object[] expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    TomlArray array = result.getArray("foo");
    assertNotNull(array);
    assertEquals(expected.length, array.size());
    assertTomlArrayEquals(expected, array);
  }

  static Stream<Arguments> heterogeneousArraySupplier() {
    // @formatter:off
    return Stream
        .of(
            Arguments.of("foo = [1, 'a']", new Object[] {1L, "a"}));
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("tableSupplier")
  void shouldParseTable(String input, String key, Object expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.get(key));
  }

  static Stream<Arguments> tableSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("[foo]\nbar = 'baz'", "foo.bar", "baz"),
        Arguments.of("[foo] #foo.bar\nbar = 'baz'", "foo.bar", "baz"),
        Arguments.of("[foo]\n[foo.bar]\nbaz = 'buz'", "foo.bar.baz", "buz"),
        Arguments.of("[foo.bar]\nbaz=1\n[foo]\nbaz=2", "foo.baz", 2L));
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("inlineTableSupplier")
  void shouldParseInlineTable(String input, String key, Object expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expected, result.get(key));
  }

  static Stream<Arguments> inlineTableSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("foo = {}", "foo.bar", null),
        Arguments.of("foo = { bar = 'baz' }", "foo.bar", "baz"),
        Arguments.of("foo = { bar = 'baz', baz.buz = 2 }", "foo.baz.buz", 2L),
        Arguments.of("foo = { bar = ['baz', 'buz']   , baz  .   buz = 2 }", "foo.baz.buz", 2L),
        Arguments.of("foo = { bar = ['baz',\n'buz'\n], baz.buz = 2 }", "foo.baz.buz", 2L),
        Arguments.of("bar = { bar = ['baz',\n'buz'\n], baz.buz = 2 }\nfoo=2\n", "foo", 2L)
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("arrayTableSupplier")
  void shouldParseArrayTable(String input, Object[] path, Object expected) {
    TomlParseResult result = Toml.parse(input);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    Object element = result;
    for (Object step : path) {
      if (step instanceof String) {
        assertTrue(element instanceof TomlTable);
        element = ((TomlTable) element).get((String) step);
      } else if (step instanceof Integer) {
        assertTrue(element instanceof TomlArray);
        element = ((TomlArray) element).get((Integer) step);
      } else {
        fail("path not found");
      }
    }
    assertEquals(expected, element);
  }

  static Stream<Arguments> arrayTableSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("[[foo]]\nbar = 'baz'", new Object[] {"foo", 0, "bar"}, "baz"),
        Arguments.of("[[foo]] #foo.bar\nbar = 'baz'", new Object[] {"foo", 0, "bar"}, "baz"),
        Arguments.of("[[foo]] \n   bar = 'buz'\nbuz=1\n", new Object[] {"foo", 0, "buz"}, 1L),
        Arguments.of("[[foo]] \n   bar = 'buz'\n[[foo]]\nbar=1\n", new Object[] {"foo", 0, "bar"}, "buz"),
        Arguments.of("[[foo]] \n   bar = 'buz'\n[[foo]]\nbar=1\n", new Object[] {"foo", 1, "bar"}, 1L),
        Arguments.of("[[foo]]\nbar=1\n[[foo]]\nbar=2\n", new Object[] {"foo", 0, "bar"}, 1L),
        Arguments.of("[[foo]]\nbar=1\n[[foo]]\nbar=2\n", new Object[] {"foo", 1, "bar"}, 2L),
        Arguments.of("[[foo]]\n\n[foo.bar]\n\nbaz=2\n\n", new Object[] {"foo", 0, "bar", "baz"}, 2L),
        Arguments.of(
                "[[foo]]\n[[foo.bar]]\n[[foo.baz]]\n[foo.bar.baz]\nbuz=2\n[foo.baz.buz]\nbiz=3\n",
                new Object[] {"foo", 0, "bar", 0, "baz", "buz"}, 2L),
        Arguments.of(
                "[[foo]]\n[[foo.bar]]\n[[foo.baz]]\n[foo.bar.baz]\nbuz=2\n[foo.baz.buz]\nbiz=3\n",
                new Object[] {"foo", 0, "baz", 0, "buz", "biz"}, 3L)
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("errorCaseSupplier")
  void shouldHandleParseErrors(String input, int line, int column, String expected) {
    TomlParseResult result = Toml.parse(input);
    List<TomlParseError> errors = result.errors();
    assertFalse(errors.isEmpty());
    assertEquals(expected, errors.get(0).getMessage(), () -> joinErrors(result));
    assertEquals(line, errors.get(0).position().line());
    assertEquals(column, errors.get(0).position().column());
  }

  static Stream<Arguments> errorCaseSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("\"foo\"", 1, 6, "Unexpected end of input, expected . or ="),
        Arguments.of("foo", 1, 4, "Unexpected end of input, expected . or ="),
        Arguments.of("foo  \n", 1, 6, "Unexpected end of line, expected . or ="),
        Arguments.of("foo =", 1, 6, "Unexpected end of input, expected ', \", ''', \"\"\", a number, a boolean, a date/time, an array, or a table"),
        Arguments.of("foo = 0b", 1, 8, "Unexpected 'b', expected a newline or end-of-input"),
        Arguments.of("foo = +", 1, 7, "Unexpected '+', expected ', \", ''', \"\"\", a number, a boolean, a date/time, an array, or a table"),
        Arguments.of("=", 1, 1, "Unexpected '=', expected a-z, A-Z, 0-9, ', \", a table key, a newline, or end-of-input"),
        Arguments.of("\"foo \nbar\" = 1", 1, 6, "Unexpected end of line, expected \" or a character"),
        Arguments.of("foo = \"bar \\y baz\"", 1, 12, "Invalid escape sequence '\\y'"),
        Arguments.of("\u0011abc = 'foo'", 1, 1, "Unexpected '\\u0011', expected a-z, A-Z, 0-9, ', \", a table key, a newline, or end-of-input"),
        Arguments.of(" \uDBFF\uDFFFAAabc='foo'", 1, 2, "Unexpected '\\U0010ffff', expected a-z, A-Z, 0-9, ', \", a table key, a newline, or end-of-input"),

        Arguments.of("foo = 1234567891234567891233456789", 1, 7, "Integer is too large"),

        Arguments.of("\n\nfoo    =    \t    +1E1000", 3, 18, "Float is too large"),
        Arguments.of("foo = +1E-1000", 1, 7, "Float is too small"),
        Arguments.of("foo = 0.000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000001", 1, 7, "Float is too small"),

        Arguments.of("\nfoo = 1937-47-18-00:00:00-04:00", 2, 17, "Unexpected '-', expected a newline or end-of-input"),
        Arguments.of("\nfoo = 1937-47-18  00:00:00-04:00", 2, 19, "Unexpected '00', expected a newline or end-of-input"),
        Arguments.of("\nfoo = 2334567891233457889-07-18T00:00:00-04:00", 2, 7, "Invalid year (valid range 0000..9999)"),
        Arguments.of("\nfoo = 2-07-18T00:00:00-04:00", 2, 7, "Invalid year (valid range 0000..9999)"),
        Arguments.of("\nfoo = -07-18T00:00:00-04:00", 2, 9, "Unexpected '7-18T00', expected a newline or end-of-input"),
        Arguments.of("\nfoo = 1937-47-18T00:00:00-04:00", 2, 12, "Invalid month (valid range 01..12)"),
        Arguments.of("\nfoo = 1937-7-18T00:00:00-04:00", 2, 12, "Invalid month (valid range 01..12)"),
        Arguments.of("\nfoo = 1937-00-18T00:00:00-04:00", 2, 12, "Invalid month (valid range 01..12)"),
        Arguments.of("\nfoo = 1937--18T00:00:00-04:00", 2, 12, "Unexpected '-', expected a date/time"),
        Arguments.of("\nfoo = 1937-07-48T00:00:00-04:00", 2, 15, "Invalid day (valid range 01..28/31)"),
        Arguments.of("\nfoo = 1937-07-8T00:00:00-04:00", 2, 15, "Invalid day (valid range 01..28/31)"),
        Arguments.of("\nfoo = 1937-07-00T00:00:00-04:00", 2, 15, "Invalid day (valid range 01..28/31)"),
        Arguments.of("\nfoo = 1937-02-30T00:00:00-04:00", 2, 15, "Invalid date 'FEBRUARY 30'"),
        Arguments.of("\nfoo = 1937-07-18T30:00:00-04:00", 2, 18, "Invalid hour (valid range 00..23)"),
        Arguments.of("\nfoo = 1937-07-18T3:00:00-04:00", 2, 18, "Invalid hour (valid range 00..23)"),
        Arguments.of("\nfoo = 1937-07-18T13:70:00-04:00", 2, 21, "Invalid minutes (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:7:00-04:00", 2, 21, "Invalid minutes (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:92-04:00", 2, 24, "Invalid seconds (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:2-04:00", 2, 24, "Invalid seconds (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:02.0000000009-04:00", 2, 27, "Invalid nanoseconds (valid range 0..999999999)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:02.-04:00", 2, 27, "Unexpected '-', expected a date/time"),
        Arguments.of("\nfoo = 1937-07-18T13:55:26-25:00", 2, 26, "Invalid zone offset hours (valid range -18..+18)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:26-:00", 2, 27, "Unexpected ':', expected a date/time"),
        Arguments.of("\nfoo = 1937-07-18T13:55:26-04:60", 2, 30, "Invalid zone offset minutes (valid range 0..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:26-18:30", 2, 26, "Invalid zone offset (valid range -18:00..+18:00)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:26-18:", 2, 30, "Unexpected end of input, expected a date/time"),

        Arguments.of("\nfoo = 2334567891233457889-07-18T00:00:00", 2, 7, "Invalid year (valid range 0000..9999)"),
        Arguments.of("\nfoo = 1937-47-18T00:00:00", 2, 12, "Invalid month (valid range 01..12)"),
        Arguments.of("\nfoo = 1937-07-48T00:00:00", 2, 15, "Invalid day (valid range 01..28/31)"),
        Arguments.of("\nfoo = 1937-07-18T30:00:00", 2, 18, "Invalid hour (valid range 00..23)"),
        Arguments.of("\nfoo = 1937-07-18T13:70:00", 2, 21, "Invalid minutes (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:92", 2, 24, "Invalid seconds (valid range 00..59)"),
        Arguments.of("\nfoo = 1937-07-18T13:55:02.0000000009", 2, 27, "Invalid nanoseconds (valid range 0..999999999)"),

        Arguments.of("\nfoo = 2334567891233457889-07-18", 2, 7, "Invalid year (valid range 0000..9999)"),
        Arguments.of("\nfoo = 1937-47-18", 2, 12, "Invalid month (valid range 01..12)"),
        Arguments.of("\nfoo = 1937-07-48", 2, 15, "Invalid day (valid range 01..28/31)"),

        Arguments.of("\nfoo = 30:00:00", 2, 7, "Invalid hour (valid range 00..23)"),
        Arguments.of("\nfoo = 13:70:00", 2, 10, "Invalid minutes (valid range 00..59)"),
        Arguments.of("\nfoo = 13:55:92", 2, 13, "Invalid seconds (valid range 00..59)"),
        Arguments.of("\nfoo = 13:55:02.0000000009", 2, 16, "Invalid nanoseconds (valid range 0..999999999)"),
        Arguments.of("\nfoo = 13:55:02,", 2, 15, "Unexpected ',', expected a newline or end-of-input"),
        Arguments.of("\nfoo = 13:55:02 , ", 2, 16, "Unexpected ',', expected a newline or end-of-input"),

        Arguments.of("foo = [", 1, 8, "Unexpected end of input, expected ], ', \", ''', \"\"\", a number, a boolean, a date/time, an array, a table, or a newline"),
        Arguments.of("foo = [ 1\n", 2, 1, "Unexpected end of input, expected ] or a newline"),
        Arguments.of("foo = [ 1, 'bar ]\n", 1, 18, "Unexpected end of line, expected '"),

        Arguments.of("[]", 1, 1, "Empty table key"),
        Arguments.of("[foo] bar='baz'", 1, 7, "Unexpected 'bar', expected a newline or end-of-input"),
        Arguments.of("foo='bar'\n[foo]\nbar='baz'", 2, 1, "foo previously defined at line 1, column 1"),
        Arguments.of("[foo]\nbar='baz'\n[foo]\nbaz=1", 3, 1, "foo previously defined at line 1, column 1"),
        Arguments.of("[foo]\nbar='baz'\n[foo.bar]\nbaz=1", 3, 1, "foo.bar previously defined at line 2, column 1"),

        Arguments.of("foo = {", 1, 8, "Unexpected end of input, expected a-z, A-Z, 0-9, }, ', or \""),
        Arguments.of("foo = { bar = 1,\nbaz = 2 }", 1, 17, "Unexpected end of line, expected a-z, A-Z, 0-9, ', or \""),
        Arguments.of("foo = { bar = 1\nbaz = 2 }", 1, 16, "Unexpected end of line, expected }"),
        Arguments.of("foo = { bar = 1 baz = 2 }", 1, 17, "Unexpected 'baz', expected } or a comma"),

        Arguments.of("[foo]\nbar=1\n[[foo]]\nbar=2\n", 3, 1, "foo is not an array (previously defined at line 1, column 1)"),
        Arguments.of("foo = [1]\n[[foo]]\nbar=2\n", 2, 1, "foo previously defined as a literal array at line 1, column 1"),
        Arguments.of("foo = []\n[[foo]]\nbar=2\n", 2, 1, "foo previously defined as a literal array at line 1, column 1"),
        Arguments.of("[[foo.bar]]\n[foo]\nbaz=2\nbar=3\n", 4, 1, "bar previously defined at line 1, column 1"),
        Arguments.of("[[foo]]\nbaz=1\n[[foo.bar]]\nbaz=2\n[foo.bar]\nbaz=3\n", 5, 1, "foo.bar previously defined at line 3, column 1")
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("errorCaseSupplier_V0_5_0")
  void shouldHandleParseErrors_V0_5_0(String input, int line, int column, String expected) {
    TomlParseResult result = Toml.parse(input, TomlVersion.V0_5_0);
    List<TomlParseError> errors = result.errors();
    assertFalse(errors.isEmpty());
    assertEquals(expected, errors.get(0).getMessage(), () -> joinErrors(result));
    assertEquals(line, errors.get(0).position().line());
    assertEquals(column, errors.get(0).position().column());
  }

  static Stream<Arguments> errorCaseSupplier_V0_5_0() {
    // @formatter:off
    return Stream.of(
        Arguments.of("\"foo\tbar\" = 1", 1, 5, "Use \\t to represent a tab in a string (TOML versions before 1.0.0)"),
        Arguments.of("foo = [ 1, 'bar' ]", 1, 12, "Cannot add a string to an array containing integers")
    );
    // @formatter:on
  }

  @Test
  void testTomlV0_4_0Example() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/example-v0.4.0.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is, TomlVersion.V0_4_0);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals("value", result.getString("table.key"));
    assertEquals("Preston-Werner", result.getString("table.inline.name.last"));
    assertEquals("<\\i\\c*\\s*>", result.getString("string.literal.regex"));
    assertEquals(2L, result.getArray("array.key5").getLong(1));
    assertEquals(
        "granny smith",
        result.getArray("fruit").getTable(0).getArray("variety").getTable(1).getString("name"));
  }

  @Test
  void testHardExample() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/hard_example.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is, TomlVersion.V0_4_0);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals("You'll hate me after this - #", result.getString("the.test_string"));
    assertEquals(" And when \"'s are in the string, along with # \"", result.getString("the.hard.harder_test_string"));
    assertEquals("]", result.getArray("the.hard.'bit#'.multi_line_array").getString(0));
  }

  @Test
  void testHardExampleUnicode() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/hard_example_unicode.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is, TomlVersion.V0_4_0);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals("Ýôú'ℓℓ λáƭè ₥è áƒƭèř ƭλïƨ - #", result.getString("the.test_string"));
    assertEquals(" Âñδ ωλèñ \"'ƨ ářè ïñ ƭλè ƨƭřïñϱ, áℓôñϱ ωïƭλ # \"", result.getString("the.hard.harder_test_string"));
    assertEquals("]", result.getArray("the.hard.'βïƭ#'.multi_line_array").getString(0));
  }

  @Test
  void testSpecExample() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/toml-v0.5.0-spec-example.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is, TomlVersion.V0_4_0);
    assertFalse(result.hasErrors(), () -> joinErrors(result));

    assertEquals("Tom Preston-Werner", result.getString("owner.name"));
    assertEquals(OffsetDateTime.parse("1979-05-27T07:32:00-08:00"), result.getOffsetDateTime("owner.dob"));

    assertEquals("10.0.0.2", result.getString("servers.beta.ip"));
    TomlArray clientHosts = result.getArray("clients.hosts");
    assertNotNull(clientHosts);
    assertTrue(clientHosts.containsStrings());
    assertEquals(Arrays.asList("alpha", "omega"), clientHosts.toList());
  }

  @Test
  void testArrayTables() throws Exception {
    String expectedJson =
        new Scanner(this.getClass().getResourceAsStream("/org/tomlj/array_table_example.json"), "UTF-8")
            .useDelimiter("\\A")
            .next();
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/array_table_example.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expectedJson.replace("\n", System.lineSeparator()), result.toJson());
  }

  @Test
  void testDottedKeyOrder() throws Exception {
    TomlParseResult result1 = Toml.parse("[dog.\"tater.man\"]\ntype.name = \"pug\"");
    assertFalse(result1.hasErrors(), () -> joinErrors(result1));
    TomlParseResult result2 = Toml.parse("a.b.c = 1\na.d = 2\n");
    assertFalse(result2.hasErrors(), () -> joinErrors(result2));
    TomlParseResult result3 = Toml.parse("# THIS IS INVALID\na.b = 1\na.b.c = 2\n");
    assertTrue(result3.hasErrors());
  }

  @Test
  void testSpacesInKeys() throws Exception {
    TomlParseResult result1 = Toml.parse("\"Dog type\" = \"pug\"");
    assertFalse(result1.hasErrors(), () -> joinErrors(result1));
    assertEquals("pug", result1.getString("\"Dog type\""));
    assertEquals("pug", result1.getString("Dog type"));

    TomlParseResult result2 = Toml.parse("[\"Dog 1\"]\n  type = \"pug\"");
    assertFalse(result2.hasErrors(), () -> joinErrors(result2));
    assertEquals("pug", result2.getString("\"Dog 1\".type"));
    assertEquals("pug", result2.getString("Dog 1.type"));

    TomlParseResult result3 = Toml.parse("[pets.\"Dog 1\"]\n  type = \"pug\"");
    assertFalse(result3.hasErrors(), () -> joinErrors(result3));
    assertEquals("pug", result3.getString("pets.\"Dog 1\".type"));
    assertEquals("pug", result3.getString("pets.Dog 1.type"));
    assertEquals("pug", result3.getString("pets.Dog 1  .type"));
    assertEquals("pug", result3.getString("pets.  Dog 1.type"));
  }

  @Test
  void testQuotesInJson() throws Exception {
    TomlParseResult result1 = Toml.parse("key = \"this is 'a test' with single quotes\"");
    assertFalse(result1.hasErrors(), () -> joinErrors(result1));
    String expected1 = "{\n  \"key\" : \"this is 'a test' with single quotes\"\n}\n";
    assertEquals(expected1.replace("\n", System.lineSeparator()), result1.toJson());

    TomlParseResult result2 = Toml.parse("[\"dog 'type'\"]\ntype = \"pug\"");
    assertFalse(result2.hasErrors(), () -> joinErrors(result2));
    String expected2 = "{\n  \"dog 'type'\" : {\n    \"type\" : \"pug\"\n  }\n}\n";
    assertEquals(expected2.replace("\n", System.lineSeparator()), result2.toJson());

    TomlParseResult result3 = Toml.parse("key = \"this is \\\"a test\\\" with double quotes\"");
    assertFalse(result3.hasErrors(), () -> joinErrors(result3));
    String expected3 = "{\n  \"key\" : \"this is \\\"a test\\\" with double quotes\"\n}\n";
    assertEquals(expected3.replace("\n", System.lineSeparator()), result3.toJson());
  }

  @Test
  void testBackslashesInJson() throws Exception {
    TomlParseResult result1 = Toml.parse("path = 'C:\\Users\\dog\\catsihate'");
    assertFalse(result1.hasErrors(), () -> joinErrors(result1));
    String expected = "{\n  \"path\" : \"C:\\\\Users\\\\dog\\\\catsihate\"\n}\n";
    assertEquals(expected.replace("\n", System.lineSeparator()), result1.toJson());
  }

  @Test
  void testOrderPreservationInJson() throws Exception {
    String expectedJson =
        new Scanner(this.getClass().getResourceAsStream("/org/tomlj/toml-v0.5.0-spec-example.json"), "UTF-8")
            .useDelimiter("\\A")
            .next();
    InputStream is = this.getClass().getResourceAsStream("/org/tomlj/toml-v0.5.0-spec-example.toml");
    assertNotNull(is);
    TomlParseResult result = Toml.parse(is);
    assertFalse(result.hasErrors(), () -> joinErrors(result));
    assertEquals(expectedJson.replace("\n", System.lineSeparator()), result.toJson());
  }

  private String joinErrors(TomlParseResult result) {
    return result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("\n"));
  }

  private static void assertTomlArrayEquals(Object[] expected, TomlArray array) {
    for (int i = 0; i < expected.length; ++i) {
      Object obj = array.get(i);
      if (expected[i] instanceof Object[]) {
        assertTrue(obj instanceof TomlArray);
        assertTomlArrayEquals((Object[]) expected[i], (TomlArray) obj);
      } else {
        assertEquals(expected[i], obj);
      }
    }
  }

  private static boolean arrayEquals(TomlArray array, TomlArray array2) {
    if (array.size() != array2.size()) {
      return false;
    }

    for (int i = 0; i < array.size(); i++) {
      Object value1 = array.get(i);
      Object value2 = array2.get(i);

      Optional<TomlType> tomlType = typeFor(value1);
      assert tomlType.isPresent();

      Optional<TomlType> tomlType2 = typeFor(value2);
      assert tomlType2.isPresent();

      if (tomlType.get() != tomlType2.get()) {
        return false;
      }

      if (tomlType.get().equals(TABLE)) {
        if (!tableEquals((TomlTable) value1, (TomlTable) value2)) {
          return false;
        }
      } else if (tomlType.get().equals(ARRAY)) {
        if (!arrayEquals((TomlArray) value1, (TomlArray) value2)) {
          return false;
        }
      } else {
        if (!value1.equals(value2)) {
          return false;
        }
      }
    }

    return true;
  }

  private static boolean tableEquals(TomlTable table, TomlTable table2) {
    if (table.entrySet().size() != table2.entrySet().size()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : table.entrySet()) {
      String key = entry.getKey();
      if (!table2.keySet().contains(key)) {
        return false;
      }

      Object value1 = entry.getValue();
      Optional<Map.Entry<String, Object>> value2Entry =
          table2.entrySet().stream().filter(entry2 -> entry2.getKey().equals(key)).findFirst();

      if (!value2Entry.isPresent()) {
        return false;
      }

      Object value2 = value2Entry.get().getValue();

      Optional<TomlType> tomlType = typeFor(value1);
      assert tomlType.isPresent();

      Optional<TomlType> tomlType2 = typeFor(value2);
      assert tomlType2.isPresent();

      if (tomlType.get() != tomlType2.get()) {
        return false;
      }

      if (tomlType.get().equals(TABLE)) {
        if (!tableEquals((TomlTable) value1, (TomlTable) value2)) {
          return false;
        }
      } else if (tomlType.get().equals(ARRAY)) {
        if (!arrayEquals((TomlArray) value1, (TomlArray) value2)) {
          return false;
        }
      } else {
        if (!value1.equals(value2)) {
          return false;
        }
      }
    }
    return true;
  }
}
