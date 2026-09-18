# TomlJ: A Java parser for Tom's Obvious, Minimal Language (TOML)

TomlJ is a complete [TOML](https://toml.io/) parser for Java, built on the
[ANTLR](https://github.com/antlr/antlr4/) parser-generator and runtime library.

```java
TomlParseResult result = Toml.parse(Paths.get("config.toml"));
result.errors().forEach(error -> System.err.println(error.toString()));

String host = result.getString("server.host");
long port = result.getLong("server.port", () -> 8080);
```

## Error reporting and recovery

*TomlJ never throws on invalid input.* Every error is recorded with its position, and parsing
continues at the next expression, so one run finds every problem in a file instead of stopping at
the first. Editors, IDEs and linters need this, and so does any tool that wants to show a user the
whole list of things to fix rather than one mistake per run.

Every error says what was expected and where. If something was left unclosed, it also points back at
where that was opened:

```java
Toml.parse("deps = [\n  \"a\",\n  \"b\"\n").errors();
// Unexpected end of input, expected ], a comma, or a newline;
//   the array opened at line 1, column 8 is unclosed (line 4, column 1)
```

Values know where they came from as well, so your own checks can point at the file the same way:

```java
long port = result.getLong("server.port");
if (port > 65535) {
  System.err.println("config.toml:" + result.inputPositionOf("server.port").line() + ": port out of range");
}
```

## Also worth knowing

* **Complete, and tested against the spec.** TomlJ supports TOML 1.1.0. Every build runs the
  official [toml-test](https://github.com/toml-lang/toml-test) suite for 1.0.0 and 1.1.0: valid
  files must give exactly the expected values, and invalid files must be rejected.
* **Reads TOML 1.0.0 too.** `TomlVersion.V1_0_0` reports 1.1.0 syntax as an error instead of
  accepting it.
* **A typed getter for every TOML type**, returning `String`, `Long`, `Double`, `Boolean`,
  `TomlArray` or `TomlTable`, and the four date and time types as `java.time`'s `OffsetDateTime`,
  `LocalDateTime`, `LocalDate` and `LocalTime`. Each getter returns `null` if the key is missing,
  throws `TomlInvalidTypeException` if the value is a different type, and has an overload that takes
  a default.
* **Comments are kept.** Every comment in a document is parsed into the model, attached to an entry
  or unattached in the table or array it was written in. See [Comments](#comments).
* **Documents can be built and edited.** A parse result is a `MutableTomlTable`: set, insert and
  remove values and comments, or build a document from scratch, and write it out with `toToml()`.
  See [Building and editing documents](#building-and-editing-documents).
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
comments together, in document order:

```java
for (TomlElement element : result.elements()) {
  if (element instanceof TomlKeyValue) {
    TomlKeyValue pair = (TomlKeyValue) element;
    System.out.println(pair.key() + " = " + pair.value().get());
  } else {
    System.out.println("# " + ((TomlComment) element).text());
  }
}
```

A comment's text is what follows `# `, one string per line in `lines()`. The comments on a `[[x]]`
header are attached to the table it opens, so they are read with `getArray("x").comments(0)`.
`toToml()` does not write comments yet.

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

Values may be any of the types the getters return, plus `Integer`, `Short`, `Byte` and `Float`
(widened to `Long` and `Double`), and a `Map` or `Collection` (converted to a table or an array).
`set` creates any intermediate tables that do not exist. Replacing a value keeps the entry's place
in `elements()`, its input position and its comments; replacing an array element keeps its place
and its comments. Removing an entry takes its comments with it and leaves the unattached comments
around it where they are.

Keys and `String` values must not contain an unpaired surrogate, and dates must be ones TOML can
write (a year from 0 to 9999, and no seconds in the offset).

`set` and `add` put a new entry after the last element. To place one elsewhere, name the entry it
goes next to: `insertBefore` and `insertAfter` take an existing key (on an array, an index) and put
the new entry immediately before or after it. The anchor can also be any element of `elements()`,
so an entry can go before or after an unattached comment. Inserting a key that is already set
throws `TomlKeyAlreadySetException`; naming a key or anchor that is not there throws
`NoSuchElementException`.

```java
result.insertAfter("owner.name", "email", "tom@example.com");
result.getArray("database.ports").insertBefore(0, 8000);
```

Comments are edited by placement. A run above an entry is a list of lines and the comment after it
is one line, so each has a setter of its own. An unattached comment is added to its table or array
after the last element, or inserted before or after an entry:

```java
result.setCommentAbove("port", "The port clients connect to.");
result.setCommentAfter("port", "not 80");
result.addComment("Everything below is optional.");
result.insertCommentBefore("server", "Overrides for the server.");
```

A `TomlComment` read from any entry or document can be set on another entry with `setComment`,
which places it by its own placement, or added as an unattached comment with `addComment`.
`removeComment(key, placement)` removes the comment of a placement, and `removeComment(comment)`
removes an unattached comment found in `elements()`. An entry's value and comments can also be
edited through the entry itself: `entry("port")` returns a `MutableTomlKeyValue`.

Entries added through the mutators have no input position, and `isModified` reports which entries,
tables or arrays have changed since the document was parsed. Mutable tables and arrays are not
safe for use from multiple threads without external synchronization.

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
  <version>1.3.0</version>
</dependency>
```

To include using Gradle: `implementation 'org.tomlj:tomlj:1.3.0'`

If your project already uses ANTLR and you would rather share one copy of the runtime, use
`org.tomlj:tomlj-antlr` instead. It is the same library, depending on `org.antlr:antlr4-runtime`
rather than bundling it.

## Links

- [GitHub project](https://github.com/tomlj/tomlj)
- [Online Java documentation](https://tomlj.org/docs/java/latest/org/tomlj/package-summary.html)
- [Issue tracker: Report a defect or feature request](https://github.com/tomlj/tomlj/issues/new)
