# TomlJ examples

Each directory here is a small program that uses TomlJ, with the TOML files it reads. They are
numbered in the order to learn them: reading a file and handling its errors, then building, editing
and writing documents, then walking a document of unknown structure and reading its comments, then
binding documents to records and classes and writing them back.

| Example | Shows |
|---|---|
| [01-reading-a-document](01-reading-a-document) | Parsing a file, checking for errors, and reading values with the typed getters |
| [02-reporting-errors](02-reporting-errors) | Listing every error in a file with its position, and pointing your own checks at a value's position |
| [03-building-a-document](03-building-a-document) | Building a document in code, with tables, arrays of tables, inline tables, comments and chosen notations such as `0o640` |
| [04-editing-in-place](04-editing-in-place) | Editing a hand-written file and writing it back with its formatting and comments kept |
| [05-choosing-the-output-format](05-choosing-the-output-format) | Writing a document as it was read, with a new layout, in the default style, or for TOML 1.0.0 |
| [06-walking-the-model](06-walking-the-model) | Visiting every table, array and value of a document whose structure is not known in advance, and converting it to JSON |
| [07-reading-comments](07-reading-comments) | Reading the comments attached to entries and the unattached comments of a table |
| [08-binding-a-document](08-binding-a-document) | Binding a whole document to a tree of records |
| [09-binding-to-records](09-binding-to-records) | Binding to enums, maps, values that may be missing and a table left unbound, and binding single tables and arrays |
| [10-binding-with-options](10-binding-with-options) | Binding to a class whose fields hold the defaults, with kebab-case keys, a renamed key, converters and ignored keys |
| [11-reporting-binding-errors](11-reporting-binding-errors) | Listing every value that could not be bound, with its path and position |
| [12-writing-bound-records](12-writing-bound-records) | Writing changed records back into a document, keeping its comments and notation, and writing records as a new document |

## Running the examples

The examples are a Gradle build of their own, which builds TomlJ from this repository and uses it in
place of the published jar. From the root of the repository, run one example:

```sh
./gradlew -p examples :04-editing-in-place:run
```

or build and run all of them:

```sh
./gradlew -p examples check
```

Each example reads its files from its own directory. `04-editing-in-place` writes its result to
`build/manifest.toml` in its directory, leaving `manifest.toml` as it was.

The examples use Java 17. TomlJ itself needs Java 8 or later.

## Using TomlJ in your own project

The examples depend on `org.tomlj:tomlj`, as your project would:

```groovy
implementation 'org.tomlj:tomlj:2.2.0'
```

## What the examples show

### 01-reading-a-document

Parses `config.toml` and reads it with `getString`, `getLong`, `getDouble`, `getTable`, `getArray`
and `getLocalDate`. A `String` key is a dotted key, so `"server.port"` reads the key `port` in the
table `server`. Each getter returns `null` for a missing key, and its overload with a supplier
returns a default instead.

### 02-reporting-errors

Parses `broken.toml`, which has four errors, and prints each with its line and column. TomlJ never
throws on invalid input: it records each error and parses on, so one run finds every problem, and
whatever parsed without error can still be read. It then parses `limits.toml`, which is valid TOML
with values out of range, and uses `inputPositionOf` to report those at their lines too.

### 03-building-a-document

Builds a document with `MutableTomlTable` and `MutableTomlArray`, sets comments on its entries, and
writes it with `toToml()`. `TomlValue.octal`, `grouped`, `hex` and `literal` choose the notation a
value is written in, and `MutableTomlTable.createInline()` makes a table that is written between
braces.

### 04-editing-in-place

Parses `manifest.toml`, changes a version, adds to two arrays, inserts a dependency, sets a comment
and removes a table, then writes the result and prints the changes. Only the lines that were edited
change:

```diff
  [package]
  name    = "inventory"
- version = "1.4.2"
- authors = ["Ana <ana@example.com>"]
+ version = "1.5.0"
+ authors = ["Ana <ana@example.com>", "Ben <ben@example.com>"]
  edition = "2021"   # change with care

  [dependencies]
  serde   = { version = "1.0", features = ["derive"] }
- tokio   = "1.38"
+ tokio   = "1.38"  # pinned until 2.0
  anyhow  = "1"      # error handling
+ clap = { version = "4", features = ["derive"] }
  log     = '0.4'
```

### 05-choosing-the-output-format

Writes `deploy.toml` with each `TomlWriteOptions.Keep` value: `LAYOUT`, the default, gives back the
file exactly as it was read; `NOTATION` keeps the form of every key and value and lays the document
out anew with the indentation and line width given; `NOTHING` writes it in the default style. It
also reformats one table with `reformat`, writes the document for TOML 1.0.0, and parses it without
its source text to save memory.

### 06-walking-the-model

Prints the tree of `catalog.toml` with the TOML type of each value, using `entrySet()` on tables
and `get(index)` on arrays, then writes the document as JSON with `toJson()`.

### 07-reading-comments

Reads the comments of `agent.toml`. A run of comment lines directly above an entry, and a comment on
its line, are attached to the entry and read with `comment(key, ABOVE)` and `comment(key, AFTER)`.
Every other comment is unattached, and is listed among the table's entries by `elements()`. The
example ends by printing a reference of the settings, described by their comments.

### 08-binding-a-document

Binds `config.toml`, the file `01-reading-a-document` reads with getters, to a tree of records with
`Toml.parseAs(file, Config.class)`, which parses the file and binds it in one call. Each key is
bound to the record component of the same name, and each value to the component's type: `[server]`
to a nested record, `opened` to a `LocalDate`, and each `[[warehouse]]` to an element of a `List`. A
file with parse errors makes `parseAs` throw a `TomlParseException`, and one whose values do not fit
the records a `TomlBindException`; the example prints the errors of either, each with its position.

### 09-binding-to-records

Binds `inventory.toml` to records with more kinds of values: `[labels]` to a `Map<String, String>`,
and `level = "info"` to the enum constant `INFO`. The package is annotated with JSpecify's
`@NullMarked`, so a component may be missing only if it is an `Optional`, which is then empty, or
is annotated `@Nullable`, which is then `null`. `[plugins]` is left unbound: its component is
declared `TomlTable`, so it holds the table itself, and each plugin reads its own settings from it
with getters. The example ends by binding single tables and arrays of the document, with a
`GenericType` for the generic type `Map<String, String>`.

### 10-binding-with-options

Binds `server.toml` to a class, whose field initializers are the defaults for the keys the file
leaves out. `KeyNaming.KEBAB_CASE` binds the field `listenAddress` to the key `listen-address`,
`@TomlName` gives one field a key of its own, and converters bind `Duration` and `Path`, which TomlJ
does not bind itself. The file has a `[metrics]` table for another program: it is reported as an
unknown key, until `withUnknownKeysIgnored(true)` is added to the options.

### 11-reporting-binding-errors

Binds `pipeline.toml`, which is valid TOML with six values that do not fit the records they are
bound to: a string for a time, an integer too large for an `int`, a misspelled key, a missing key,
a string that names no enum constant, and an array for a string. Each error prints with its path,
such as `step[1].when`, and its position. It then binds `fixed.toml`, where those are corrected,
and the check in a record's constructor reports the one error left:

```
pipeline.toml could not be bound:
  start: expected a local time, found a string (line 2, column 9)
  retries: 3000000000 is out of range for int (line 3, column 11)
  timout: unknown key (line 4, column 1)
  step[1].command: missing (line 10, column 1)
  step[1].when: "on-sucess" is not one of ALWAYS, ON_SUCCESS, ON_FAILURE (line 12, column 8)
  step[2].command: expected a string, found an array (line 16, column 11)

fixed.toml could not be bound:
  step[2]: timeout must be positive, but is -5 (line 14, column 1)
```

### 12-writing-bound-records

Binds `deployment.toml` to records, changes them, and calls `update` to write the change back into
the document. Only the values that differ are changed: `replicas` becomes 5 with its comment kept,
the web service gets a new image, and a third service is added at the end of the array of tables.
Every comment stays, and each unchanged value keeps the way it is written, such as `memory = 0x200`,
and `timeout = "30s"`, which the converter reads back as the same `Duration`. The converter is
registered with a second function that writes a `Duration`, which is needed to write one at all.
The example ends by writing new records as a new document with `Toml.toToml`.

[docs/editing.md](../docs/editing.md), [docs/writing.md](../docs/writing.md),
[docs/comments.md](../docs/comments.md) and [docs/binding.md](../docs/binding.md) describe editing,
writing, comments and binding in full.
