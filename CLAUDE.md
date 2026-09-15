# CLAUDE.md

TomlJ is a Java library (`org.tomlj:tomlj`) that parses TOML 1.1.0 using ANTLR 4.

## Build

Use the Gradle wrapper with JDK 17 or newer (JDK 21 is known to work).

```sh
./gradlew dev                                                        # format, build, test, check licenses, javadoc
./gradlew test --tests 'org.tomlj.TomlTest'                          # one class
./gradlew test --tests 'org.tomlj.TomlTest.shouldHandleParseErrors'  # one method
```

Run `./gradlew dev` before committing. It applies Spotless formatting, and the build fails on unformatted code.

## Constraints

- `src/main` compiles with `--release 9`, so no `var`, records, text blocks or pattern matching there. Tests compile
  with `--release 17`.
- Compilation uses `-Werror` with Error Prone, so any warning fails the build.
- Keep `checker-qual` on the 3.x line: 4.x requires Java 11.
- Do not switch ANTLR generation to `-Xexact-output-dir` with a custom `outputDirectory`: Gradle 9 registers that
  directory as the source root, which breaks the javadoc exclusion of `org.tomlj.internal` and puts generated files at
  the root of the sources jar.
- Commits follow CONTRIBUTING.md: one purpose per commit, with formatting changes kept apart from code changes.
