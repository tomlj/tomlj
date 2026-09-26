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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tomlj.bindtest.MarkedTypes;
import org.tomlj.bindtest.Unloadable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TomlBindTest {

  record Server(String host, int port) {}

  record Config(String name, Server server, List<String> tags) {}

  private static List<String> errors(TomlBindException e) {
    return e.errors().stream().map(TomlBindError::toString).collect(Collectors.toList());
  }

  private static TomlBindException bindFails(String toml, Class<?> type) {
    return bindFails(toml, type, TomlBindOptions.defaults());
  }

  private static TomlBindException bindFails(String toml, Class<?> type, TomlBindOptions options) {
    TomlParseResult result = Toml.parse(toml);
    assertFalse(result.hasErrors(), () -> result.errors().toString());
    return assertThrows(TomlBindException.class, () -> result.as(type, options));
  }

  @Test
  void bindsRecords() {
    Config config = Toml.parse("""
        name = "demo"
        tags = ["a", "b"]

        [server]
        host = "localhost"
        port = 8080
        """).as(Config.class);
    assertEquals(new Config("demo", new Server("localhost", 8080), List.of("a", "b")), config);
  }

  static class Base {
    String name;
  }

  static class Settings extends Base {
    int port = 8080;
    String host = "localhost";
    transient String cache = "kept";
    static String shared = "static";
    List<Server> servers;
  }

  @Test
  void bindsClassesAndKeepsInitialValues() {
    Settings settings = Toml.parse("""
        name = "demo"
        host = "example.com"
        [[servers]]
        host = "a"
        port = 1
        """).as(Settings.class);
    assertEquals("demo", settings.name);
    assertEquals("example.com", settings.host);
    assertEquals(8080, settings.port);
    assertEquals("kept", settings.cache);
    assertEquals(List.of(new Server("a", 1)), settings.servers);
  }

  @Test
  void reportsTransientFieldKeysAsUnknown() {
    TomlBindException e = bindFails("cache = \"x\"", Settings.class);
    assertEquals(List.of("cache: unknown key (line 1, column 1)"), errors(e));
  }

  static class FinalBase {
    final String name = "base";
  }

  static class FinalSettings extends FinalBase {
    @TomlName("server-host")
    final String host = "localhost";
    int port = 8080;
  }

  @Test
  void reportsFinalFieldKeysAsFinal() {
    TomlBindException e = bindFails("name = \"a\"\nserver-host = \"b\"\nport = 1\nhost = \"c\"", FinalSettings.class);
    assertEquals(
        List
            .of(
                "name: the field for this key is final (line 1, column 1)",
                "server-host: the field for this key is final (line 2, column 1)",
                "host: unknown key (line 4, column 1)"),
        errors(e));
  }

  record Nullability(String plain, @NonNull String checkerNonNull, int primitive, Optional<String> optional) {}

  @Test
  void bindsMissingKeysByNullability() {
    TomlBindException e = bindFails("[t]\nother = 1", Nullability.class);
    assertEquals(
        List
            .of(
                "checkerNonNull: missing (line 1, column 1)",
                "primitive: missing (line 1, column 1)",
                "t: unknown key (line 1, column 1)"),
        errors(e));

    Nullability n = Toml.parse("checkerNonNull = \"a\"\nprimitive = 1").as(Nullability.class);
    assertNull(n.plain());
    assertEquals(Optional.empty(), n.optional());
  }

  @Test
  void reportsMissingKeysAtTheirTable() {
    TomlBindException e = bindFails("name = \"n\"\ntags = []\n[server]\nhost = \"a\"", Config.class);
    assertEquals(List.of("server.port: missing (line 3, column 1)"), errors(e));
  }

  @Test
  void reportsMissingKeysAtTheTableBound() {
    TomlTable server = Toml.parse("name = \"n\"\n[server]\nhost = \"a\"").getTable("server");
    TomlBindException e = assertThrows(TomlBindException.class, () -> server.as(Server.class));
    assertEquals(List.of("port: missing (line 2, column 1)"), errors(e));
  }

  @NullMarked
  record NullMarkedRecord(String required, @Nullable String nullable, Optional<String> optional) {}

  @Test
  void honorsNullMarkedClasses() {
    assertEquals(List.of("required: missing (line 1, column 1)"), errors(bindFails("", NullMarkedRecord.class)));
    NullMarkedRecord r = Toml.parse("required = \"x\"").as(NullMarkedRecord.class);
    assertNull(r.nullable());
    assertEquals(Optional.empty(), r.optional());
  }

  @Test
  void honorsNullMarkedPackagesAndNullUnmarked() {
    assertEquals(List.of("name: missing (line 1, column 1)"), errors(bindFails("", MarkedTypes.Marked.class)));
    assertNull(Toml.parse("name = \"x\"").as(MarkedTypes.Marked.class).description());
    MarkedTypes.Unmarked unmarked = Toml.parse("").as(MarkedTypes.Unmarked.class);
    assertNull(unmarked.name());
  }

  @NullMarked
  static class MarkedSettings {
    String withDefault = "default";
    @Nullable
    String nullable;
    String required;
    Optional<String> optional;
  }

  @Test
  void reportsMissingNonNullFieldsOnlyWithoutInitialValue() {
    assertEquals(List.of("required: missing (line 1, column 1)"), errors(bindFails("", MarkedSettings.class)));
    MarkedSettings settings = Toml.parse("required = \"r\"").as(MarkedSettings.class);
    assertEquals("default", settings.withDefault);
    assertNull(settings.nullable);
    assertEquals(Optional.empty(), settings.optional);
  }

  @Test
  void collectsEveryErrorInDocumentOrder() {
    TomlBindException e = bindFails("""
        name = 1
        tags = ["a", 2, "c", false]

        [server]
        host = "localhost"
        port = 3000000000
        hots = "typo"
        """, Config.class);
    assertEquals(
        List
            .of(
                "name: expected a string, found an integer (line 1, column 8)",
                "tags[1]: expected a string, found an integer (line 2, column 14)",
                "tags[3]: expected a string, found a boolean (line 2, column 22)",
                "server.port: 3000000000 is out of range for int (line 6, column 8)",
                "server.hots: unknown key (line 7, column 1)"),
        errors(e));
    assertEquals(String.join("\n", errors(e)), e.getMessage());
    assertEquals("server.port", e.errors().get(3).path());
    assertEquals(6, e.errors().get(3).position().line());
  }

  @Test
  void ignoresUnknownKeysWhenAsked() {
    Server server = Toml
        .parse("host = \"a\"\nport = 1\nextra = true")
        .as(Server.class, TomlBindOptions.defaults().withUnknownKeysIgnored(true));
    assertEquals(new Server("a", 1), server);
  }

  record Naming(String maxConnections, String httpURL, @TomlName("host-name") String host) {}

  @Test
  void convertsNamesToKeys() {
    Naming snake = Toml
        .parse("max_connections = \"1\"\nhttp_url = \"u\"\nhost-name = \"h\"")
        .as(Naming.class, TomlBindOptions.defaults().withKeyNaming(TomlBindOptions.KeyNaming.SNAKE_CASE));
    assertEquals(new Naming("1", "u", "h"), snake);

    Naming kebab = Toml
        .parse("max-connections = \"1\"\nhttp-url = \"u\"\nhost-name = \"h\"")
        .as(Naming.class, TomlBindOptions.defaults().withKeyNaming(TomlBindOptions.KeyNaming.KEBAB_CASE));
    assertEquals(new Naming("1", "u", "h"), kebab);

    Naming exact = Toml.parse("maxConnections = \"1\"\nhttpURL = \"u\"\nhost-name = \"h\"").as(Naming.class);
    assertEquals(new Naming("1", "u", "h"), exact);
  }

  @Test
  void convertsNamesToSnakeCaseByWord() {
    TomlBindOptions.KeyNaming snake = TomlBindOptions.KeyNaming.SNAKE_CASE;
    assertEquals("max_connections", snake.keyFor("maxConnections"));
    assertEquals("url_path", snake.keyFor("URLPath"));
    assertEquals("http2_port", snake.keyFor("http2Port"));
    assertEquals("ip_v6_address", snake.keyFor("ipV6Address"));
    assertEquals("name", snake.keyFor("name"));
  }

  enum Mode {
    READ_ONLY, READ_WRITE
  }

  record Scalars(
      long l,
      short s,
      byte b,
      BigInteger big,
      double fromInt,
      float f,
      BigDecimal decimal,
      char c,
      Mode mode,
      Mode lenientMode,
      Instant instant,
      ZonedDateTime zoned,
      LocalDate date,
      LocalTime time,
      Object raw,
      TomlTable table) {}

  @Test
  void bindsScalars() {
    Scalars s = Toml.parse("""
        l = 9007199254740993
        s = -2
        b = 7
        big = 12
        fromInt = 3
        f = 1.5
        decimal = 0.1
        c = "x"
        mode = "READ_ONLY"
        lenientMode = "read-write"
        instant = 1979-05-27T07:32:00Z
        zoned = 1979-05-27T07:32:00-07:00
        date = 1979-05-27
        time = 07:32:00
        raw = [1, "a"]
        table = { a = 1 }
        """).as(Scalars.class);
    assertEquals(9007199254740993L, s.l());
    assertEquals((short) -2, s.s());
    assertEquals((byte) 7, s.b());
    assertEquals(BigInteger.valueOf(12), s.big());
    assertEquals(3.0, s.fromInt());
    assertEquals(1.5f, s.f());
    assertEquals(new BigDecimal("0.1"), s.decimal());
    assertEquals('x', s.c());
    assertEquals(Mode.READ_ONLY, s.mode());
    assertEquals(Mode.READ_WRITE, s.lenientMode());
    assertEquals(Instant.parse("1979-05-27T07:32:00Z"), s.instant());
    assertEquals(-7 * 3600, s.zoned().getOffset().getTotalSeconds());
    assertEquals(LocalDate.of(1979, 5, 27), s.date());
    assertEquals(LocalTime.of(7, 32), s.time());
    assertInstanceOf(TomlArray.class, s.raw());
    assertEquals(1L, s.table().getLong("a"));
  }

  record Editable(MutableTomlTable table, MutableTomlArray array) {}

  @Test
  void bindsMutableTablesAndArraysAsCopies() {
    TomlParseResult document = Toml.parse("array = [1, 2]\n[table]\na = 1\n");
    Editable editable = document.as(Editable.class);
    editable.table().set("b", 2L);
    editable.array().add(3L);
    assertEquals(2L, editable.table().getLong("b"));
    assertEquals(List.of(1L, 2L, 3L), editable.array().toList());
    assertEquals("array = [1, 2]\n[table]\na = 1\n", document.toToml());
    assertEquals(
        List.of("table: expected a table, found an integer (line 1, column 9)"),
        errors(bindFails("table = 1\narray = []", Editable.class)));
  }

  record ModeHolder(Mode mode) {}

  @Test
  void bindsEnumConstantsIgnoringCaseAndSeparators() {
    for (String mode : List.of("read_write", "read-write", "Read Write")) {
      assertEquals(Mode.READ_WRITE, Toml.parse("mode = \"" + mode + "\"").as(ModeHolder.class).mode(), mode);
    }
  }

  record Numbers(double d, byte b, Mode mode, char c) {}

  @Test
  void reportsScalarConversionErrors() {
    TomlBindException e = bindFails("""
        d = 9007199254740993
        b = 128
        mode = "APPEND"
        c = "xy"
        """, Numbers.class);
    assertEquals(
        List
            .of(
                "d: 9007199254740993 cannot be represented exactly as a double (line 1, column 5)",
                "b: 128 is out of range for byte (line 2, column 5)",
                "mode: \"APPEND\" is not one of READ_ONLY, READ_WRITE (line 3, column 8)",
                "c: expected a string of one character (line 4, column 5)"),
        errors(e));
  }

  static class Containers {
    int[] ints;
    String[][] nested;
    Set<String> set;
    SortedSet<String> sorted;
    ArrayDeque<Long> deque;
    Map<String, Integer> map;
    List<Optional<String>> optionals;
  }

  @Test
  void bindsContainers() {
    Containers c = Toml.parse("""
        ints = [1, 2]
        nested = [["a"], ["b", "c"]]
        set = ["x", "x", "y"]
        sorted = ["b", "a"]
        deque = [1]
        optionals = ["o"]
        [map]
        one = 1
        two = 2
        """).as(Containers.class);
    assertArrayEquals(new int[] {1, 2}, c.ints);
    assertArrayEquals(new String[][] {{"a"}, {"b", "c"}}, c.nested);
    assertEquals(List.of("x", "y"), new ArrayList<>(c.set));
    assertEquals(List.of("a", "b"), new ArrayList<>(c.sorted));
    assertEquals(List.of(1L), new ArrayList<>(c.deque));
    assertEquals(List.of("one", "two"), new ArrayList<>(c.map.keySet()));
    assertEquals(2, c.map.get("two"));
    assertEquals(List.of(Optional.of("o")), c.optionals);
  }

  record Timeout(Duration timeout) {}

  @Test
  void usesConverters() {
    TomlBindOptions options =
        TomlBindOptions.defaults().withConverter(Duration.class, value -> Duration.parse((String) value));
    assertEquals(Duration.ofSeconds(5), Toml.parse("timeout = \"PT5S\"").as(Timeout.class, options).timeout());

    TomlBindException e = bindFails("timeout = \"5 seconds\"", Timeout.class, options);
    assertEquals(1, e.errors().size());
    assertEquals("timeout", e.errors().get(0).path());
    assertEquals("Text cannot be parsed to a Duration", e.errors().get(0).message());
    assertEquals(1, e.errors().get(0).position().line());
    assertInstanceOf(DateTimeParseException.class, e.errors().get(0).cause());
    assertSame(e.errors().get(0).cause(), e.getSuppressed()[0]);
  }

  record Port(int value) {
    Port {
      if (value < 1 || value > 65535) {
        throw new IllegalArgumentException("port " + value + " is not between 1 and 65535");
      }
    }
  }

  record Listener(Port port) {}

  @Test
  void reportsConstructorExceptionsAtTheTable() {
    TomlBindException e = bindFails("[port]\nvalue = 0", Listener.class);
    assertEquals(List.of("port: port 0 is not between 1 and 65535 (line 1, column 1)"), errors(e));
    assertInstanceOf(IllegalArgumentException.class, e.errors().get(0).cause());
    assertEquals(1, e.getSuppressed().length);
  }

  @Test
  void keepsNoCauseForErrorsFoundByBinding() {
    TomlBindException e = bindFails("port = \"x\"", Listener.class);
    assertNull(e.errors().get(0).cause());
    assertEquals(0, e.getSuppressed().length);
  }

  record Box<T>(T value)
  {
  }

  static class Holder<T> {
    List<T> items;
  }

  static class StringHolder extends Holder<String> {
  }

  @Test
  void bindsGenericTypes() {
    Map<String, Server> servers = Toml.parse("""
        [a]
        host = "a"
        port = 1
        [b]
        host = "b"
        port = 2
        """).as(new GenericType<Map<String, Server>>() {});
    assertEquals(new Server("b", 2), servers.get("b"));

    Box<List<Integer>> box = Toml.parse("value = [1, 2]").as(new GenericType<Box<List<Integer>>>() {});
    assertEquals(List.of(1, 2), box.value());

    StringHolder holder = Toml.parse("items = [\"x\"]").as(StringHolder.class);
    assertEquals(List.of("x"), holder.items);

    TomlBindException e = bindFails("items = [1]", StringHolder.class);
    assertEquals(List.of("items[0]: expected a string, found an integer (line 1, column 10)"), errors(e));
  }

  record Pair<A,B>(
  A first,
  @Nullable
  Pair<B, A> swapped)
  {
  }

  record Growing<T>(T value, List<Growing<List<T>>>children)
  {
  }

  @Test
  void bindsGenericTypesThatHoldThemselves() {
    Pair<String, Long> pair =
        Toml.parse("first = \"a\"\n[swapped]\nfirst = 1\n").as(new GenericType<Pair<String, Long>>() {});
    assertEquals(new Pair<>("a", new Pair<>(1L, null)), pair);

    TomlParseResult result = Toml.parse("value = 1\nchildren = []\n");
    assertEquals(
        "Cannot bind to org.tomlj.TomlBindTest$Growing: its members use it with type arguments that grow without end",
        assertThrows(IllegalArgumentException.class, () -> result.as(new GenericType<Growing<Long>>() {}))
            .getMessage());
  }

  @Test
  void bindsArrays() {
    TomlArray array =
        Toml.parse("servers = [{ host = \"a\", port = 1 }, { host = \"b\", port = 2 }]").getArray("servers");
    Server[] servers = array.as(Server[].class);
    assertEquals(new Server("b", 2), servers[1]);
    List<Server> list = array.as(new GenericType<List<Server>>() {});
    assertEquals(List.of(new Server("a", 1), new Server("b", 2)), list);

    TomlBindException e =
        assertThrows(TomlBindException.class, () -> Toml.parse("a = [1, \"x\"]").getArray("a").as(long[].class));
    assertEquals(List.of("[1]: expected an integer, found a string (line 1, column 9)"), errors(e));
  }

  record Node(String name, @Nullable List<Node> children) {}

  @Test
  void bindsRecursiveTypes() {
    Node tree = Toml.parse("""
        name = "root"
        [[children]]
        name = "a"
        [[children.children]]
        name = "b"
        """).as(Node.class);
    assertEquals(new Node("root", List.of(new Node("a", List.of(new Node("b", null))))), tree);
  }

  @Test
  void quotesKeysInPaths() {
    TomlBindException e = assertThrows(
        TomlBindException.class,
        () -> Toml.parse("[\"a b\"]\nhost = 1\nport = 2").as(new GenericType<Map<String, Server>>() {}));
    assertEquals(List.of("\"a b\".host: expected a string, found an integer (line 2, column 8)"), errors(e));
  }

  @NullMarked
  record StrictConfig(String name, Server server, List<String> tags) {}

  @Test
  void bindsWhatParsed() {
    TomlParseResult result = Toml.parse("name = \"demo\"\ntags = [\n[server]\nhost = \"a\"\nport = 1");
    assertEquals(
        List.of("Unexpected end of line, expected ] or a value (line 2, column 9)"),
        result.errors().stream().map(TomlParseError::toString).collect(Collectors.toList()));
    assertEquals(new Config("demo", new Server("a", 1), null), result.as(Config.class));

    // The key on the line the parser rejected is also reported as missing
    TomlBindException e = assertThrows(TomlBindException.class, () -> result.as(StrictConfig.class));
    assertEquals(List.of("tags: missing (line 1, column 1)"), errors(e));
  }

  @Test
  void bindsBuiltTables() {
    MutableTomlTable table = MutableTomlTable.create();
    table.set("host", "a");
    table.set("port", 3000000000L);
    TomlBindException e = assertThrows(TomlBindException.class, () -> table.as(Server.class));
    assertEquals(List.of("port: 3000000000 is out of range for int"), errors(e));
  }

  interface Shape {
  }

  record Drawing(Shape shape) {}

  static class NoDefaultConstructor {
    NoDefaultConstructor(String unused) {}
  }

  record Duplicate(@TomlName("x") String a, @TomlName("x") String b) {}

  record WithThread(Thread thread) {}

  record IntKeys(Map<Integer, String> map) {}

  @Test
  void defaultOptionsLetAClassLoaderBeUnloaded() throws Exception {
    WeakReference<ClassLoader> loader = bindInLoaderOfItsOwn();
    for (int i = 0; i < 100 && loader.get() != null; i++) {
      System.gc();
      Thread.sleep(10);
    }
    assertNull(loader.get());
  }

  private static WeakReference<ClassLoader> bindInLoaderOfItsOwn() throws Exception {
    String name = Unloadable.class.getName();
    byte[] bytes;
    try (InputStream in = Unloadable.class.getResourceAsStream("Unloadable.class")) {
      bytes = in.readAllBytes();
    }
    ClassLoader loader = new ClassLoader(TomlBindTest.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        if (!className.equals(name)) {
          return super.loadClass(className, resolve);
        }
        synchronized (getClassLoadingLock(className)) {
          Class<?> loaded = findLoadedClass(className);
          return (loaded != null) ? loaded : defineClass(className, bytes, 0, bytes.length);
        }
      }
    };
    Class<?> type = loader.loadClass(name);
    assertNotSame(Unloadable.class, type);
    Object bound = Toml.parse("name = \"a\"").as(type);
    assertEquals("name = \"a\"\n", MutableTomlTable.from(bound).toToml());
    return new WeakReference<>(loader);
  }

  @Test
  void serializesBindExceptions() throws Exception {
    TomlBindException e = bindFails("[port]\nvalue = 0", Listener.class);
    TomlBindException copy = serializedCopy(e);
    assertEquals(e.getMessage(), copy.getMessage());
    assertEquals(e.errors(), copy.errors());
    assertInstanceOf(IllegalArgumentException.class, copy.errors().get(0).cause());
  }

  @SuppressWarnings("unchecked")
  static <T> T serializedCopy(T value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) in.readObject();
    }
  }

  @Test
  void rejectsTypesItCannotBind() {
    TomlParseResult empty = Toml.parse("");
    assertEquals(
        "Cannot bind org.tomlj.TomlBindTest$Drawing.shape: Cannot bind to org.tomlj.TomlBindTest$Shape: it is an "
            + "interface or abstract class; register a converter for it in TomlBindOptions",
        assertThrows(IllegalArgumentException.class, () -> empty.as(Drawing.class)).getMessage());
    assertEquals(
        "Cannot bind to org.tomlj.TomlBindTest$NoDefaultConstructor: it has no constructor without parameters",
        assertThrows(IllegalArgumentException.class, () -> empty.as(NoDefaultConstructor.class)).getMessage());
    assertEquals(
        "Cannot bind to org.tomlj.TomlBindTest$Duplicate: a and b are both bound to the key x",
        assertThrows(IllegalArgumentException.class, () -> empty.as(Duplicate.class)).getMessage());
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> empty.as(WithThread.class))
            .getMessage()
            .contains("TomlJ does not bind to it"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> empty.as(IntKeys.class))
            .getMessage()
            .contains("the keys of a map must be strings"));
  }

  @Test
  void reportsTypeErrorsAtTheRoot() {
    TomlBindException e = assertThrows(TomlBindException.class, () -> Toml.parse("").as(Integer.class));
    assertEquals(List.of("expected an integer, found a table (line 1, column 1)"), errors(e));
  }
}
