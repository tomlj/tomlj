# TomlJ: A Java parser and serializer for Tom's Obvious, Minimal Language (TOML)

TomlJ is a complete [TOML](https://toml.io/) parser and serializer for Java, built on the
venerable [ANTLR](https://github.com/antlr/antlr4/) parser-generator and runtime library.

```java
TomlParseResult result = Toml.parse(Paths.get("config.toml"));
result.errors().forEach(error -> System.err.println(error.toString()));

String host = result.getString("server.host");
long port = result.getLong("server.port", () -> 8080);
```

The [examples](examples) directory has complete programs that show how to use TomlJ, from reading a
configuration file to editing a document in place and binding it to records.

## Error reporting and recovery

*TomlJ never throws on invalid input.* Every error is recorded with its position, and parsing
continues at the next expression, so one run finds every problem in a file instead of stopping at
the first. Editors, IDEs and linters need this, and so does any tool that wants to show a user the
whole list of things to fix rather than one mistake per run.

Every error says what was expected and where. If something was left unclosed, it also points back at
where that was opened:

```java
Toml.parse("deps = [\n  \"a\",\n  \"b\"\n").errors();
// Unexpected end of input, expected ] or a comma;
//   the array opened at line 1, column 8 is unclosed (line 4, column 1)
```

Values know where they came from as well, so your own checks can point at the file the same way:

```java
long port = result.getLong("server.port");
if (port > 65535) {
  System.err.println("config.toml:" + result.inputPositionOf("server.port").line() + ": port out of range");
}
```

## Other highlights

* **Complete, and tested against the spec.** TomlJ supports TOML 1.1.0. Every build runs the
  official [toml-test](https://github.com/toml-lang/toml-test) suite for 1.0.0 and 1.1.0: valid
  files must give exactly the expected values, and invalid files must be rejected.
* **Documents can be built and edited.** A parse result is a `MutableTomlTable`: set, insert and
  remove values and comments, or build a document from scratch, and write it out with `toToml()`.
  See [Building and editing documents](#building-and-editing-documents).
* **Edited documents keep their format and layout.** `toToml()` writes a document identical to
  the parsed source except where it was edited: comments, blank lines, key order, indentation and
  the notation of each value (`0xFF`, `'literal'`, `{ a = 1 }`) stay as they were written. See
  [Writing TOML](#writing-toml).
* **Tables bind to records and classes, and back.** `Toml.parseAs(file, Config.class)` binds a
  document to a record or a class, converting each value to the type declared for it, and reports
  every value that does not fit with its path and position. `result.update(config)` writes a
  changed object back into the document, touching only the values that differ. See
  [Binding to Java objects](#binding-to-java-objects).
* **A typed getter for every TOML type**, returning `String`, `Long`, `Double`, `Boolean`,
  `TomlArray` or `TomlTable`, and the four date and time types as `java.time`'s `OffsetDateTime`,
  `LocalDateTime`, `LocalDate` and `LocalTime`. Each getter returns `null` if the key is missing,
  throws `TomlInvalidTypeException` if the value is the wrong type, and has an overload that takes
  a default.
* **Reads TOML 1.0.0 too.** `TomlVersion.V1_0_0` reports 1.1.0 syntax as an error instead of
  accepting it.
* **No dependencies.** The jar carries its own copy of the ANTLR runtime, relocated under TomlJ's
  own package, so there is nothing else to add and no clash with ANTLR elsewhere in your project.
  Works on Java 9 and later.

## Usage

You can parse a `String`, `Path`, `InputStream`, `Reader` or `ReadableByteChannel`. Check
`hasErrors()` before using the result:

```java
Path source = Paths.get("/path/to/file.toml");
TomlParseResult result = Toml.parse(source);
result.errors().forEach(error -> System.err.println(error.toString()));

String value = result.getString("a. dotted . key");
```

### Keys

Methods that take a `String` key read it as a dotted key, using TOML syntax. Keys with characters
outside `A-Z`, `a-z`, `0-9`, `_` and `-` must be quoted, just as they would be in a TOML file.
Methods that take a `List<String>` use each element as a key exactly as given, with no quoting. Use
those when working with keys from `keySet()` or `entrySet()`:

```java
String quoted = result.getString("\"@key#with$special%characters\"");
String literal = result.getString(Collections.singletonList("@key#with$special%characters"));
```

### Building and editing documents

`MutableTomlTable` and `MutableTomlArray` extend `TomlTable` and `TomlArray` with mutators. A
document can be built from scratch and written out with `toToml()`:

```java
MutableTomlTable doc = MutableTomlTable.create();
doc.set("title", "Example");
doc.set("owner.name", "Tom");
doc.getOrCreateTable("database").set("ports", MutableTomlArray.of(8001, 8002));
String toml = doc.toToml();
```

A parse result is itself a `MutableTomlTable`, so a document can be parsed, changed and written
back:

```java
TomlParseResult result = Toml.parse(source);
result.set("owner.name", "Chris");
result.remove("title");
Files.writeString(source, result.toToml());
```

`set` and `add` put a new entry after the last element, and `insertBefore` and `insertAfter` put one
next to an existing entry. A value parsed from its TOML text, `TomlValue.parse("0xFF")`, or made
with a notation factory, `TomlValue.hex(255)`, is written back in that notation, and a table made
with `MutableTomlTable.createInline()` is written between braces on its entry's line. Comments are
set and removed by placement: a run above an entry, the comment after it, or an unattached comment
of a table or array.
[docs/editing.md](docs/editing.md) describes the mutators in full: the values they accept, where a
new entry goes, and how comments are edited.

### Writing TOML

`toToml()` writes a parse result from the text it was read from: an unedited document comes back
byte for byte, and an edit changes only the lines it touches. `toToml(TomlWriteOptions)` chooses how
much of that is kept, with `keep(TomlWriteOptions.Keep)`: `LAYOUT`, the default, keeps every line
as it was read; `NOTATION` keeps the form of each key, value and table, the order of lines and the
comments, and lays the document out anew; `NOTHING` writes the whole document in the default
style, as a document built through the editing API is written. `withIndent`, `withMaxLineWidth`,
`withLineSeparator` and `withVersion` shape whatever is written anew:

```java
TomlWriteOptions options = TomlWriteOptions.defaults()
    .keep(TomlWriteOptions.Keep.NOTATION)
    .withIndent(2);
String reindented = result.toToml(options);
```

`reformat(TomlWriteOptions.Keep)` on a `MutableTomlTable` or `MutableTomlArray` does the same for
one table or array and everything nested in it:

```java
result.getTable("server").reformat(TomlWriteOptions.Keep.NOTHING);
```

Parse with `TomlParseOptions.sourceless()` to save the memory the text takes; the result is written
in the default style. [docs/writing.md](docs/writing.md) describes writing in full: where each kind
of edit lands, what each amount keeps, the default style and the options.

### Binding to Java objects

`Toml.parseAs` parses a document and binds it to a record, a class or a map, and `as` binds any
table or array of a parsed document to a record, a class, a collection, a map or a Java array:

```java
record Server(String host, int port) {}

record Config(String name, List<Server> servers) {}

Config config = Toml.parseAs(source, Config.class);
```

Each key is bound to the record component or field of the same name, or of another style of name
with `TomlBindOptions.withKeyNaming`, or to the key given by `@TomlName`. A key the document does not
have leaves an `Optional` empty and a field of a class at its initial value, and is otherwise `null`,
or an error for a component or field that is never `null`: a primitive, one annotated `NonNull`, or
one in a class or package annotated with JSpecify's `@NullMarked`. A key that names nothing is an
error unless the options ignore unknown keys. Binding reports every error at once, in a
`TomlBindException`, each with its path and position:

```
servers[1].port: expected an integer, found a string (line 9, column 8)
servers[1].hots: unknown key (line 10, column 1)
```

`parseAs` binds nothing from a document with parse errors, and reports them in a
`TomlParseException`.

Converters in `TomlBindOptions` bind other types, such as `Duration`, and a `GenericType` names a
generic type, such as `Map<String, Server>`.

Objects are written back the same way. `Toml.toToml(config)` writes an object as a new document,
`MutableTomlTable.from(config)` creates a table from one, and `update` changes a document to hold
one, leaving each value that already binds to the same value as it was, with its comments and the
way it is written:

```java
TomlParseResult document = Toml.parse(source);
Config config = document.as(Config.class);
document.update(new Config("staging", config.servers()));
Files.writeString(source, document.toToml());
```

[docs/binding.md](docs/binding.md) describes binding in full: the types bound, keys, missing keys,
converters, errors, and writing objects.

### Comments

Every comment in a document is kept. A comment on the same line as an entry, or a run of comment
lines directly above it, is attached to that entry. Every other comment is unattached, and belongs to
the table or array it was written in:

```toml
# The port clients connect to.
port = 8080 # not 80

# Everything below is optional.

[server]
```

```java
for (TomlComment comment : result.comments("port")) {
  System.out.println(comment.placement() + ": " + comment.text());
}
// ABOVE: The port clients connect to.
// AFTER: not 80
```

The unattached comment is read through `elements()`, which lists a table's entries and unattached
comments together, in document order. Comments are set and removed through the editing API, and
`toToml()` writes every comment back where it was read from. [docs/comments.md](docs/comments.md)
states the rules in full: how a comment's text is read, which table an unattached comment belongs
to, how comments are edited and written, and the cases at their edges.

### Specification version

Parsing uses the newest supported version of TOML unless you ask for an older one. To check a file
against an earlier version, pass a `TomlVersion`:

```java
TomlParseResult result = Toml.parse(source, TomlVersion.V1_0_0);
```

## Getting TomlJ

TomlJ is published to Maven Central. `org.tomlj:tomlj` is a single jar with no dependencies: it
carries its own copy of the ANTLR runtime, under TomlJ's own package, so it cannot clash with any
other ANTLR in your project.

To include using Maven:
```xml
<dependency>
  <groupId>org.tomlj</groupId>
  <artifactId>tomlj</artifactId>
  <version>2.1.1</version>
</dependency>
```

To include using Gradle: `implementation 'org.tomlj:tomlj:2.1.1'`

If your project already uses ANTLR and you would rather share one copy of the runtime, use
`org.tomlj:tomlj-antlr` instead. It is the same library, depending on `org.antlr:antlr4-runtime`
rather than bundling it.

## Links

- [GitHub project](https://github.com/tomlj/tomlj)
- [Online Java documentation](https://tomlj.org/docs/java/latest/org/tomlj/package-summary.html)
- [Issue tracker: Report a defect or feature request](https://github.com/tomlj/tomlj/issues/new)
