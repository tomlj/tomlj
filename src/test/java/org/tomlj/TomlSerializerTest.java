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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TomlSerializerTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("tableSupplier")
  void shouldSerializeTable(String description, TomlTable table, String expected) {
    assertSerializes(table, expected.replace("\n", System.lineSeparator()));
  }

  static Stream<Arguments> tableSupplier() {
    // @formatter:off
    return Stream.of(
        unchanged("bare and quoted keys", """
            bare_Key-1 = 1
            "a b" = 2
            "a.b" = 3
            "" = 4
            "é" = 5
            "k\\u0001\\"" = 6
            """),
        parsed("literal keys", """
            'lit' = 1
            'a\\b' = 2
            """, """
            lit = 1
            "a\\\\b" = 2
            """),
        unchanged("quoted keys in headers", """
            ["a b"."c.d".""]
            e = 1

            [[x."y z"]]
            f = 2
            """),
        parsed("string escapes", """
            s = "\\b\\t\\n\\f\\r"
            q = "say \\"hi\\" \\\\ back\\\\"
            """, """
            s = \"""
            \\b\\t
            \\f\\r\"""
            q = "say \\"hi\\" \\\\ back\\\\"
            """),
        parsed(
            "a multi-line string with a newline in the middle",
            "s = \"a\\nb\"\n",
            "s = \"\"\"\na\nb\"\"\"\n"),
        parsed(
            "a multi-line string with a trailing newline",
            "s = \"a\\n\"\n",
            "s = \"\"\"\na\n\"\"\"\n"),
        parsed(
            "a multi-line string starting with a newline",
            "s = \"\\nx\"\n",
            "s = \"\"\"\n\nx\"\"\"\n"),
        parsed(
            "a multi-line string containing a carriage return, escaped, and a raw newline",
            "s = \"a\\r\\nb\"\n",
            "s = \"\"\"\na\\r\nb\"\"\"\n"),
        parsed(
            "a run of three quotes inside a multi-line string",
            "s = \"a\\n\\\"\\\"\\\"b\"\n",
            "s = \"\"\"\na\n\"\"\\\"b\"\"\"\n"),
        parsed(
            "a multi-line string ending in one quote",
            "s = \"a\\nb\\\"\"\n",
            "s = \"\"\"\na\nb\"\"\"\"\n"),
        parsed(
            "a multi-line string ending in two quotes",
            "s = \"a\\nb\\\"\\\"\"\n",
            "s = \"\"\"\na\nb\"\"\"\"\"\n"),
        parsed(
            "a backslash directly before a newline in a multi-line string",
            "s = \"x\\\\\\ny\"\n",
            "s = \"\"\"\nx\\\\\ny\"\"\"\n"),
        parsed(
            "a tab and another control character in a multi-line string",
            "s = \"a\\n\\t\\u0001b\"\n",
            "s = \"\"\"\na\n\\t\\u0001b\"\"\"\n"),
        unchanged("a string with a newline inside an array", "a = [\"x\\ny\"]\n"),
        unchanged("a string with a newline inside an inline table", "a = [1, { c = \"x\\ny\" }]\n"),
        unchanged("a key containing a newline", "\"a\\nb\" = 1\n"),
        parsed("other control characters and DEL", """
            c = "\\u0000\\u0001\\u001F\\U0000001B\\u007F"
            """, """
            c = "\\u0000\\u0001\\u001f\\u001b\\u007f"
            """),
        parsed("non-ASCII characters", """
            s = "caf\\u00E9 \\U0001F600 \\u4E2D"
            """, """
            s = "café 😀 中"
            """),
        parsed("a literal string with quotes and a backslash", """
            s = 'a "b" \\c'
            """, """
            s = "a \\"b\\" \\\\c"
            """),
        parsed("floats", """
            a = nan
            b = -nan
            c = +inf
            d = -inf
            e = -0.0
            f = 1e300
            g = 6.02e-23
            h = 1.5
            """, """
            a = nan
            b = nan
            c = inf
            d = -inf
            e = -0.0
            f = 1.0E300
            g = 6.02E-23
            h = 1.5
            """),
        parsed("integers", """
            a = -9223372036854775808
            b = 0xff
            c = 1_000
            """, """
            a = -9223372036854775808
            b = 255
            c = 1000
            """),
        Arguments.of("dates and times", datesAndTimes(), """
            time = 07:32:00
            fraction = 07:32:05.12
            nanos = 07:32:05.000000001
            date = 1979-05-27
            local = 1979-05-27T07:32:00
            utc = 1979-05-27T07:32:00Z
            offset = 1979-05-27T07:32:00.5+05:30
            negative = 1979-05-27T07:32:00-07:00
            """),
        unchanged("dates and times directly before closing brackets", """
            a = [1, { b = [1979-05-27], c = 07:32:00 }]
            """),
        Arguments.of("values before sub-tables even when inserted after them", valuesInsertedAfterTables(), """
            name = "x"
            tags = ["a"]

            [server]
            port = 80
            host = "h"

            [server.tls]
            enabled = true
            """),
        unchanged("no header for a table holding only sub-tables, and a header for an empty table", """
            [a.b]
            c = 1

            [d]
            """),
        parsed("blank lines before later headers", """
            x = 1
            [a]
            y = 2
            [b]
            [b.c]
            z = 3
            """, """
            x = 1

            [a]
            y = 2

            [b.c]
            z = 3
            """),
        unchanged("arrays of tables with empty elements and sub-tables", """
            [[a]]

            [[a]]
            x = 1

            [a.b]
            y = 2

            [[a]]

            [a.c.d]
            z = 3
            """),
        unchanged("arrays of tables under an implicit table", """
            [[a.b]]
            x = 1

            [[a.b]]

            [[a.b.c]]
            y = 2
            """),
        parsed("an array of inline tables directly in a table", """
            a = [{ b = { c = 1 } }, {}]
            """, """
            [[a]]

            [a.b]
            c = 1

            [[a]]
            """),
        unchanged("an array exactly at the width limit", """
            a = ["%s"]
            """.formatted("x".repeat(72))),
        parsed("an array one column over the width limit", """
            a = ["%s"]
            """.formatted("x".repeat(73)), """
            a = [
              "%s",
            ]
            """.formatted("x".repeat(73))),
        unchanged("an array at the width limit in code points", """
            a = ["%s"]
            """.formatted("😀".repeat(72))),
        parsed("arrays in a multi-line array", """
            a = [[1, 2], ["%s"], ["%s"]]
            """.formatted("x".repeat(73), "y".repeat(74)), """
            a = [
              [1, 2],
              ["%s"],
              [
                "%s",
              ],
            ]
            """.formatted("x".repeat(73), "y".repeat(74))),
        unchanged("tables in mixed arrays", """
            a = [1, { b = 2, c = [3, { d = { e = 4 } }] }, {}]
            """),
        unchanged("an array of arrays of tables", """
            a = [[{ b = 1 }, { b = 2 }], [{}]]
            """),
        parsed("a long array inside an inline table", """
            a = [1, { b = ["%s", "y"] }]
            """.formatted("x".repeat(80)), """
            a = [
              1,
              { b = ["%s", "y"] },
            ]
            """.formatted("x".repeat(80))),
        unchanged("an empty array after a long key", """
            %s = []
            """.formatted("k".repeat(90)))
    );
    // @formatter:on
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("commentSupplier")
  void shouldSerializeComments(String description, TomlTable table, String expected) {
    assertSerializes(table, expected.replace("\n", System.lineSeparator()));
  }

  static Stream<Arguments> commentSupplier() {
    // @formatter:off
    return Stream.of(
        unchanged("a run above an entry and a comment after it", """
            # one
            # two
            x = 1  # after
            """),
        unchanged("comments on a table header", """
            # above
            [a]  # after
            x = 1
            """),
        unchanged("comments on an array of tables header", """
            [[x]]
            y = 1

            # above
            [[x]]  # after
            z = 2
            """),
        parsed("a comment on a table holding only sub-tables", """
            # above
            [a]
            [a.b]
            x = 1
            """, """
            # above
            [a]

            [a.b]
            x = 1
            """),
        unchanged("an unattached comment first in the root", """
            # unattached

            x = 1
            """),
        parsed("an unattached comment between two entries", """
            x = 1
            # unattached

            y = 2
            """, """
            x = 1

            # unattached

            y = 2
            """),
        unchanged("an unattached comment trailing a section a header follows", """
            [a]
            x = 1
            # trailing

            [b]
            y = 2
            """),
        unchanged("an unattached comment trailing the document", """
            [a]
            x = 1
            # trailing
            """),
        unchanged("an unattached comment between the headers of the root", """
            [a]
            x = 1

            # between

            [b]
            y = 2
            """),
        unchanged("an unattached comment at the start of a section", """
            [a]

            # leading

            x = 1
            """),
        unchanged("a comment with no text", """
            #
            x = 1
            """),
        unchanged("comments in an array", """
            a = [
              # above
              1,  # after
              # unattached

              2,
              # before the closing bracket
            ]
            """),
        unchanged("an array holding only a comment", """
            a = [
              # only a comment
            ]
            """),
        parsed("a comment above an element of an array of tables", """
            a = [
              # above
              { b = 1 },
            ]
            """, """
            # above
            [[a]]
            b = 1
            """),
        unchanged("an array of tables held by an entry with a comment", """
            # above
            a = [{ b = 1 }]
            """),
        unchanged("an unattached comment in an array of tables", """
            a = [
              # unattached

              { b = 1 },
            ]
            """)
    );
    // @formatter:on
  }

  @Test
  void shouldWriteAnInlineTableHoldingCommentsAsASection() {
    TomlTable table = parse("a = {\n  # unattached\n\n  b = 1,  # after\n}\n");
    assertSerializes(
        table,
        "[a]\n\n# unattached\n\nb = 1  # after\n".replace("\n", System.lineSeparator()),
        TomlVersion.V1_0_0);
  }

  @Test
  void shouldWriteAnInlineTableHoldingCommentsOverLines() {
    TomlTable table = parse("a = [1, {\n  # above\n  b = 2 }]\n");
    assertSerializes(table, """
        a = [
          1,
          {
            # above
            b = 2,
          },
        ]
        """.replace("\n", System.lineSeparator()), TomlVersion.LATEST);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("arraySupplier")
  void shouldSerializeArray(String description, TomlArray array, String expected) {
    assertSerializes(array, expected.replace("\n", System.lineSeparator()));
  }

  static Stream<Arguments> arraySupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("an empty array", MutableTomlArray.create(), "[]"),
        Arguments.of("a single-line array",
            MutableTomlArray.of(1, "two", List.of(3), Map.of("b", 2), List.of()),
            "[1, \"two\", [3], { b = 2 }, []]"),
        Arguments.of("an array exactly at the width limit",
            MutableTomlArray.of("x".repeat(76)),
            "[\"%s\"]".formatted("x".repeat(76))),
        Arguments.of("an array one column over the width limit",
            MutableTomlArray.of("x".repeat(77)),
            """
            [
              "%s",
            ]""".formatted("x".repeat(77))),
        Arguments.of("a multi-line array holding an array that fits",
            MutableTomlArray.of("x".repeat(40), List.of("y".repeat(40))),
            """
            [
              "%s",
              ["%s"],
            ]""".formatted("x".repeat(40), "y".repeat(40))),
        Arguments.of("an array of tables",
            parse("[[a]]\nb = 1\n[[a]]\nc = { d = 2 }\n").getArray("a"),
            "[{ b = 1 }, { c = { d = 2 } }]"),
        Arguments.of("an array holding comments",
            parse("a = [\n  # above\n  1,  # after\n]\n").getArray("a"),
            """
            [
              # above
              1,  # after
            ]""")
    );
    // @formatter:on
  }

  @Test
  void shouldSerializeNestedTableAsDocumentRoot() {
    TomlTable table = parse("[a]\nx = 1\n[a.b]\ny = 2\n[[a.c]]\n").getTable("a");
    assertNotNull(table);
    assertSerializes(table, "x = 1\n\n[b]\ny = 2\n\n[[c]]\n".replace("\n", System.lineSeparator()));
  }

  @ParameterizedTest
  @MethodSource("multilineStringRoundTripSupplier")
  void shouldRoundTripMultilineStringValues(String value) {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("s", value);

    String toml = table.toToml();
    for (TomlVersion version : List.of(TomlVersion.V1_0_0, TomlVersion.LATEST)) {
      TomlParseResult result = Toml.parse(toml, version);
      assertFalse(result.hasErrors(), () -> toml + "\n" + result.errors());
      assertEquals(value, result.getString("s"), () -> toml);
    }
  }

  static Stream<String> multilineStringRoundTripSupplier() {
    // @formatter:off
    return Stream.of(
        "a\nb", "a\n", "\n", "\n\n", "\nx", "a\r\nb", "\r\n", "\"", "\"\"", "\"\"\"", "\"\"\"\"\"\"\"",
        "x\n\"", "x\n\"\"", "x\n\"\"\"", "\\\n", "a\\\nb", "\t\n\u0000\u007F", "trailing space \nx", "😀\n日本");
    // @formatter:on
  }

  private static Arguments unchanged(String description, String toml) {
    return Arguments.of(description, parse(toml), toml);
  }

  private static Arguments parsed(String description, String toml, String expected) {
    return Arguments.of(description, parse(toml), expected);
  }

  private static TomlTable datesAndTimes() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("time", LocalTime.of(7, 32));
    table.set("fraction", LocalTime.of(7, 32, 5, 120_000_000));
    table.set("nanos", LocalTime.of(7, 32, 5, 1));
    table.set("date", LocalDate.of(1979, 5, 27));
    table.set("local", LocalDateTime.of(1979, 5, 27, 7, 32));
    table.set("utc", OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 0, ZoneOffset.UTC));
    table.set("offset", OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 500_000_000, ZoneOffset.ofHoursMinutes(5, 30)));
    table.set("negative", OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 0, ZoneOffset.ofHours(-7)));
    return table;
  }

  private static TomlTable valuesInsertedAfterTables() {
    MutableTomlTable doc = MutableTomlTable.create();
    MutableTomlTable server = doc.getOrCreateTable("server");
    server.getOrCreateTable("tls").set("enabled", true);
    server.set("port", 80);
    doc.set("name", "x");
    server.set("host", "h");
    doc.set("tags", MutableTomlArray.of("a"));
    return doc;
  }

  private static TomlParseResult parse(String toml) {
    TomlParseResult result = Toml.parse(toml, TomlVersion.LATEST);
    assertFalse(result.hasErrors(), () -> toml + "\n" + result.errors());
    return result;
  }

  private static void assertSerializes(TomlTable table, String expected) {
    assertSerializes(table, expected, TomlVersion.V1_0_0);
  }

  private static void assertSerializes(TomlTable table, String expected, TomlVersion version) {
    String toml = table.toToml();
    assertEquals(expected, toml);

    TomlParseResult reparsed = Toml.parse(toml, version);
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    assertTrue(Toml.equals(table, reparsed), () -> toml);
    TomlAssertions.assertSameComments(table, reparsed);
  }

  private static void assertSerializes(TomlArray array, String expected) {
    String toml = array.toToml();
    assertEquals(expected, toml);

    TomlParseResult reparsed = Toml.parse("a = " + toml, TomlVersion.V1_0_0);
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    TomlArray reparsedArray = reparsed.getArray("a");
    assertNotNull(reparsedArray);
    assertTrue(Toml.equals(array, reparsedArray), () -> toml);
    TomlAssertions.assertSameComments(array, reparsedArray);
  }
}
