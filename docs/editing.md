# Building and editing documents

`MutableTomlTable` and `MutableTomlArray` extend `TomlTable` and `TomlArray` with mutators. A
document can be built from scratch, and a parse result can be changed, then written out with
`toToml()`. This document states what the mutators accept, where a new entry goes, and how comments
are edited.

## Building a document

`MutableTomlTable.create()` makes an empty table, and `MutableTomlArray.of(...)` an array of the
given values:

```java
MutableTomlTable doc = MutableTomlTable.create();
doc.set("title", "Example");
doc.set("owner.name", "Tom");
doc.getOrCreateTable("database").set("ports", MutableTomlArray.of(8001, 8002));
String toml = doc.toToml();
```

`MutableTomlTable.createInline()` makes a table that is written between braces on its entry's
line, `point = { x = 1, y = 2 }`, rather than under a `[point]` header, and
`MutableTomlArray.createInline()` an array that is written between brackets whatever it holds,
where an array of tables made with `create()` is written as `[[x]]` headers. Both are written in the
default style when the options keep nothing.

## Editing a parse result

A parse result is itself a `MutableTomlTable`, so a document can be parsed, changed and written
back:

```java
TomlParseResult result = Toml.parse(source);
result.set("owner.name", "Chris");
result.remove("title");
Files.writeString(source, result.toToml());
```

Entries added through the mutators have no input position, and `isModified` reports which entries,
tables or arrays have changed since the document was parsed. `errors()` reports only what was found
while parsing, and is unaffected by any change made afterwards.

## Values

Values may be any of the types the getters return, plus `Integer`, `Short`, `Byte` and `Float`
(widened to `Long` and `Double`), and a `Map` or `Collection` (converted to a table or an array).
A `TomlValue` read from an entry, `result.entry("port").value()`, stores what it holds, so a value
can be taken from one entry and set on another. `set` creates any intermediate tables that do not
exist.

A value can also be parsed from its TOML text with `TomlValue.parse`, which takes what is written
after the `=` of a `key = value` line: a scalar, an inline table or an array. The value keeps the
notation it was written in, so `toToml()` writes it back as it was parsed, `0xFF` rather than `255`
and `{ a = 1 }` on the entry's line rather than under a `[t]` header, unless the options keep
nothing. Text that is not exactly one value, or that has a comment or a line break before or after
the value, throws `IllegalArgumentException`; a comment inside an inline table or an array is part
of the value and is kept.

```java
doc.set("mask", TomlValue.parse("0xFF"));
doc.set("path", TomlValue.parse("'C:\\Users\\tom'"));
doc.set("point", TomlValue.parse("{ x = 1, y = 2 }"));
```

For the notations that are easy to get wrong by hand, `TomlValue` has factories that take the
value itself: `hex(255)` for `0xFF` and `hexLowercase(255)` for `0xff`, `octal(493)` for `0o755`,
`binary(10)` for `0b1010`, `grouped(1000000)` for `1_000_000`, `literal("C:\\Users")` for
`'C:\Users'` and `multilineLiteral(text)` for a `'''` string. Each throws `IllegalArgumentException`
for a value its notation cannot write, such as a negative integer with a base prefix or an
apostrophe in a literal string.

```java
doc.set("mode", TomlValue.octal(493));
doc.set("pattern", TomlValue.literal("\\d+"));
```

[writing.md](writing.md) describes what is written from what.

Keys and `String` values must not contain an unpaired surrogate, and dates must be ones TOML can
write (a year from 0 to 9999, and no seconds in the offset). A value that cannot be written as TOML
throws `IllegalArgumentException`.

Replacing a value keeps the entry's place in `elements()`, its input position and its comments;
replacing an array element keeps its place and its comments. Removing an entry also removes its
attached comments; the unattached comments around it stay in `elements()`.

## Placing an entry

`set` and `add` put a new entry after the last element. To place one elsewhere, name the entry it
goes next to: `insertBefore` and `insertAfter` take an existing key (on an array, an index) and put
the new entry immediately before or after it. The anchor can also be any element of `elements()`,
so an entry can go before or after an unattached comment.

```java
result.insertAfter("owner.name", "email", "tom@example.com");
result.getArray("database.ports").insertBefore(0, 8000);
```

Inserting a key that is already set throws `TomlKeyAlreadySetException`; naming a key or anchor
that is not there throws `NoSuchElementException`.

## Editing comments

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
removes an unattached comment found in `elements()`.

The placements themselves, and which table an unattached comment belongs to, are described in
[comments.md](comments.md).

## Editing through an entry

An entry's value and comments can also be edited through the entry itself: `entry("port")` returns
a `MutableTomlKeyValue`, with the same setters for its own value and comments.

## Threads

Mutable tables and arrays are not safe for use from multiple threads without external
synchronization.
