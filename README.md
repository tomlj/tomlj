# TomlJ: A Java parser for Tom's Obvious, Minimal Language (TOML)

TomlJ is a complete [TOML](https://github.com/toml-lang/toml) parser with the
following attributes:

* Supports the latest TOML specification version (1.1.0).
* Provides detailed error reporting, including error position.
* Performs error recovery, allowing parsing to continue after an error.

It uses the [ANTLR](https://github.com/antlr/antlr4/) parser-generator and
runtime library.

## Usage

Parsing is straightforward:

```java
Path source = Paths.get("/path/to/file.toml");
TomlParseResult result = Toml.parse(source);
result.errors().forEach(error -> System.err.println(error.toString()));

String value = result.getString("a. dotted . key");
```

Parsing never throws for invalid input. Every error is recorded in `result.errors()`, with its line and column,
and parsing continues with the next expression so that the rest of the document is still available. Check
`result.hasErrors()` before relying on the result. A key/value pair that contains a syntax error, such as
`key = 4uoxyz`, is reported as an error and omitted from the result.

Methods that take a `String` key parse it as a dotted key using TOML syntax, so keys containing characters
outside `A-Z`, `a-z`, `0-9`, `_` and `-` must be quoted, exactly as in a TOML document. Methods that take a
`List<String>` treat each element as a literal key with no quoting needed, which makes them the right choice
when iterating over `keySet()` or `entrySet()`:

```java
String quoted = result.getString("\"@key#with$special%characters\"");
String literal = result.getString(Collections.singletonList("@key#with$special%characters"));
```

## Getting TomlJ

TomlJ is published to a Maven Central.

To include using Maven:
```xml
<dependency>
  <groupId>org.tomlj</groupId>
  <artifactId>tomlj</artifactId>
  <version>1.1.1</version>
</dependency>
```

To include using Gradle: `implementation 'org.tomlj:tomlj:1.1.1'`

## Links

- [GitHub project](https://github.com/tomlj/tomlj)
- [Online Java documentation](https://tomlj.org/docs/java/latest/org/tomlj/package-summary.html)
- [Issue tracker: Report a defect or feature request](https://github.com/tomlj/tomlj/issues/new)
- [StackOverflow: Ask "how-to" and "why-didn't-it-work" questions](https://stackoverflow.com/questions/ask?tags=tomlj)
