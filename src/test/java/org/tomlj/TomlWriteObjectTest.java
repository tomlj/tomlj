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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TomlWriteObjectTest {

  enum When {
    ALWAYS, ON_SUCCESS
  }

  record Server(String host, int port) {}

  record Config(String name, List<String> tags, Server server, List<Server> servers) {}

  private static final Config CONFIG = new Config(
      "demo",
      List.of("a", "b"),
      new Server("localhost", 8080),
      List.of(new Server("alpha", 8001), new Server("beta", 8002)));

  private static TomlParseResult parse(String toml) {
    TomlParseResult result = Toml.parse(toml);
    assertFalse(result.hasErrors(), () -> result.errors().toString());
    return result;
  }

  @Test
  void writesRecordsAsTables() {
    assertEquals("""
        name = "demo"
        tags = ["a", "b"]

        [server]
        host = "localhost"
        port = 8080

        [[servers]]
        host = "alpha"
        port = 8001

        [[servers]]
        host = "beta"
        port = 8002
        """, MutableTomlTable.from(CONFIG).toToml());
  }

  @Test
  void writesWhatBindsBackToAnEqualObject() {
    assertEquals(CONFIG, MutableTomlTable.from(CONFIG).as(Config.class));
    assertEquals(CONFIG, Toml.parse(MutableTomlTable.from(CONFIG).toToml()).as(Config.class));
  }

  record Scalars(
      char c,
      byte b,
      short s,
      float f,
      BigInteger big,
      BigDecimal decimal,
      Instant instant,
      ZonedDateTime zoned,
      When when,
      @SuppressWarnings("ArrayRecordComponent") int[] numbers) {}

  @Test
  void writesScalars() {
    Scalars scalars = new Scalars(
        'x',
        (byte) 1,
        (short) 2,
        0.1f,
        BigInteger.valueOf(Long.MAX_VALUE),
        new BigDecimal("2.5"),
        Instant.parse("2026-09-25T10:15:30Z"),
        ZonedDateTime.of(2026, 9, 25, 10, 15, 30, 0, ZoneId.of("Europe/Paris")),
        When.ON_SUCCESS,
        new int[] {1, 2});
    assertEquals("""
        c = "x"
        b = 1
        s = 2
        f = 0.1
        big = 9223372036854775807
        decimal = 2.5
        instant = 2026-09-25T10:15:30Z
        zoned = 2026-09-25T10:15:30+02:00
        when = "ON_SUCCESS"
        numbers = [1, 2]
        """, MutableTomlTable.from(scalars).toToml());
    Scalars back = MutableTomlTable.from(scalars).as(Scalars.class);
    assertEquals(0.1f, back.f());
    assertEquals(scalars.instant(), back.instant());
    assertEquals(When.ON_SUCCESS, back.when());
  }

  record WithMissing(String name, @Nullable String description, Optional<Integer> backlog) {}

  @Test
  void leavesOutNullAndEmptyMembers() {
    assertEquals("name = \"n\"\n", MutableTomlTable.from(new WithMissing("n", null, Optional.empty())).toToml());
    assertEquals(
        "name = \"n\"\nbacklog = 5\n",
        MutableTomlTable.from(new WithMissing("n", null, Optional.of(5))).toToml());
  }

  static class Base {
    String name = "base";
  }

  static final class Settings extends Base {
    static String ignoredStatic = "s";
    transient String ignoredTransient = "t";
    final String ignoredFinal = "f";
    int maxConnections = 10;
    @TomlName("trust-x-forwarded-for")
    boolean trustProxy = true;
  }

  @Test
  void writesClassesWithTheKeysTheyAreBoundTo() {
    TomlBindOptions options = TomlBindOptions.defaults().withKeyNaming(TomlBindOptions.KeyNaming.KEBAB_CASE);
    assertEquals("""
        name = "base"
        max-connections = 10
        trust-x-forwarded-for = true
        """, MutableTomlTable.from(new Settings(), options).toToml());
  }

  @SuppressWarnings("ClassCanBeStatic")
  final class Inner {
    String name;

    Inner(String name) {
      this.name = name;
    }
  }

  @Test
  void writesClassesThatCannotBeBound() {
    assertEquals("name = \"n\"\n", MutableTomlTable.from(new Inner("n")).toToml());
  }

  @Test
  void writesMapsAndArrays() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("n", 1);
    map.put("skipped", null);
    map.put("server", new Server("h", 2));
    assertEquals("""
        n = 1

        [server]
        host = "h"
        port = 2
        """, MutableTomlTable.from(map).toToml());
    assertEquals(
        "[{ host = \"a\", port = 1 }, { host = \"b\", port = 2 }]",
        MutableTomlArray.from(new Server[] {new Server("a", 1), new Server("b", 2)}).toToml());
  }

  @Test
  void writesTablesAndArraysOfAnyClass() {
    TomlParseResult parsed = Toml.parse("[t]\nk = 0x10\nl = [1, 2]\n");
    assertFalse(parsed.hasErrors());
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("t", parsed.getTable("t"));
    map.put("l", parsed.getArray("t.l"));
    assertEquals("l = [1, 2]\n\n[t]\nk = 0x10\nl = [1, 2]\n", MutableTomlTable.from(map).toToml());
    assertEquals("[t]\nk = 0x10\nl = [1, 2]\n", MutableTomlTable.from(parsed).toToml());
    assertEquals("[1, 2]", MutableTomlArray.from(parsed.getArray("t.l")).toToml());
  }

  record Durations(Duration timeout, List<Object> any) {}

  @Test
  void writesWithConverters() {
    TomlBindOptions options = TomlBindOptions
        .defaults()
        .withConverter(Duration.class, value -> Duration.parse((String) value), Duration::toString)
        .withConverter(Path.class, value -> Path.of((String) value), Path::toString)
        .withConverter(Integer.class, value -> ((Long) value).intValue(), i -> TomlValue.hex(i));
    assertEquals(
        "timeout = \"PT30S\"\nany = [\"/tmp\", 0xA]\n",
        MutableTomlTable.from(new Durations(Duration.ofSeconds(30), List.of(Path.of("/tmp"), 10)), options).toToml());
  }

  record Box<T>(T value)
  {
  }

  record Boxes(Box<Server> server, Box<List<When>> whens) {}

  @Test
  void writesGenericRecords() {
    assertEquals("""
        [server.value]
        host = "h"
        port = 1

        [whens]
        value = ["ALWAYS"]
        """, MutableTomlTable.from(new Boxes(new Box<>(new Server("h", 1)), new Box<>(List.of(When.ALWAYS)))).toToml());
  }

  record Growing<T>(T value, List<Growing<List<T>>>children)
  {
  }

  @Test
  void rejectsGenericTypesThatGrowWithoutEnd() {
    assertEquals(
        "Cannot write org.tomlj.TomlWriteObjectTest$Growing: its members use it with type arguments that grow without end",
        assertThrows(IllegalArgumentException.class, () -> MutableTomlTable.from(new Growing<>(1, List.of())))
            .getMessage());
  }

  record Numbers(BigInteger big, BigDecimal decimal, List<String> tags) {}

  record Holder(Numbers numbers) {}

  @Test
  void rejectsValuesTomlCannotHold() {
    assertEquals(
        "Cannot write numbers.big: 9223372036854775808 is out of range for a TOML integer",
        assertThrows(
            IllegalArgumentException.class,
            () -> MutableTomlTable
                .from(new Holder(new Numbers(BigInteger.ONE.shiftLeft(63), BigDecimal.ONE, List.of()))))
            .getMessage());
    assertEquals(
        "Cannot write numbers.decimal: 0.1000000000000000000001 cannot be represented exactly as a TOML float",
        assertThrows(
            IllegalArgumentException.class,
            () -> MutableTomlTable
                .from(new Holder(new Numbers(BigInteger.ONE, new BigDecimal("0.1000000000000000000001"), List.of()))))
            .getMessage());
    assertEquals(
        "Cannot write numbers.tags[1]: an array cannot hold a missing value",
        assertThrows(
            IllegalArgumentException.class,
            () -> MutableTomlTable
                .from(new Holder(new Numbers(BigInteger.ONE, BigDecimal.ONE, Arrays.asList("a", null)))))
            .getMessage());
    assertEquals(
        "Cannot write tags[0]: String contains an unpaired surrogate",
        assertThrows(
            IllegalArgumentException.class,
            () -> MutableTomlTable.from(new Numbers(BigInteger.ONE, BigDecimal.ONE, List.of("\uD800")))).getMessage());
  }

  interface Shape {
  }

  record Drawing(Shape shape) {}

  record WithDuration(Duration duration) {}

  static final class Node {
    String name = "n";
    List<Object> children = new ArrayList<>();
  }

  @Test
  void rejectsWhatItCannotWrite() {
    assertEquals(
        "Cannot write org.tomlj.TomlWriteObjectTest$Drawing.shape: Cannot write org.tomlj.TomlWriteObjectTest$Shape: "
            + "it is an interface or abstract class; register a converter for it in TomlBindOptions",
        assertThrows(IllegalArgumentException.class, () -> MutableTomlTable.from(new Drawing(null))).getMessage());
    assertEquals(
        "Cannot write org.tomlj.TomlWriteObjectTest$WithDuration.duration: Cannot write java.time.Duration: its "
            + "converter in TomlBindOptions only reads it; register one that also writes it",
        assertThrows(
            IllegalArgumentException.class,
            () -> MutableTomlTable
                .from(
                    new WithDuration(Duration.ZERO),
                    TomlBindOptions.defaults().withConverter(Duration.class, value -> Duration.ZERO)))
            .getMessage());
    assertEquals(
        "Cannot write a java.lang.String as a table; only a record, class or map is written as one",
        assertThrows(IllegalArgumentException.class, () -> MutableTomlTable.from("text")).getMessage());
    assertEquals(
        "Cannot write a org.tomlj.TomlWriteObjectTest$Server as an array; only a collection or Java array is written "
            + "as one",
        assertThrows(IllegalArgumentException.class, () -> MutableTomlArray.from(new Server("h", 1))).getMessage());

    Node node = new Node();
    node.children.add(node);
    assertEquals(
        "Cannot write children[0]: the value holds itself, and TOML cannot hold a value within itself",
        assertThrows(IllegalArgumentException.class, () -> MutableTomlTable.from(node)).getMessage());
  }

  @Test
  void updatesNothingWhenTheObjectIsUnchanged() {
    String toml = """
        # The name
        name = "demo"   # after
        tags = [
          'a', # first
          "b",
        ]

        [server]
        host = "localhost"
        port = 0x1F90

        [[servers]]
        host = "alpha"
        port = 8001

        [[servers]]
        host = "beta"
        port = 8002
        """;
    TomlParseResult document = parse(toml);
    document.update(document.as(Config.class));
    assertFalse(document.isModified());
    assertEquals(toml, document.toToml());
  }

  @Test
  void updatesChangedValuesKeepingComments() {
    TomlParseResult document = parse("""
        # The name
        name = "demo"   # after
        tags = [
          "a", # first
          "b",
        ]

        [server] # the server
        host = "localhost"
        port = 8080

        # servers
        [[servers]]
        host = "alpha" # a
        port = 8001

        [[servers]]
        host = "beta"
        port = 8002
        """);
    document
        .update(
            new Config(
                "demo2",
                List.of("a", "c", "d"),
                new Server("localhost", 9090),
                List.of(new Server("alpha", 8001), new Server("beta", 8003), new Server("gamma", 8004))));
    assertEquals("""
        # The name
        name = "demo2"   # after
        tags = [
          "a", # first
          "c",
          "d",
        ]

        [server] # the server
        host = "localhost"
        port = 9090

        # servers
        [[servers]]
        host = "alpha" # a
        port = 8001

        [[servers]]
        host = "beta"
        port = 8003

        [[servers]]
        host = "gamma"
        port = 8004
        """, document.toToml());
    assertFalse(document.isModified("server.host"));
    assertTrue(document.isModified("server.port"));
  }

  @Test
  void updatesArraysAtTheirEnd() {
    TomlParseResult document = parse("""
        name = "demo"
        tags = ["a", "b", "c"]

        [server]
        host = "localhost"
        port = 8080

        [[servers]]
        host = "alpha"
        port = 8001 # alpha

        [[servers]]
        host = "beta"
        port = 8002
        """);
    document
        .update(new Config("demo", List.of("a"), new Server("localhost", 8080), List.of(new Server("alpha", 8001))));
    assertEquals("""
        name = "demo"
        tags = ["a"]

        [server]
        host = "localhost"
        port = 8080

        [[servers]]
        host = "alpha"
        port = 8001 # alpha
        """, document.toToml());
  }

  record Options(When when, float ratio, @Nullable String description, Optional<Integer> backlog, Duration timeout) {}

  @Test
  void keepsHowUnchangedValuesAreWritten() {
    TomlBindOptions options = TomlBindOptions
        .defaults()
        .withConverter(Duration.class, value -> parseDuration((String) value), Duration::toString);
    String toml = """
        when = "on-success"
        ratio = 1
        description = 'text'
        backlog = 0o17
        timeout = "30s"
        """;
    TomlParseResult document = parse(toml);
    Options read = document.as(Options.class, options);
    document.update(read, options);
    assertEquals(toml, document.toToml());

    document.update(new Options(When.ON_SUCCESS, 1.5f, null, Optional.empty(), Duration.ofMinutes(1)), options);
    assertEquals("""
        when = "on-success"
        ratio = 1.5
        timeout = "PT1M"
        """, document.toToml());
  }

  static final class Label {
    final String text;

    Label(String text) {
      this.text = text;
    }
  }

  record Written(BigDecimal price, ZonedDateTime at, Label label, List<BigDecimal> prices) {}

  @Test
  void keepsValuesThatWriteTheSameThoughNotEqual() {
    TomlBindOptions options =
        TomlBindOptions.defaults().withConverter(Label.class, value -> new Label((String) value), label -> label.text);
    String toml = """
        price = 1.50
        at = 2026-09-26 10:00:00+02:00
        label = "\\u0078"
        prices = [1.50, 2.0]
        """;
    TomlParseResult document = parse(toml);
    ZonedDateTime inParis = ZonedDateTime.of(2026, 9, 26, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    List<BigDecimal> prices = List.of(new BigDecimal("0.5"), new BigDecimal("1.500"), new BigDecimal("2.00"));
    document.update(new Written(new BigDecimal("1.500"), inParis, new Label("x"), prices), options);
    assertEquals("""
        price = 1.50
        at = 2026-09-26 10:00:00+02:00
        label = "\\u0078"
        prices = [0.5, 1.50, 2.0]
        """, document.toToml());

    document.update(new Written(new BigDecimal("2.5"), inParis.plusHours(1), new Label("y"), prices), options);
    assertEquals("""
        price = 2.5
        at = 2026-09-26T11:00:00+02:00
        label = "y"
        prices = [0.5, 1.50, 2.0]
        """, document.toToml());
  }

  private static Duration parseDuration(String text) {
    return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
  }

  @Test
  void updatesKeepKeysThatNameNoMember() {
    TomlParseResult document = parse("host = \"a\"\nport = 1\nextra = true\n");
    document.update(new Server("b", 1));
    assertEquals("host = \"b\"\nport = 1\nextra = true\n", document.toToml());
  }

  @Test
  void updatesMapsToHoldOnlyTheirEntries() {
    TomlParseResult document = parse("[limits]\na = 1 # a\nb = 2\n");
    Map<String, Integer> limits = new LinkedHashMap<>();
    limits.put("a", 1);
    limits.put("c", 3);
    document.getTable("limits").update(limits);
    assertEquals("[limits]\na = 1 # a\nc = 3\n", document.toToml());
  }

  record Groups(List<TomlTable> group) {}

  @Test
  void updatesArraysKeepingTheTablesTheyHold() {
    String source = "[[group]]\na = 1\n[[group]]\na = 2 # two\n";
    TomlParseResult document = parse(source);
    Groups groups = Toml.parse(source, TomlParseOptions.defaults().withoutSource()).as(Groups.class);
    document.update(new Groups(List.of(groups.group().get(1))));
    assertEquals("[[group]]\na = 2 # two\n", document.toToml());
  }

  record Plugins(String name, TomlArray levels, TomlTable settings) {}

  @Test
  void updatesTablesAndArraysInPlace() {
    String source = """
        name = "a"
        levels = [1, 0x2] # levels

        [settings]
        k   =   0x10 # note
        [settings.sub]
        x = 'y' # x
        """;
    TomlParseResult document = parse(source);
    Plugins plugins = Toml.parse(source, TomlParseOptions.defaults().withoutSource()).as(Plugins.class);
    document.update(plugins);
    assertEquals(source, document.toToml());

    MutableTomlArray levels = MutableTomlArray.copyOf(plugins.levels());
    levels.add(3);
    MutableTomlTable settings = MutableTomlTable.copyOf(plugins.settings());
    settings.set("added", true);
    settings.set("sub.x", "z");
    document.update(new Plugins("a", levels, settings));
    assertEquals("""
        name = "a"
        levels = [1, 0x2, 3] # levels

        [settings]
        k   =   0x10 # note
        added = true
        [settings.sub]
        x = "z" # x
        """, document.toToml());
  }

  record Addresses(Object address) {}

  @Test
  void replacesValuesOfAnotherType() {
    TomlParseResult document = parse("# where\naddress = \"a\" # after\n");
    document.update(new Addresses(new Server("h", 1)));
    TomlParseResult written = parse(document.toToml());
    assertEquals(new Server("h", 1), written.getTable("address").as(Server.class));
    assertEquals(List.of("where"), written.comment("address", TomlComment.Placement.ABOVE).lines());
    assertEquals("after", written.comment("address", TomlComment.Placement.AFTER).text());
  }

  @Test
  void updatesArrays() {
    TomlParseResult document = parse("ports = [\n  1, # one\n  2,\n]\n");
    MutableTomlArray ports = document.getArray("ports");
    assertSame(ports, ports.update(new int[] {1, 3}));
    assertEquals("ports = [\n  1, # one\n  3,\n]\n", document.toToml());
  }

  @Test
  void updatesArraysKeepingTheElementsTheyHold() {
    TomlParseResult document = parse("""
        ports = [
          8080, # http
          8443, # https
        ]
        """);
    document.update(Map.of("ports", List.of(80, 8080, 8443)));
    assertEquals("""
        ports = [
          80,
          8080, # http
          8443, # https
        ]
        """, document.toToml());
    assertFalse(document.getArray("ports").isModified(1));

    document.update(Map.of("ports", List.of(80, 8443)));
    assertEquals("""
        ports = [
          80,
          8443, # https
        ]
        """, document.toToml());
  }

  record Servers(List<Server> servers) {}

  @Test
  void updatesArraysOfTablesKeepingTheTablesTheyHold() {
    TomlParseResult document = parse("""
        [[servers]] # a
        host = "alpha"
        port = 8001

        [[servers]] # b
        host = "beta"
        port = 8002

        [[servers]] # g
        host = "gamma"
        port = 8003
        """);
    document
        .update(
            new Servers(
                List
                    .of(
                        new Server("zeta", 8000),
                        new Server("alpha", 8001),
                        new Server("beta", 9002),
                        new Server("gamma", 8003))));
    assertEquals("""
        [[servers]]
        host = "zeta"
        port = 8000

        [[servers]] # a
        host = "alpha"
        port = 8001

        [[servers]] # b
        host = "beta"
        port = 9002

        [[servers]] # g
        host = "gamma"
        port = 8003
        """, document.toToml());
  }

  @Test
  void updatesArraysToHoldTheirList() {
    List<List<Integer>> lists = new ArrayList<>();
    lists.add(List.of());
    lists.add(List.of(1, 2, 3));
    lists.add(List.of(3, 2, 1));
    lists.add(List.of(1, 4, 2, 5, 3));
    lists.add(List.of(2, 2, 2));
    lists.add(List.of(9, 1, 9));
    List<Integer> large = new ArrayList<>();
    List<Integer> reversed = new ArrayList<>();
    for (int i = 0; i < 300; i++) {
      large.add(i);
      reversed.add(0, i);
    }
    lists.add(large);
    lists.add(reversed);
    for (List<Integer> from : lists) {
      for (List<Integer> to : lists) {
        TomlParseResult document = parse("values = " + MutableTomlArray.from(from).toToml() + "\n");
        document.update(Map.of("values", to));
        assertEquals(to, parse(document.toToml()).getArray("values").as(new GenericType<List<Integer>>() {}));
      }
    }
  }

  @Test
  void writesOffsetsItWasGiven() {
    OffsetDateTime time = OffsetDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneOffset.ofHours(-7));
    TomlParseResult document = parse("time = 2026-09-25T17:00:00Z\n");
    document.update(Map.of("time", time.toInstant()));
    assertEquals("time = 2026-09-25T17:00:00Z\n", document.toToml());
    document.update(Map.of("time", time));
    assertEquals("time = 2026-09-25T10:00:00-07:00\n", document.toToml());
  }
}
