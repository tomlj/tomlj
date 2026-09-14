# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TomlJ is a Java library (`org.tomlj:tomlj`) that parses TOML 1.1.0 using ANTLR 4. It reports errors with
positions and performs error recovery, so a parse always returns a result plus a list of errors. Main sources
compile with `--release 9`, so no `var`, records, text blocks, or pattern matching in `src/main`. Tests compile
with `--release 17` because JUnit 6 requires it, so test code may use those features.

## Commands

All builds go through the Gradle wrapper. A JDK 17 or newer must be on the path or in `JAVA_HOME` (Gradle 9,
Error Prone, and JUnit 6 all need it); JDK 21 is known to work.

```sh
./gradlew                     # default tasks: build, checkLicense, javadoc
./gradlew dev                 # alias: spotlessApply + build + checkLicense + javadoc
./gradlew test                # run all tests (JUnit 6)
./gradlew checkLicense        # verify dependency licenses against gradle/allowed-licenses.json
./gradlew test --tests 'org.tomlj.TomlTest'                          # one class
./gradlew test --tests 'org.tomlj.TomlTest.shouldHandleParseErrors'  # one method
./gradlew spotlessApply       # format Java + Gradle files (spotlessCheck runs as part of build)
./gradlew generateGrammarSource  # regenerate ANTLR lexer/parser only
./gradlew shadowJar           # fat jar with ANTLR runtime relocated to org.tomlj.internal.antlr
./gradlew jacocoRootTestReport   # coverage report
```

Compilation uses `-Werror` plus Error Prone, so any compiler or Error Prone warning fails the build. Generated
ANTLR sources are excluded from those checks. Individual Error Prone checks are switched off in `build.gradle`
where they conflict with the code style. `GRADLE_MAX_TEST_FORKS` caps parallel test forks (default: half the
CPUs).

Dependency versions are centralised in `dependency-versions.gradle` (via the Spring dependency-management
plugin, with the JUnit BOM imported); `build.gradle` declares dependencies without versions. Keep `checker-qual`
on the 3.x line: 4.x requires Java 11 and the library targets Java 9.

The release version lives in `build.gradle` (`versionNumber`); non-release builds get a `-dev` suffix. The README
also hardcodes the published version in its dependency snippets.

## Formatting and style

Spotless enforces: Eclipse formatter config in `gradle/eclipse-java-style.xml`, the Apache license header from
`gradle/spotless.license.java` on every Java file, import order `org.tomlj`, then `java`, then everything else,
and unused-import removal. Run `./gradlew spotlessApply` before committing; the build fails on formatting drift.
`// @formatter:off` / `on` is used around large `Stream.of(Arguments.of(...))` tables in tests.

Nullness is annotated with Checker Framework qualifiers (`@Nullable`, and `@DefaultQualifier(NonNull)` on public
classes). Keep those on new public API.

CONTRIBUTING.md asks that commits serve a single purpose and that formatting-only changes are not mixed with
code changes.

## Architecture

### Grammar and generated code

- `src/main/antlr/org/tomlj/internal/TomlLexer.g4` and `TomlParser.g4` are the source of truth. The Gradle
  `antlr` plugin generates `org.tomlj.internal.TomlLexer`, `TomlParser`, and `TomlParserBaseVisitor` into
  `build/generated-src/antlr/main/org/tomlj/internal` (with `-visitor`). Everything under `org.tomlj.internal`
  is excluded from javadoc and is not public API. The package subdirectory comes from the grammar files'
  location under `src/main/antlr`; because the parser grammar imports the lexer's `.tokens` file, `build.gradle`
  passes ANTLR `-lib` pointing at that generated package directory. Do not switch to `-Xexact-output-dir` with a
  custom `outputDirectory`: Gradle 9 registers that directory as the source root, which breaks the javadoc
  exclusion and puts generated files at the root of the sources jar.
- `src/main/gen/` is gitignored IDE output. Never edit or rely on it.
- The lexer is heavily mode-based (`KeyMode`, `TomlKeyMode`, `ValueMode`, `BasicStringMode`,
  `MLBasicStringMode`, `LiteralStringMode`, `MLLiteralStringMode`, `DateMode`, `InlineTableMode`). `=` pushes
  `ValueMode`; value tokens pop it. Arrays and inline tables are handled with an explicit `arrayDepth` counter and
  a stack in `@members`, so that nested values inside `[...]` re-enter `ValueMode`. Most tokenisation bugs are in
  this mode/depth bookkeeping rather than in the parser grammar.
- `Parser.parseDottedKey` reuses the same lexer starting in `TomlKeyMode` to parse dotted keys passed to the
  string-keyed API (`get("a.b.c")`).

### Parse pipeline

`Toml` (public static entry points for String/Path/InputStream/Reader/Channel, all delegating to
`Parser.parse(CharStream, TomlVersion)`) →
`Parser` (wires lexer, parser, and `AccumulatingErrorListener`; wraps the resulting table in an anonymous
`TomlParseResult`) →
`LineVisitor` (visits top-level expressions: key/value pairs, `[table]`, `[[array.table]]`; tracks the
`currentTable` and the set of tables implicitly opened by dotted keys, which get `define`d when the next table
header is seen) →
`KeyVisitor` and `ValueVisitor` (per-value dispatch; `ValueVisitor` delegates to `QuotedStringVisitor`,
`ArrayVisitor`, `InlineTableVisitor`, `LocalDateVisitor`, `LocalTimeVisitor`, `ZoneOffsetVisitor`) →
`MutableTomlTable` / `MutableTomlArray` / `MutableHomogeneousTomlArray` (the internal mutable model).

`MutableTomlTable` owns the TOML table semantics: `createTable` and `createTableArray` enforce "previously
defined at ..." rules, implicit vs. defined tables, and the distinction between literal arrays and arrays of
tables. Each element stores its `TomlPosition`, which is what `inputPositionOf` and error messages use.

### Error handling

Semantic errors are thrown as `TomlParseError` (carries a `TomlPosition`) from visitors and the mutable model.
`LineVisitor` catches them per expression and hands them to the `ErrorReporter`, then continues, which is how
error recovery works. Syntax errors come from ANTLR into `AccumulatingErrorListener`, which rewrites the raw
token expectations into human-readable text via `TokenName` (e.g. `Unexpected end of input, expected . or =`).
Tests assert exact message text and line/column, so changing a message or a grammar rule usually requires
updating `errorCaseSupplier` in `TomlTest`.

### Public API shape

`TomlTable` and `TomlArray` are interfaces whose typed accessors (`getString`, `getLong`, `getTable`,
`isBoolean`, dotted-key overloads, `toJson`, `toToml`, ...) are almost all `default` methods built on a small
abstract core (`get(List<String>)`, `keyPathSet`, `entryPathSet`, `inputPositionOf`, ...). Implementations are
package-private (`MutableTomlTable`, `EmptyTomlTable`, etc.). `TomlParseResult` extends `TomlTable` and adds
`errors()`. `JsonSerializer` and `TomlSerializer` back the `toJson` / `toToml` defaults; `JsonOptions` controls
JSON output. `TomlType` maps Java value classes to TOML types. Adding a typed accessor means adding it to the
interface as a `default` method, not to the implementations.

### Spec versions

`TomlVersion` (`V0_4_0`, `V0_5_0`, `V1_0_0`, `V1_1_0`, `LATEST` = alias of `V1_1_0`, `HEAD`) is threaded through
every visitor. Behaviour that differs between spec versions is gated with `version.after(...)` (dotted keys,
heterogeneous arrays, tabs in strings, and the 1.1.0 additions: `\e` and `\xHH` escapes, optional seconds, and
newlines and trailing commas in inline tables). Add new version-dependent behaviour the same way rather than
branching on equality.

## Tests

`src/test/java/org/tomlj/TomlTest.java` holds nearly all coverage. Most cases are `@ParameterizedTest` with a
`@MethodSource` supplier returning `Stream<Arguments>` of `(input, expected)`; add new cases to the relevant
supplier rather than writing a new test method. Larger fixtures live in `src/test/resources/org/tomlj/` as
`.toml` files, some paired with a `.json` file that is compared against `result.toJson()` (line endings are
normalised with `System.lineSeparator()`).

`TomlTestSuiteTest` runs the official [toml-test](https://github.com/toml-lang/toml-test) suite. The
`extractTomlTest` task (a dependency of `test`) fetches the tagged source archive, versioned in
`dependency-versions.gradle` and resolved through an Ivy repository over GitHub, and extracts its `tests/` directory
to `build/toml-test`. The test reads the `files-toml-1.0.0` list (parsed at `V1_0_0`) and the `files-toml-1.1.0`
list (parsed at both `V1_1_0` and `HEAD`), compares valid cases against their tagged JSON with the same rules as the
official runner, and asserts that invalid cases report errors. Each test factory takes a set of cases the parser is
known to fail, which are asserted to still fail so that an entry must be removed once the behaviour is fixed. The
sets are currently empty; when bumping the suite version, expect to add entries for any new cases that fail.
