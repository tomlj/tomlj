# Comments

TomlJ keeps every comment in a document and reads them back through the model. A comment has no
meaning in TOML, so where one belongs is a matter of convention. This document states the
conventions TomlJ uses, and the cases at their edges.

## What is a comment

In TOML, a comment starts with `#` and continues to the end of its line. It can share that line
with an expression: a `key = value` line, a table header (a `[[x]]` header counts as one), an array
element or an inline table entry. Or it can have the line to itself. A `#` inside a string is part
of the string, not a comment.

TomlJ treats several comment lines in a row as one comment, called a "run". A run ends at a blank
line, meaning an empty line or one holding only spaces and tabs, or at an expression. A comment
line indented differently from the one above it is still part of the same run.

```toml
# The port clients connect to.
# Change it in the environment, not here.
port = 8080 # not 80

# Everything below is optional.
```

The first two lines are one run. `not 80` is a comment on the port's own line. The last line is a
run of one.

## How TomlJ represents comments

Every comment is a `TomlComment`. It holds the text of its lines without the `#`: `lines()` has one
string per line, `text()` joins them with a newline, and `position()` is the position of the first
line. The lines of a run are contiguous, so line *i* of a run is at `position().line() + i`.

The text is what follows `# `. One leading space is dropped, and only one: `#foo` and `# foo` both
read as `foo`, `#  wide` reads as ` wide`, and a bare `#` reads as an empty string.

Where a comment sits is its placement, which `placement()` returns as a value of the enum
`TomlComment.Placement`: `ABOVE` an expression, `AFTER` one on the same line, or `UNATTACHED` to
any.

## Comments above an expression: `ABOVE`

A run directly above an expression, with no blank line between, is attached to that expression as
its `ABOVE` comment. The model holds the expression as an entry, a key and its value in a table or
an element of an array, and the comment is read through that entry: `comment(key, placement)` on a
table, and `comment(index, placement)` on an array, return the comment attached to an entry at a
placement, or `null` if there is none there. `comments(key)` and `comments(index)` list all the
comments attached to an entry. An unknown key returns `null`, or an empty list.

Only the run directly above counts. Two runs above an expression must have a blank line between
them, and the further one is unattached:

```toml
# far

# near
a = 1
```

`near` is attached to `a`, with a `placement()` of `TomlComment.Placement.ABOVE`. `far` is
unattached:

```java
TomlComment near = result.comment("a", TomlComment.Placement.ABOVE);
System.out.println(near.placement() + ": " + near.text());
// ABOVE: near
```

The expression can be of any kind. A run above a header is attached to the table the header opens,
and is read from the table that holds it; a run above a `[[x]]` header is attached to that element
of the array `x`. Inside an array or an inline table written over lines, a run above an element or
an entry is attached to it. With `TomlComment.Placement.ABOVE` imported statically:

```toml
hosts = [
  # primary
  "a.example",
  # backup
  "b.example",
]

# The server section.
[server]
# The port clients connect to.
port = 8080

# The first mirror.
[[mirror]]
url = "https://mirror.example"
```

```java
result.getArray("hosts").comment(0, ABOVE).text();       // primary
result.comment("server", ABOVE).text();                  // The server section.
result.getTable("server").comment("port", ABOVE).text(); // The port clients connect to.
result.comment("server.port", ABOVE).text();             // the same, through the dotted key
result.getArray("mirror").comment(0, ABOVE).text();      // The first mirror.
```

A run above a dotted key, `a.b = 1`, is likewise attached to the entry the key names, `b` in the
table `a`, so it is read with `comment("a.b", ABOVE)`.

## Comments after an expression: `AFTER`

A comment on the same line as an expression is attached to it as its `AFTER` comment, and is read
through the entry in the same way. It is always one line, where an `ABOVE` comment is a run of any
length.

An entry has at most one comment of each placement, and `comments(key)` lists the `ABOVE` comment
first, then the `AFTER` comment, either of which may be absent:

```toml
# above a
a = 1 # after a
```

`after a` is attached to `a`, with a `placement()` of `TomlComment.Placement.AFTER`, and
`comments("a")` holds `above a` and then `after a`:

```java
TomlComment after = result.comment("a", TomlComment.Placement.AFTER);
System.out.println(after.placement() + ": " + after.text());
// AFTER: after a

for (TomlComment comment : result.comments("a")) {
  System.out.println(comment.placement() + ": " + comment.text());
}
// ABOVE: above a
// AFTER: after a
```

The expression can be of any kind, and the `AFTER` comment is the comment on the line it ends on:
after a multi-line value, the comment on the closing line. In an array it may sit before or after
the element's comma. On a header's line it is attached to the table the header opens, and on an
array's closing bracket, to the array's own entry. With `TomlComment.Placement.AFTER` imported
statically:

```toml
a = 1 # after a
s = """
x
""" # after s
l = [
  1, # after the first element
  2  # after the second
] # after l

[server] # after server
```

```java
result.comment("a", AFTER).text();             // after a
result.comment("s", AFTER).text();             // after s
result.getArray("l").comment(0, AFTER).text(); // after the first element
result.comment("l", AFTER).text();             // after l
result.comment("server", AFTER).text();        // after server
```

A comment on the line of an opening bracket, `l = [ # note`, follows no element. It is an unattached
comment of the array.

## Unattached comments

Every other comment is unattached: a run with a blank line, or nothing, below it. Its `placement()`
is `TomlComment.Placement.UNATTACHED`. It is attached to no entry, so no `comment(key, placement)`
returns it, and asking for that placement is an error. Instead it belongs to a container, which is a
table (the root, a table with a header, or an inline table) or an array, and appears in its
`elements()` between the two elements it was written between. `elements()` on a table or an array
lists its entries and its unattached comments together, in document order. An unattached comment is
a `TomlComment` element; an entry is a `TomlKeyValue` in a table and a `TomlEntry` in an array.

```toml
a = 1
# below a

# floating

# above b
b = 2 # after b
```

`elements()` on the root lists the entry `a`, the comments `below a` and `floating`, and the entry
`b`. `a` has no comments: a comment directly below an entry is not attached to it. The alternative,
a `BELOW` placement, would mean that removing the last entry of a section also removes the comment
at the end of that section. So an unattached comment can be directly adjacent only to the line above
it; if it were directly above an expression, it would be that expression's `ABOVE` comment.

```java
for (TomlElement element : result.elements()) {
  if (element instanceof TomlKeyValue) {
    TomlKeyValue pair = (TomlKeyValue) element;
    System.out.println(pair.key() + " = " + pair.value().get());
  } else {
    TomlComment comment = (TomlComment) element;
    System.out.println(comment.placement() + ": " + comment.text());
  }
}
// a = 1
// UNATTACHED: below a
// UNATTACHED: floating
// b = 2
```

Inside an array or an inline table, an unattached comment belongs to that array or inline table. In
the body of a document, the question is which table it belongs to.

## Which table an unattached comment belongs to

In the body of a document, the lines from a header down to the next header are that table's
section, and the lines before the first header are the root's. An unattached comment inside a
section belongs to the section's table. The question only arises where two sections meet, which is
the point where one section ends and the next header begins. Adjacency decides it:

> An unattached comment directly under the line above it belongs to the innermost table open at
> that point: the section it is in, or, if the line above is a header, the table that header opens.
> Otherwise it belongs to the table of the next expression, placed before that expression; or to
> the root, at the end of the document, if there is none.

| Document | Owner of `note` |
| --- | --- |
| `[a]` / `x = 1` / blank / `# note` / blank / `[b]` | the root, before `b`. Not removed with `a`. |
| `[a]` / `x = 1` / `# note` / blank / `[b]` | table `a`, after `x`. Removed with `a`. |
| `[a]` / `# note` / blank / `x = 1` | table `a`, before `x`. The header opens `a`. |
| `[a]` / blank / `# note` / blank / `x = 1` | table `a`, before `x`. The next expression is `a`'s. |
| `[a]` / `x = 1` / blank / `# note` | the root, at the end. Not removed with `a`. |
| `a.b = 1` / `# note` | the root. A dotted key does not open a section. |

The first two rows show why the blank line matters: a comment separated by a blank line from the
section above it and followed by a header, such as a divider or a footer, is not removed with the
table of that section, while a comment directly under a section's last line belongs to that
section's table.

## Indentation plays no part

Only blank lines and line breaks decide where a comment belongs. Indentation has no meaning in TOML,
and TomlJ ignores it, even where a document indents its sections:

```toml
[a]
j = 1

    [b]
    #foo
    k = 2
#bar
```

`bar` belongs to `b`, although it is dedented, because it is directly under `k = 2` and `b` is the
table open there. A line holding only spaces or tabs is a blank line, so it separates comments like
an empty one.

The same holds where the indentation suggests two comments belong together:

```toml
[a]
j = 1

    [b]
    k = 2
    #foo

    #bar

[c]
l = 3
```

`foo` is an unattached comment of `b`, directly under `k`. `bar` is separated from it by a blank
line, and the next expression is the header `[c]`, which is an expression of the root, so `bar` is
an unattached comment of the root, before `c`. It does not belong to `b`, and not to `a` either,
although it is indented under `b`. Removing `b` does not remove it, and neither does removing `c`.

## Edge cases, gathered

* **A run above a line the parser rejects** is dropped along with that line: the document is
  reported with an error, and the comment is not in the model.
* **A comment at the end of the document without a final newline** is kept like any other.
* **A comment inside a string** is part of the string. `#` starts a comment only outside a value.
* **A comment inside an inline table** is TOML 1.1.0 syntax, since it needs a line break inside the
  braces. Parsed as TOML 1.0.0, the document is reported with an error.
* **A comment on a `[[x]]` header** belongs to that element of the array, not to the array or to
  the table holding it: `comments("x")` is empty.
* **A comment on an array's opening bracket line** is an unattached comment of the array; one on
  its closing bracket line is the `AFTER` comment of the array's entry.
* **A comment separated from the last line of a table by a blank line, then followed by a header,**
  belongs to the root even if the author indented it under the table.
