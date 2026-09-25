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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SerializerTest {

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
  @MethodSource("tableWithOptionsSupplier")
  void shouldSerializeTableWithOptions(String description, TomlTable table, TomlWriteOptions options, String expected) {
    assertSerializes(table, options, expected.replace("\n", options.lineSeparator()));
  }

  static Stream<Arguments> tableWithOptionsSupplier() {
    // @formatter:off
    String document = """
        title = "Example"

        [a.b.c]
        d = 1

        [server]
        host = "localhost"

        [server.tls]
        enabled = true

        [[products]]
        sku = 1

        [products.size]
        width = 2

        [[products]]
        sku = 2
        """;
    String longArray = "a = [" + String.join(", ", Collections.nCopies(1000, "[\"xxxxxxxxxx\", 1]")) + "]\n";
    TomlWriteOptions toml10Options = TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0);
    TomlWriteOptions toml11Options = TomlWriteOptions.defaults().withVersion(TomlVersion.V1_1_0);
    TomlWriteOptions aligned = TomlWriteOptions.defaults().withIndent(2).withEntriesAlignedWithHeaders(true);
    return Stream.of(
        parsed("an indent of 2", document, TomlWriteOptions.defaults().withIndent(2), """
            title = "Example"

                [a.b.c]
                  d = 1

            [server]
              host = "localhost"

              [server.tls]
                enabled = true

            [[products]]
              sku = 1

              [products.size]
                width = 2

            [[products]]
              sku = 2
            """),
        parsed("an indent of 4", document, TomlWriteOptions.defaults().withIndent(4), """
            title = "Example"

                    [a.b.c]
                        d = 1

            [server]
                host = "localhost"

                [server.tls]
                    enabled = true

            [[products]]
                sku = 1

                [products.size]
                    width = 2

            [[products]]
                sku = 2
            """),
        parsed("comments indented like the entries around them", """
            [a]
            # unattached

            # above
            b = 1  # after
            """, TomlWriteOptions.defaults().withIndent(2), """
            [a]

              # unattached

              # above
              b = 1  # after
            """),
        parsed("a multi-line array under an indented key", """
            [t]
            list = ["%s", ["%s"]]
            """.formatted("x".repeat(74), "y".repeat(80)), TomlWriteOptions.defaults().withIndent(2), """
            [t]
              list = [
                "%s",
                [
                  "%s",
                ],
              ]
            """.formatted("x".repeat(74), "y".repeat(80))),
        parsed(
            "a multi-line string under an indented key, with content lines unindented",
            "[t]\ns = \"a\\nb\"\n",
            TomlWriteOptions.defaults().withIndent(2),
            "[t]\n  s = \"\"\"\na\nb\"\"\"\n"),
        parsed("an array at the width limit without indentation", """
            [t]
            list = ["%s"]
            """.formatted("x".repeat(69)), TomlWriteOptions.defaults(), """
            [t]
            list = ["%s"]
            """.formatted("x".repeat(69))),
        parsed("the same array over the width limit with indentation", """
            [t]
            list = ["%s"]
            """.formatted("x".repeat(69)), TomlWriteOptions.defaults().withIndent(2), """
            [t]
              list = [
                "%s",
              ]
            """.formatted("x".repeat(69))),
        parsed("a maximum line width of 0 for TOML 1.0.0", """
            a = [1, [2], []]
            b = []
            c = [3, { d = [4, 5], e = [] }]
            """, toml10Options.withMaxLineWidth(0), """
            a = [
              1,
              [
                2,
              ],
              [],
            ]
            b = []
            c = [
              3,
              { d = [4, 5], e = [] },
            ]
            """),
        parsed("a maximum line width of 0 for TOML 1.1.0", """
            a = [1, [2], []]
            b = []
            c = [3, { d = [4, 5], e = [] }]
            """, toml11Options.withMaxLineWidth(0), """
            a = [
              1,
              [
                2,
              ],
              [],
            ]
            b = []
            c = [
              3,
              {
                d = [
                  4,
                  5,
                ],
                e = [],
              },
            ]
            """),
        parsed("a maximum line width of Integer.MAX_VALUE", longArray,
            TomlWriteOptions.defaults().withMaxLineWidth(Integer.MAX_VALUE), longArray),
        unchanged("an inline table that fits for TOML 1.0.0", """
            a = [1, { b = 2 }]
            """, toml10Options),
        unchanged("an inline table that fits for TOML 1.1.0", """
            a = [1, { b = 2 }]
            """, toml11Options),
        parsed("a long inline table for TOML 1.0.0", """
            a = [1, { b = "%s", c = "%s" }]
            """.formatted("x".repeat(40), "y".repeat(40)), toml10Options, """
            a = [
              1,
              { b = "%s", c = "%s" },
            ]
            """.formatted("x".repeat(40), "y".repeat(40))),
        parsed("a long inline table for TOML 1.1.0", """
            a = [1, { b = "%s", c = "%s" }]
            """.formatted("x".repeat(40), "y".repeat(40)), toml11Options, """
            a = [
              1,
              {
                b = "%s",
                c = "%s",
              },
            ]
            """.formatted("x".repeat(40), "y".repeat(40))),
        parsed("inline tables in a multi-line array for TOML 1.1.0", """
            a = ["%s", { b = 1 }, { c = "%s" }]
            """.formatted("x".repeat(70), "y".repeat(80)), toml11Options, """
            a = [
              "%s",
              { b = 1 },
              {
                c = "%s",
              },
            ]
            """.formatted("x".repeat(70), "y".repeat(80))),
        parsed("a long inline table inside an inline table for TOML 1.1.0", """
            a = [1, { b = { c = "%s" } }]
            """.formatted("x".repeat(80)), toml11Options, """
            a = [
              1,
              {
                b = {
                  c = "%s",
                },
              },
            ]
            """.formatted("x".repeat(80))),
        parsed("a long array inside an inline table for TOML 1.1.0", """
            a = [1, { b = ["%s", "y"] }]
            """.formatted("x".repeat(80)), toml11Options, """
            a = [
              1,
              {
                b = [
                  "%s",
                  "y",
                ],
              },
            ]
            """.formatted("x".repeat(80))),
        parsed("an array that fits once its inline table is split for TOML 1.0.0", """
            a = [1, { b = ["%s", "y"] }]
            """.formatted("x".repeat(62)), toml10Options, """
            a = [
              1,
              { b = ["%s", "y"] },
            ]
            """.formatted("x".repeat(62))),
        parsed("an array that fits once its inline table is split for TOML 1.1.0", """
            a = [1, { b = ["%s", "y"] }]
            """.formatted("x".repeat(62)), toml11Options, """
            a = [
              1,
              {
                b = ["%s", "y"],
              },
            ]
            """.formatted("x".repeat(62))),
        parsed("an empty inline table in a multi-line array for TOML 1.0.0", """
            a = ["%s", {}]
            """.formatted("x".repeat(80)), toml10Options, """
            a = [
              "%s",
              {},
            ]
            """.formatted("x".repeat(80))),
        parsed("an empty inline table in a multi-line array for TOML 1.1.0", """
            a = ["%s", {}]
            """.formatted("x".repeat(80)), toml11Options, """
            a = [
              "%s",
              {},
            ]
            """.formatted("x".repeat(80))),
        parsed("entries aligned with their headers", document, aligned, """
            title = "Example"

                [a.b.c]
                d = 1

            [server]
            host = "localhost"

              [server.tls]
              enabled = true

            [[products]]
            sku = 1

              [products.size]
              width = 2

            [[products]]
            sku = 2
            """),
        parsed("comments indented like aligned entries", """
            [a.b]
            # unattached

            # above
            c = 1  # after
            """, aligned, """
              [a.b]

              # unattached

              # above
              c = 1  # after
            """),
        parsed("an array at the width limit under an entry aligned with its header", """
            [t]
            list = ["%s"]
            """.formatted("x".repeat(69)), aligned, """
            [t]
            list = ["%s"]
            """.formatted("x".repeat(69)))
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
  void shouldWriteValuesReadFromADocumentInTheFormTheyWereRead() {
    TomlParseResult result = Toml.parse("mask = 0xFF\nt = { a = 0x1 }\naot = [ { a = 1 } ]\n");
    assertFalse(result.hasErrors(), () -> result.errors().toString());
    MutableTomlTable table = MutableTomlTable.create();
    table.set("mask", result.entry("mask").value());
    table.set("t", result.get("t"));
    table.set("aot", result.get("aot"));

    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\n");
    assertSerializes(table, options, "mask = 0xFF\nt = { a = 0x1 }\naot = [{ a = 1 }]\n");
    assertSerializes(
        table,
        options.keep(TomlWriteOptions.Keep.NOTHING),
        "mask = 255\n\n[t]\na = 1\n\n[[aot]]\na = 1\n");
  }

  @Test
  void shouldWriteATableMadeInlineOnItsEntrysLineUnlessNothingIsKept() {
    MutableTomlTable doc = MutableTomlTable.create();
    doc.set("point", MutableTomlTable.createInline().set("x", 1).set("y", 2));
    doc.set("empty", MutableTomlTable.createInline());
    MutableTomlTable nested = MutableTomlTable.createInline();
    nested.set("a", MutableTomlTable.createInline().set("b", 1));
    nested.set("c", MutableTomlTable.create().set("d", 2));
    doc.getOrCreateTable("section").set("nested", nested);

    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\n");
    assertSerializes(
        doc,
        options,
        "point = { x = 1, y = 2 }\nempty = {}\n\n[section]\nnested = { a = { b = 1 }, c.d = 2 }\n");
    assertSerializes(
        doc,
        options.keep(TomlWriteOptions.Keep.NOTHING),
        "[point]\nx = 1\ny = 2\n\n[empty]\n\n[section.nested.a]\nb = 1\n\n[section.nested.c]\nd = 2\n");
  }

  @Test
  void shouldWriteAnArrayMadeInlineBetweenBracketsWhateverItHolds() {
    MutableTomlTable doc = MutableTomlTable.create();
    MutableTomlArray points = MutableTomlArray.createInline();
    points.add(MutableTomlTable.create().set("x", 1));
    points.add(MutableTomlTable.create().set("x", 2));
    doc.set("points", points);
    MutableTomlArray sections = MutableTomlArray.create();
    sections.add(MutableTomlTable.create().set("x", 3));
    doc.set("sections", sections);

    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\n");
    assertSerializes(doc, options, "points = [{ x = 1 }, { x = 2 }]\n\n[[sections]]\nx = 3\n");
    assertSerializes(
        doc,
        options.keep(TomlWriteOptions.Keep.NOTHING),
        "[[points]]\nx = 1\n\n[[points]]\nx = 2\n\n[[sections]]\nx = 3\n");
  }

  @Test
  void shouldWriteATableMadeInlineHoldingACommentOverLinesForToml110() {
    MutableTomlTable doc = MutableTomlTable.create();
    MutableTomlTable point = MutableTomlTable.createInline();
    point.set("x", 1);
    point.setCommentAbove("x", "the x");
    doc.set("point", point);

    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\n");
    assertSerializes(doc, options.withVersion(TomlVersion.V1_1_0), "point = {\n  # the x\n  x = 1,\n}\n");
    assertThrows(IllegalArgumentException.class, () -> doc.toToml(options.withVersion(TomlVersion.V1_0_0)));
  }

  @Test
  void shouldWriteAnInlineTableHoldingCommentsAsASection() {
    TomlTable table = parse("a = {\n  # unattached\n\n  b = 1,  # after\n}\n");
    assertSerializes(
        table,
        TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0),
        "[a]\n\n# unattached\n\nb = 1  # after\n".replace("\n", System.lineSeparator()));
  }

  @Test
  void shouldWriteAnInlineTableHoldingCommentsOverLines() {
    TomlTable table = parse("a = [1, {\n  # above\n  b = 2 }]\n");
    assertSerializes(table, TomlWriteOptions.defaults().withVersion(TomlVersion.LATEST), """
        a = [
          1,
          {
            # above
            b = 2,
          },
        ]
        """.replace("\n", System.lineSeparator()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inlineTableHoldingCommentsSupplier")
  void shouldRejectAnInlineTableHoldingCommentsForToml100(String description, TomlTable table) {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> write(table, TomlVersion.V1_0_0));
    assertEquals(
        "An inline table holding a comment cannot be written for TOML 1.0.0, which allows no line break inside an inline table",
        e.getMessage());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inlineTableHoldingCommentsSupplier")
  void shouldWriteAnInlineTableHoldingCommentsForToml110(String description, TomlTable table) {
    String toml = write(table, TomlVersion.LATEST);
    TomlParseResult reparsed = Toml.parse(toml, TomlVersion.LATEST);
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    assertTrue(Toml.equals(table, reparsed), () -> toml);
    TomlAssertions.assertSameComments(table, reparsed);
  }

  static Stream<Arguments> inlineTableHoldingCommentsSupplier() {
    // @formatter:off
    return Stream.of(
        Arguments.of("an inline table with a comment above an entry",
            parse("a = [1, {\n  # above\n  b = 2 }]\n")),
        Arguments.of("an inline table in an inline table holding a comment",
            parse("a = [1, { b = {\n  # above\n  c = 1 } }]\n")),
        Arguments.of("an inline table holding an array holding a comment",
            parse("a = [1, { b = [\n  # above\n  1] }]\n"))
    );
    // @formatter:on
  }

  @Test
  void shouldWriteAnArrayHoldingCommentsOverLinesForToml100() {
    TomlTable table = parse("a = [1, [\n  # above\n  2]]\n");
    assertSerializes(table, TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0), """
        a = [
          1,
          [
            # above
            2,
          ],
        ]
        """.replace("\n", System.lineSeparator()));
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

    for (TomlWriteOptions options : List
        .of(TomlWriteOptions.defaults(), TomlWriteOptions.defaults().withLineSeparator("\r\n"))) {
      String toml = table.toToml(options);
      for (TomlVersion version : List.of(TomlVersion.V1_0_0, TomlVersion.LATEST)) {
        TomlParseResult result = Toml.parse(toml, version);
        assertFalse(result.hasErrors(), () -> toml + "\n" + result.errors());
        assertEquals(value, result.getString("s"), () -> toml);
      }
    }
  }

  static Stream<String> multilineStringRoundTripSupplier() {
    // @formatter:off
    return Stream.of(
        "a\nb", "a\n", "\n", "\n\n", "\nx", "a\r\nb", "\r\n", "\"", "\"\"", "\"\"\"", "\"\"\"\"\"\"\"",
        "x\n\"", "x\n\"\"", "x\n\"\"\"", "\\\n", "a\\\nb", "\t\n\u0000\u007F", "trailing space \nx", "😀\n日本");
    // @formatter:on
  }

  @Test
  void shouldWriteCrlfLineSeparatorsInTables() {
    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\r\n");
    String x = "x".repeat(80);
    TomlTable table = parse("a = 1\n[b]\nc = [\"" + x + "\", 2]\n[b.d]\ne = true\n");

    String toml = table.toToml(options);
    assertSerializes(
        table,
        options,
        "a = 1\r\n\r\n[b]\r\nc = [\r\n  \"" + x + "\",\r\n  2,\r\n]\r\n\r\n[b.d]\r\ne = true\r\n");
    assertFalse(toml.replace("\r\n", "").contains("\n"), toml);
  }

  @Test
  void shouldWriteCrlfLineSeparatorsInArrays() {
    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\r\n");
    String x = "x".repeat(80);
    TomlArray array = MutableTomlArray.of(x, List.of(2));

    String toml = array.toToml(options);
    assertSerializes(array, options, "[\r\n  \"" + x + "\",\r\n  [2],\r\n]");
    assertFalse(toml.replace("\r\n", "").contains("\n"), toml);
  }

  @Test
  void shouldWriteCrlfLineSeparatorsInMultilineStrings() {
    TomlWriteOptions options = TomlWriteOptions.defaults().withLineSeparator("\r\n");
    MutableTomlTable table = MutableTomlTable.create();
    table.set("s", "a\nb");

    String toml = table.toToml(options);
    assertSerializes(table, options, "s = \"\"\"\r\na\r\nb\"\"\"\r\n");
    assertFalse(toml.replace("\r\n", "").contains("\n"), toml);
  }

  @Test
  void shouldApplyTheWidthButNotTheIndentToArrays() {
    TomlWriteOptions options = TomlWriteOptions.defaults().withIndent(4).withMaxLineWidth(9);
    TomlArray array = MutableTomlArray.of(1, List.of(2, 3));
    assertSerializes(array, options, "[\n  1,\n  [2, 3],\n]".replace("\n", System.lineSeparator()));
  }

  @Test
  void shouldAppendTheSameTextAsToTomlWithOptions() throws IOException {
    TomlWriteOptions options = TomlWriteOptions.defaults().withIndent(2).withMaxLineWidth(10).withLineSeparator("\r\n");

    TomlTable table = parse("a = [1, 2, 3]\n[b]\nc = [4, 5]\n");
    StringWriter tableOut = new StringWriter();
    table.toToml(tableOut, options);
    assertEquals(table.toToml(options), tableOut.toString());
    assertNotEquals(table.toToml(), tableOut.toString());

    TomlArray array = MutableTomlArray.of(1, 2, List.of(3, 4));
    StringWriter arrayOut = new StringWriter();
    array.toToml(arrayOut, options);
    assertEquals(array.toToml(options), arrayOut.toString());
    assertNotEquals(array.toToml(), arrayOut.toString());
  }

  @Test
  void shouldRejectNullOptions() {
    TomlTable table = MutableTomlTable.create();
    TomlArray array = MutableTomlArray.create();
    assertThrows(NullPointerException.class, () -> table.toToml((TomlWriteOptions) null));
    assertThrows(NullPointerException.class, () -> table.toToml(new StringBuilder(), null));
    assertThrows(NullPointerException.class, () -> array.toToml((TomlWriteOptions) null));
    assertThrows(NullPointerException.class, () -> array.toToml(new StringBuilder(), null));
  }

  @Test
  void joinsTheRunsAfterTheLastLineOfATableWithAnEmptyCommentLine() {
    TomlParseResult table = parse("[a]\n# one\n\nx = 1\n# two\n\n[b]\ny = 2\n");
    table.remove("a.x");
    String toml = table.toToml();
    assertEquals("[a]\n# one\n#\n# two\n\n[b]\ny = 2\n", toml);

    // The two runs come back as one run of the table they were written in, and nothing of them in the root
    TomlParseResult reparsed = Toml.parse(toml);
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    assertTrue(unattachedComments(reparsed).isEmpty());
    TomlTable a = reparsed.getTable("a");
    assertNotNull(a);
    List<TomlComment> comments = unattachedComments(a);
    assertEquals(1, comments.size());
    assertEquals(List.of("one", "", "two"), comments.get(0).lines());
  }

  private static List<TomlComment> unattachedComments(TomlTable table) {
    return table.elements().stream().filter(TomlComment.class::isInstance).map(TomlComment.class::cast).toList();
  }

  @Test
  void keepsTheRunsAfterTheLastLineOfTheRootApart() {
    TomlParseResult table = parse("x = 1\n# one\n\ny = 2\n# two\n");
    table.remove("y");
    assertSerializes(table, "x = 1\n# one\n\n# two\n");
  }

  private static Arguments unchanged(String description, String toml) {
    return Arguments.of(description, parse(toml), toml);
  }

  private static Arguments parsed(String description, String toml, String expected) {
    return Arguments.of(description, parse(toml), expected);
  }

  private static Arguments unchanged(String description, String toml, TomlWriteOptions options) {
    return Arguments.of(description, parse(toml), options, toml);
  }

  private static Arguments parsed(String description, String toml, TomlWriteOptions options, String expected) {
    return Arguments.of(description, parse(toml), options, expected);
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

  // Parsed with no source kept, since the default style is what a document with no text to read is written in; a
  // document parsed with its source is written from that text instead, which SourcePreservingSerializerTest covers.
  private static TomlParseResult parse(String toml) {
    TomlParseResult result =
        Toml.parse(toml, TomlParseOptions.defaults().withVersion(TomlVersion.LATEST).withoutSource());
    assertFalse(result.hasErrors(), () -> toml + "\n" + result.errors());
    return result;
  }

  private static void assertSerializes(TomlTable table, String expected) {
    assertSerializes(table, TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0), expected);
  }

  private static String write(TomlTable table, TomlVersion version) {
    return table.toToml(TomlWriteOptions.defaults().withVersion(version));
  }

  private static void assertSerializes(TomlTable table, TomlWriteOptions options, String expected) {
    String toml = table.toToml(options);
    assertEquals(expected, toml);

    TomlParseResult reparsed = Toml.parse(toml, options.version());
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    assertTrue(Toml.equals(table, reparsed), () -> toml);
    TomlAssertions.assertSameComments(table, reparsed);
  }

  private static void assertSerializes(TomlArray array, String expected) {
    assertSerializes(array, TomlWriteOptions.defaults(), expected);
  }

  private static void assertSerializes(TomlArray array, TomlWriteOptions options, String expected) {
    String toml = array.toToml(options);
    assertEquals(expected, toml);

    TomlParseResult reparsed = Toml.parse("a = " + toml, options.version());
    assertFalse(reparsed.hasErrors(), () -> toml + "\n" + reparsed.errors());
    TomlArray reparsedArray = reparsed.getArray("a");
    assertNotNull(reparsedArray);
    assertTrue(Toml.equals(array, reparsedArray), () -> toml);
    TomlAssertions.assertSameComments(array, reparsedArray);
  }
}
