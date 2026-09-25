# TomlJ examples

Each directory here is a small program that uses TomlJ, with the TOML files it reads. They are
numbered in the order to learn them: reading a file and handling its errors, then building, editing
and writing documents, then walking a document of unknown structure and reading its comments.

| Example | Shows |
|---|---|
| [01-reading-a-document](01-reading-a-document) | Parsing a file, checking for errors, and reading values with the typed getters |
| [02-reporting-errors](02-reporting-errors) | Listing every error in a file with its position, and pointing your own checks at a value's position |
| [03-building-a-document](03-building-a-document) | Building a document in code, with tables, arrays of tables, inline tables, comments and chosen notations such as `0o640` |
| [04-editing-in-place](04-editing-in-place) | Editing a hand-written file and writing it back with its formatting and comments kept |
| [05-choosing-the-output-format](05-choosing-the-output-format) | Writing a document as it was read, with a new layout, in the default style, or for TOML 1.0.0 |
| [06-walking-the-model](06-walking-the-model) | Visiting every table, array and value of a document whose structure is not known in advance, and converting it to JSON |
| [07-reading-comments](07-reading-comments) | Reading the comments attached to entries and the unattached comments of a table |

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

The examples use Java 17. TomlJ itself needs Java 9 or later.

## Using TomlJ in your own project

The examples depend on `org.tomlj:tomlj`, as your project would:

```groovy
implementation 'org.tomlj:tomlj:2.0.1'
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
its source text, for an application that only reads it.

### 06-walking-the-model

Prints the tree of `catalog.toml` with the TOML type of each value, using `entrySet()` on tables
and `get(index)` on arrays, then writes the document as JSON with `toJson()`.

### 07-reading-comments

Reads the comments of `agent.toml`. A run of comment lines directly above an entry, and a comment on
its line, are attached to the entry and read with `comment(key, ABOVE)` and `comment(key, AFTER)`.
Every other comment is unattached, and is listed among the table's entries by `elements()`. The
example ends by printing a reference of the settings, described by their comments.

[docs/editing.md](../docs/editing.md), [docs/writing.md](../docs/writing.md) and
[docs/comments.md](../docs/comments.md) describe editing, writing and comments in full.
