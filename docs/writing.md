# Writing TOML

`toToml()` writes any table or array as TOML. A parse result is written from the text it was parsed
from, so a document comes back as it went in, changed only where it was edited. Anything else is
written in a default style. `TomlWriteOptions` chooses how much of a parsed document's existing
structure and format is kept, and shapes what is written anew. This document states what is written
from what, where each kind of edit lands, and the cases at the edges.

## What is written from what

A `TomlParseResult` holds the text it was parsed from, and each line, header and comment in it
remembers where it was read. Writing the result walks the model and copies that text, so an unedited
document comes back byte for byte, with its spacing, blank lines, comments and the form of every
value. An edit changes only the lines it touches. This is what the default options ask for,
`TomlWriteOptions.Keep.LAYOUT`.

Only the parse result itself is written this way. `toToml()` on a table or array taken from it, or
on a copy of one, writes it in the default style, and so are a document built through the editing
API and a result parsed with `TomlParseOptions.defaults().withoutSource()`. Keeping the text takes
memory, and an application that only reads a document never uses it, so parse without the source
when the document will not be written back.

A value read from a document keeps the literal it was written with, `0x10` or `'literal'`, wherever
it is written, in the default style included, unless the options ask to keep nothing. An inline
table, and an array read between brackets, keep their form the same way: a table taken from one
document and set in another is written as `t = { a = 1 }` where it was read so, rather than as a
`[t]` section. A value parsed from text with `TomlValue.parse`, or made with one of the notation
factories on `TomlValue` such as `hex(255)`, keeps its text and its form the same way. The key of
a `key = value` line read from a document keeps its quoting the same way.
The keys of a header are written from the document's text only while the document keeps its
notation; the default style quotes a header key only where TOML requires it.

## How much is kept: `Keep`

`TomlWriteOptions.keep(Keep)` sets how much of the existing structure and format of a parsed
document is kept. Each value keeps less than the one before it, and where a value has nothing to
keep, the next value applies:

* **`LAYOUT`**, the default, keeps the layout: every line, header and comment the parser accepted is
  written back as it was read, with its spacing, indentation and blank lines, and only what the
  editing API changed is written anew, as `NOTATION` writes it.
* **`NOTATION`** keeps the notation: the form each key, value and table was written in, whether
  `0x10` or `16`, a bare or quoted key, a basic or literal string, a header, dotted keys or an
  inline table. The order of lines and sections and the comments are kept too, while whitespace,
  indentation, blank lines and the layout of arrays and inline tables come from the options.
  Anything with no notation to keep, such as a document parsed without its source, is written as
  `NOTHING` writes it.
* **`NOTHING`** keeps nothing: the whole document is written in the default style, as a document
  built through the editing API is written.

So a line the editing API wrote anew, inside a document that keeps its layout, is written from its
parts with the key, value and comments the model holds; and a table built through the editing API,
inside a document that keeps its notation, is written in the default style.

## Keeping the layout: where an edit lands

With `LAYOUT`, everything the editing API did not touch is copied. What it did touch is written
anew, in the place the document gives it:

* **A replaced value** keeps its line: the blank lines above it, its key, the spacing around the
  `=`, the comment run above it and the comment after it are written as they were read, and only the
  value is written anew. A value that cannot sit on a line, a table or an array of tables, leaves
  the line and is written as a section, or as sections, after the last line of its parent's subtree,
  with the comment run above the line written above the header and the comment after it written
  after the header. An array of tables whose entry has a comment stays on the line, written as an
  array of inline tables, since `[[x]]` sections have no place for the comment. A string holding a
  newline is written as a multi-line string, and the line keeps its comment.
* **A new entry** goes after the nearest line of its table: after the last line of the section that
  holds the table, or, for `insertBefore` and `insertAfter`, next to the line of the entry it was
  placed by. It is indented like the entries around it, and named by a key relative to the section
  it lands in, so a new entry of a table a dotted key opened is written with a dotted key. A new
  entry of the root goes before the first header.
* **A new table** becomes a section after the last line of its parent's subtree, and a new table of
  the root goes at the end of the document. A new table of an array of tables follows the last
  `[[x]]` section. A table stored under a new key, or one copied in from another document, is
  written as a section the same way, and each of its lines is written from the text it was read as,
  so its values keep their literals.
* **A removed entry** is not written, and neither are the blank lines above it, the comment run
  above it and the comment after it. A removed table's whole section is not written. An unattached
  comment stays where it is. A table a dotted key opened that is left with no entries is written
  under a header of its own, since a dotted key needs a value.
* **A comment set above a line** is indented like the line. A comment set after a value is written
  two spaces from it; one that replaces another keeps the spacing the document wrote before it, and
  when one is removed, that spacing is removed with it. An unattached comment added or inserted is
  written with a blank line below it, and in a table a blank line above it unless it comes after the
  last line of the table, without doubling one the document already has: a run directly under a line
  belongs to the table open there, and one after a blank line to the table of the next expression,
  so the placement keeps the comment in its table on a re-parse. Two unattached comments after the
  last line of a table are written as one run, joined by an empty comment line, since with a blank
  line between them a re-parse would read the second as an unattached comment of the root; and a
  single run left after the last line of a table when the line below it is removed is written
  directly under the line above it, regardless of the blank lines the document had there. The root
  is not affected: a run after its last section belongs to the root whether or not a blank line
  precedes it. An unattached comment added to the root goes after its last section, with a blank
  line above it. A comment set on a table the document wrote as dotted keys gives it a header to
  hold the comment.
* **An array or inline table edited in place** keeps its line and its brackets. Each element that
  was not replaced is written from its own text, with its literal, its comments and the layout
  around it; an element added or replaced is written in the default style, laid out like the
  elements around it: an element added to a container written on one line goes on that line, and one
  added to a container written over lines goes on its own line. Whether the container is on one line
  is read from the text between its elements, so a multi-line string, or an array over lines, as a
  value does not put the container holding it over lines. Where an element is removed, the commas
  are adjusted: the comma between two elements that no longer have one, and the comma after the last
  element, which is dropped unless the document wrote one there. The entries of an inline table keep
  the order they were written in, dotted keys included. An array or inline table left with no
  element of the document is written anew.
* **A comment set in an array or inline table written on one line** lays it out over lines, since a
  comment ends at a line break. TOML 1.0.0 allows no line break inside an inline table, so writing
  one holding a comment for that version throws `IllegalArgumentException`.

New lines use the document's line separator unless one is set with `withLineSeparator`. A document
that ends without a newline is written without one, unless a line is added after its last line: a
line written anew ends with a newline.

## Keeping the notation

With `NOTATION`, the document is walked in the same order and every line, header and comment is
written from its parts: the key and the literal each value was written with, the comments the model
holds, and the indentation, spacing and layout the options give. A line keeps one blank line above
it where the document wrote any, a header always gets one, and the blank lines a document ends with
are dropped. An unattached comment is laid out as the default style lays it out, with a blank line
below it, and in a table one above it unless it comes after the last line of the table, regardless
of the blank lines the document had around it. A comment after a value is separated from it by two
spaces, and the run above a line is indented like the line. An array or inline table is laid out
anew: on one line if it fits within the maximum line width, and otherwise with each element on its
own line. The document's structure is kept: a header stays a header, a dotted key stays dotted and
an inline table stays inline.

## The default style

The default style writes a table in two parts: first its `key = value` lines and its unattached
comments, in their order in the table, then its sub-tables and arrays of tables, in their order. A
An unattached comment of the root that comes after its first sub-table is written among the sections
instead, in its order among them, where a re-parse reads it in the root. A sub-table is written
under a `[a.b]` header naming its path from the root, unless it holds only sub-tables and arrays of
tables, whose headers imply it. Each table of an array of tables is written under a `[[a]]` header,
unless the array or its entry holds a comment: `[[a]]` sections have no place for it, so such an
array is written on a `key = value` line as an array of inline tables. A table or array nested
inside an array or an inline table is written inline, `{ k = v }` or `[1, 2]`. A table made with
`MutableTomlTable.createInline()`, and an array made with `MutableTomlArray.createInline()`, are
written on their entry's line wherever they are, unless the options keep nothing.

An array is written on one line, `[1, 2, 3]`, when that whole line fits within the maximum line
width, and otherwise with each element on its own line, indented two spaces beyond the line the
array starts on. When writing TOML 1.1.0, an inline table is written the same way, with each entry
on its own line. TOML 1.0.0 allows no line break inside an inline table, so for it an inline table
is always written on one line, regardless of its width, and so is everything inside one.

A string holding a newline is written as a multi-line basic string when it is the value of a `key =
value` line. Every other string, whether a key or a value inside an array or inline table, is
written as a single-line basic string.

Comments are written from the model: the run above an entry or header, the comment after it, and the
unattached comments of each table or array, in the container they were written in. A blank line
separates an unattached comment from the element it would otherwise be attached to, so that a
re-parse reads it as unattached. Two unattached comments after the last line of a table are written
as one run, joined by an empty comment line, since with a blank line between them a re-parse would
read the second as an unattached comment of the root. An array or inline table holding a comment is
written over lines for every TOML version, since a comment ends at a line break; for TOML 1.0.0 an
inline table holding a comment cannot be written, and `toToml` throws `IllegalArgumentException`.

## The options

`TomlWriteOptions` shapes everything written anew, and the whole document under `NOTATION` or
`NOTHING`.

* **`withIndent(spaces)`** indents nested tables. A header whose path has `n` keys is indented by
  `(n - 1) * spaces`, and the lines of a section whose header path has `n` keys by `n * spaces`,
  dotted-key lines included. The lines of the root are not indented, and the path of a table in an
  array of tables is the path of the array. The elements of a multi-line array are indented two
  spaces beyond the line the array starts on regardless. The default is no indentation.
* **`withMaxLineWidth(columns)`** is the widest a line may be, counted in code points, for an array
  or an inline table to be written on one line. The width includes the indentation, the key before
  the value and the comma after an element of an enclosing multi-line array. A long key or string is
  never split, nor is an inline table when writing TOML 1.0.0. With a width of `0`, every non-empty
  array and inline table is written over lines, except an inline table, and everything inside one,
  when writing TOML 1.0.0. The default is 80.
* **`withLineSeparator(separator)`** ends each line written anew with `"\n"` or `"\r\n"`, the only
  newlines TOML allows, and is also used for the line breaks inside a multi-line string; a line
  copied from the document keeps its own. Without it, new lines use the separator of the document's
  first line, and a document with no source, or with no line break, uses the platform's.
* **`withVersion(version)`** names the version of TOML the output is written for, 1.1.0 by default.
  The version determines whether an inline table too long for its line may be written over lines,
  which 1.0.0 does not allow. Text copied from the document is written as the document wrote it,
  regardless of the version, and an element added to an inline table the document wrote over lines
  is laid out like the elements around it.

```java
TomlWriteOptions options = TomlWriteOptions.defaults()
    .keep(TomlWriteOptions.Keep.NOTATION)
    .withIndent(2);
String reindented = result.toToml(options);
```

## Reformatting one table or array

`MutableTomlTable` and `MutableTomlArray` have `reformat(TomlWriteOptions.Keep)`, which sets how
much one table or array, and everything nested in it, keeps when the document is written. The
options still apply to the rest of the document, and the table keeps the lesser of the two amounts:

* **`LAYOUT`**, which every table and array starts with, changes nothing.
* **`NOTATION`** writes the table's lines from their parts, in place, keeping their order, comments,
  literal forms and structure, and taking the layout the options give.
* **`NOTHING`** writes the table entirely in the default style, as one block, in the place its
  header had. A table with no header of its own, one the document wrote as dotted keys or one whose
  header the headers of its sub-tables imply, has no such place, so the block goes after the last
  line of its parent's subtree, as a new table does. An array of tables is written as `[[x]]`
  sections where the first of them was; any other array or an inline table is written anew on the
  line it was written on.

```java
result.getTable("server").reformat(TomlWriteOptions.Keep.NOTHING);
```

A later call keeps the lesser of the two amounts, so a call asking for more than was set changes
nothing and a reformat cannot be undone. A copy of a reformatted table or array is reformatted the
same way. A block written for `NOTHING` takes the indentation the options give, not the document's.

## Edge cases

* **A line the parser rejected** recorded nothing, so it is not written, and neither is the comment
  run above it, which is not in the model.
* **Sections a document interleaves**, `[a]`, `[b]`, `[a.c]`, keep their order under `LAYOUT` and
  `NOTATION`. The default style writes `[a.c]` after `[a]`.
* **A value replaced twice** is written as it was left.
* **An array of inline tables** the document wrote on one line keeps its line, although the default
  style would write it as `[[x]]` sections.
* **A table's own `toToml()`** writes it as a document root, in the default style, however the
  document wrote it. Only the parse result is written from its text.
* **An array's own `toToml()`** is always written in the default style, as an array inside a value
  is written: `[1, 2]` on one line when it fits, otherwise over lines, with tables inline, and with
  no newline after it.
* **A document parsed with `withoutSource()`** is written in the default style, keeping its comments
  and the order of its entries, but not the literal forms of its values, since those are read from
  the source text.
