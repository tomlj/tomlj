# Binding to Java objects

`as` binds a table or an array to a Java type: a record, a class, a collection, a map or a Java
array. The values are converted to the types declared for them, and every value that cannot be
bound is reported with its path and position. `from` and `update` do the reverse, writing an object
as a table or into a document. This document states which types are bound and how, which keys are
missing or unknown, how errors are reported, and how objects are written.

Binding copies values out of the document. The objects it creates are not connected to the table
they were bound from, and changing one does not change the other.

## Binding a table

```java
record Server(String host, int port) {}

record Config(String name, List<Server> servers) {}

TomlParseResult result = Toml.parse(Paths.get("config.toml"));
Config config = result.as(Config.class);
```

```toml
name = "production"

[[servers]]
host = "alpha.example.com"
port = 8001

[[servers]]
host = "beta.example.com"
port = 8002
```

Each key of the table is bound to the record component, or the field of a class, with the same
name, and each value to the type declared for it. The binding goes as deep as the types do: here
each table of the array `servers` is bound to a `Server`.

Any table or array of a document can be bound on its own, not only the document itself:

```java
Server first = result.getArray("servers").getTable(0).as(Server.class);
Server[] servers = result.getArray("servers").as(Server[].class);
```

`as(Class)` binds with the default options, and `as(Class, TomlBindOptions)` with the options
given. `TomlBindOptions` is immutable: `defaults()` gives the default options, and each `with`
method returns a copy with one option changed. Options also hold what they learn about each Java
type bound with them, so one set of options kept and reused for many bindings examines each class
only once.

A parse result with errors is bound as it is: a value that parsed is bound, and a key on a line that
could not be parsed is missing from the result, so it is bound as any missing key is (see
[Missing keys](#missing-keys)). Check `hasErrors()` first to report parse errors on their own.

## Records

A table is bound to a record through its canonical constructor, with a key for each component. The
record's constructor runs as it does for any other caller, so a compact constructor can check or
normalize what it is given; an exception it throws is reported as an error at the table (see
[Errors](#errors)). The constructor is called only once every component has been bound without
error, so its checks see only values of the right types.

Records nest, and a record may hold itself:

```java
record Node(String name, List<Node> children) {}
```

TomlJ compiles for Java 9 and finds records by reflection, so records are bound on any Java version
that has them.

## Classes

A table is bound to a class by creating an instance with its constructor without parameters, then
setting a field for each key. The constructor may be private. Every field of the class and of its
superclasses is bound, except static, transient and final fields. A final field cannot be set, so
a key for one is reported as an error that says the field is final, where another key would be
reported as unknown. To bind a class with final fields, make it a record or give it a converter.

A field whose key the table does not have keeps the value the constructor left in it, so the field
initializers of a class are its defaults:

```java
final class ServerSettings {
  private String host = "127.0.0.1";
  private int port = 80;
  private Duration timeout = Duration.ofSeconds(10);
}
```

A class that is not static and is declared inside another class cannot be created without an
instance of the class around it, and cannot be bound; declare it `static`.

## Keys

The key of a record component or field is its name. `withKeyNaming` converts names to keys in
another style, which suits the keys usually written in TOML files:

| `KeyNaming` | Field `maxConnections` | Field `httpURL` |
|---|---|---|
| `EXACT` (the default) | `maxConnections` | `httpURL` |
| `SNAKE_CASE` | `max_connections` | `http_url` |
| `KEBAB_CASE` | `max-connections` | `http-url` |

A name is split into words where a lower-case letter or a digit is followed by an upper-case
letter, and before the last upper-case letter of a run that is followed by a lower-case letter, so
`http2Port` is `http2_port` and `URLPath` is `url_path`.

`@TomlName` gives the key of a component or field itself, and the key naming is not applied to it:

```java
record Proxy(@TomlName("trust-x-forwarded-for") boolean trustForwarded, int port) {}
```

The value of `@TomlName` is a single key, used as written: `@TomlName("a.b")` names the key `"a.b"`,
not the key `b` in the table `a`.

A key of the table that names no component or field is an error, reported as `unknown key` at the
key's position. This finds a misspelled key, which would otherwise be read as missing and silently
given its default. `withUnknownKeysIgnored(true)` ignores such keys instead, for a file that holds
settings for more than one program:

```java
TomlBindOptions options = TomlBindOptions.defaults()
    .withKeyNaming(KeyNaming.KEBAB_CASE)
    .withUnknownKeysIgnored(true);
```

## Missing keys

A key that the table does not have is bound as follows:

- An `Optional` component or field is empty.
- A field of a class keeps the value it was given when the instance was created.
- A record component, or a field of a class that is `null` once the instance is created, is `null`
  if it is nullable, and a `missing` error if it is never `null`.

A primitive field of a class always has a value once the instance is created, `0` or `false` if the
class gives it none, so its key is never reported as missing. To require a key in a class, declare
its field with the boxed type and no initial value, and mark it as never `null`:
`@NonNull Integer port;`.

A primitive is never `null`. For any other type, TomlJ reads the nullness annotations of the
component or field:

- An annotation named `NonNull`, `NotNull` or `Nonnull` marks it as never `null`, and one named
  `Nullable` or `CheckForNull` marks it as nullable. The annotation may come from any package,
  since only its simple name is read: JSpecify, the Checker Framework, JSR 305 and others all work.
- Without either, it is never `null` if its class, a class enclosing it, its package or its module
  is annotated with JSpecify's `@NullMarked`, unless a nearer one of those is annotated
  `@NullUnmarked`.
- Otherwise it is nullable.

So a code base that uses JSpecify, with `@NullMarked` on its packages, gets a `missing` error for
each key it does not mark `@Nullable`:

```java
@NullMarked
package com.example.config;
```

```java
record Server(String host, int port, @Nullable String description, Optional<Integer> backlog) {}
```

Here `host` must be present, and `port` too since it is primitive, while `description` is `null`
and `backlog` empty when they are missing.

The annotations are read by reflection when the program runs, so they must be kept at run time and
their classes must be on the run-time class path. JSpecify, the Checker Framework and JSR 305
annotations are kept at run time; JetBrains' `@NotNull` and `@Nullable` are not, and TomlJ does
not see them.

## Values

The value of each key or element is bound to the type declared for it:

| TOML value | Java types |
|---|---|
| String | `String`; an enum constant; `char`, for a string of one character |
| Integer | `long`, `int`, `short`, `byte`, `BigInteger`; `double`, `float` and `BigDecimal` if the value can be represented exactly |
| Float | `double`, `float`, `BigDecimal` |
| Boolean | `boolean` |
| Offset date-time | `OffsetDateTime`, `ZonedDateTime`, `Instant` |
| Local date-time, date, time | `LocalDateTime`, `LocalDate`, `LocalTime` |
| Array | `List`, `Set`, `SortedSet`, `Collection`, `Iterable`, a concrete collection class, a Java array |
| Table | a record, a class, `Map`, `SortedMap`, a concrete map class |
| Table or array | `TomlTable` or `TomlArray`, unchanged; `MutableTomlTable` or `MutableTomlArray`, a copy, so that changing it does not change the document |
| Any value | `Object`, unchanged; `Optional<T>`, by binding the value to `T` |

The boxed types are bound as their primitives are. An integer out of range for the type it is bound
to is an error, as is an integer bound to a floating point type that cannot hold it exactly, and a
float too large for a `float`. A float bound to a `float` is rounded to the nearest `float`.

An enum constant is matched by its name, or else by its name ignoring case and reading `-` and space
as `_`, so `"on-success"` and `"on success"` are bound to `ON_SUCCESS`. A string that matches no
constant is an error that lists the constants.

A `List`, `Collection` or `Iterable` is bound to an `ArrayList`, a `Set` to a `LinkedHashSet`, a
`SortedSet` to a `TreeSet`, a `Map` to a `LinkedHashMap` and a `SortedMap` to a `TreeMap`. A
concrete collection or map class is created with its constructor without parameters. The keys of a
map must be `String`, `CharSequence` or `Object`.

A type not in the table needs a converter (see [Converters](#converters)). Binding to any other
interface or abstract class, to another class of the JDK, such as `Duration` or `Path`, or to a map whose keys
are not strings, throws `IllegalArgumentException` before any value is read.

## Generic types

A `Class` cannot name a generic type such as `Map<String, Server>`. A `GenericType` names it, as
an anonymous subclass whose type argument is the type to bind to:

```java
Map<String, Server> hosts = result.getTable("hosts").as(new GenericType<Map<String, Server>>() {});
List<String> tags = result.getArray("tags").as(new GenericType<List<String>>() {});
```

The type arguments of a generic record or class are followed through its fields: given
`record Box<T>(T value) {}`, a component declared `Box<Server>` binds `value` as a `Server`. A
subclass that fixes the type arguments of its superclass, `class StringHolder extends Holder<String>`,
binds the inherited fields with those arguments.

## Converters

`withConverter` binds a type with a function. The function is given the value as it is in the
document: a `String`, `Long`, `Double`, `Boolean`, `OffsetDateTime`, `LocalDateTime`, `LocalDate`,
`LocalTime`, `TomlArray` or `TomlTable`. It returns the value to bind, which must not be `null`.

```java
TomlBindOptions options = TomlBindOptions.defaults()
    .withConverter(Path.class, value -> Path.of((String) value))
    .withConverter(Duration.class, value -> Duration.parse((String) value));
```

A converter is used for every component, field and element declared with exactly its type, and
takes the place of the binding TomlJ would use otherwise, so a converter for `String` or for a
record of your own changes how those are bound. A `RuntimeException` thrown by the converter is
reported as an error at the value, with the exception's message, so a converter that checks its
input and throws with a clear message gives a clear error.

To write values of the type too (see [Writing objects](#writing-objects)), give a second function,
which returns the TOML value for one: any value `MutableTomlTable.set` accepts, such as a `String`,
a number, a `Map` or a `List`, or a `TomlValue` to choose how it is written:

```java
TomlBindOptions options = TomlBindOptions.defaults()
    .withConverter(Duration.class, value -> Duration.parse((String) value), Duration::toString)
    .withConverter(Permissions.class, value -> Permissions.of((Long) value), p -> TomlValue.octal(p.bits()));
```

A type whose converter only reads cannot be written.

## Errors

Binding does not stop at the first error. Every value is bound, and the errors are thrown together
in a `TomlBindException`. Its `errors()` lists each as a `TomlBindError`, in the order of the
document, and its message has one line for each:

```
start: expected a local time, found a string (line 2, column 9)
retries: 3000000000 is out of range for int (line 3, column 11)
timout: unknown key (line 4, column 1)
step[1].command: missing (line 10, column 1)
step[1].when: "on-sucess" is not one of ALWAYS, ON_SUCCESS, ON_FAILURE (line 12, column 8)
step[2].command: expected a string, found an array (line 16, column 11)
```

A `TomlBindError` has three parts:

- `path()` is the dotted key of the value within the table or array that was bound, with `[i]` for
  the element at index `i` of an array. It is empty for the table or array itself.
- `message()` says what is wrong, without the path or position.
- `position()` is where the value starts in the document. For an unknown key it is the position of
  the key, and for a missing key the position of the table the key is missing from: its header, or
  the start of the document for the root table. It is `null` for a value that was not parsed from
  a document, such as a value set with the editing API, and errors without a position come after
  the others.

An exception thrown by a record's constructor or by a converter is reported as an error with the
exception's message, at the position of the table or value being bound. The error's `cause()` is
the exception, and the `TomlBindException` holds each such exception as a suppressed exception, so
a stack trace printed for it shows where each was thrown.

`TomlBindException` reports a document that does not fit its types. A type that cannot be bound at
all, whatever the document holds, is a mistake in the program: binding to it throws
`IllegalArgumentException` at once, before any value is read, naming the type and the field that
declared it. So do two fields bound to the same key, and a field TomlJ is not allowed to set (see
[Modules](#modules)).

## Modules

TomlJ creates instances and sets fields by reflection, including private ones. On the class path
nothing more is needed. In a named module, the package that holds the types must be opened to TomlJ:

```java
module com.example.app {
  requires org.tomlj;
  opens com.example.config to org.tomlj;
}
```

Otherwise binding throws `IllegalArgumentException`, naming the class TomlJ could not reach.

## Writing objects

`MutableTomlTable.from` writes a record, a class or a map as a new table, and
`MutableTomlArray.from` writes a collection or a Java array as a new array. They are the reverse of
`as`: the table binds back to an equal object with the same options.

```java
Config config = new Config("production", List.of(new Server("alpha.example.com", 8001)));
String toml = MutableTomlTable.from(config).toToml();
```

```toml
name = "production"

[[servers]]
host = "alpha.example.com"
port = 8001
```

Each member is written under the key it is bound to, with the key naming and `@TomlName` of the
options, in the order the members are declared, those of a superclass first. The object passed to
`from` is written as its own class, and a value within it as the type its member declares, so an
instance of a subclass held in a member declared as its superclass is written with the members of
the superclass only. The writer chooses where each goes: here the array of records is written as
an array of tables, after the other entries. Each value is written as follows:

| Java type | TOML value |
|---|---|
| `String`, `boolean`, `long`, `int`, `short`, `byte`, `double` | itself |
| `char` | a string of one character |
| `float` | the shortest decimal that reads back as it: `0.1f` is `0.1` |
| `BigInteger`, `BigDecimal` | an integer or float, if TOML can hold it exactly |
| `OffsetDateTime`, `LocalDateTime`, `LocalDate`, `LocalTime` | itself |
| `Instant` | an offset date-time in UTC |
| `ZonedDateTime` | an offset date-time, with the offset of its zone at that time |
| an enum constant | its name |
| a collection or Java array | an array |
| a record, a class, a map | a table |
| `TomlTable`, `TomlArray` | a copy of it |
| `Optional<T>` | its value, written as a `T` |
| `Object` | its value, written as a value of its own class |

A member that is `null`, or an empty `Optional`, is left out, and so is an entry of a map whose
value is. An element of a collection or array cannot be `null`, since a TOML array has no place for
a missing value.

The types that can be written are those that can be bound, except that a class written needs no
constructor without parameters, and may be an inner class that is not static. A type that cannot
be written, or one whose converter only reads, throws `IllegalArgumentException` before any value
is read. So does a value that TOML cannot hold, naming its path: an integer beyond 64 bits, a
`BigDecimal` with more precision than a float, a string with an unpaired surrogate, a year after
9999, a `null` element, or an object that holds itself.

## Updating a document

`update` changes a table or array to hold an object, and changes only the values that differ, so
a document read from a file can be changed through its objects and written back with its comments
and layout:

```java
TomlParseResult document = Toml.parse(source);
Config config = document.as(Config.class);
document.update(new Config("staging", config.servers()));
Files.writeString(source, document.toToml());
```

Each value is compared with the value in the document by binding the document's value to the type
of the member. If that gives an equal value, or one that writes as the same TOML value, such as the
`BigDecimal` values `1.5` and `1.50`, the document's value is left as it is, with the way it was
written: `0x1F` for `31`, `'literal'` for `literal`, `1` for `1.0`, `"on-success"` for
`ON_SUCCESS`, or `"30s"` for a duration read by a converter that accepts that form. Updating a
document with the object bound from it changes nothing, and `isModified()` stays `false`.

A value that differs is written as `from` writes it:

- A table is updated in place, key by key. The key of a member that is `null` or an empty
  `Optional` is removed. A key that names no member is left as it is, so a document can hold keys
  the object does not describe. A map or a `TomlTable` describes the whole table, so a key that it
  does not have is removed.
- An array is updated as a line diff changes a file. The elements the document already holds, in
  the same order and as many as there can be, are left as they are, with their comments. Between
  two of them, the elements of the document are updated in order by those of the list, as any value
  is, and the elements left over are inserted or removed. So an element inserted at the start of a
  list is inserted at the start of the array, and one removed from a list is removed with its
  comments. An element that is both moved and changed is removed and inserted anew, without its
  comments. Finding the elements left as they are compares each element of the document with each
  of the list, from the first that changed to the last; where that is more than 10,000 comparisons,
  those elements are updated index by index instead.
- Any other value, or a value of another type than the document has, is replaced. The entry keeps
  its place and the comments attached to it.
- A key added to a table is added after its other entries.

`update` stops at the first value it cannot write, throwing `IllegalArgumentException`, with the
values before it already changed.
