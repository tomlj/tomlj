/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomlj.TomlTestSuiteTest.suiteDir;

import org.tomlj.internal.TomlLexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks the comments recorded for every valid document of the official TOML test suite.
 *
 * <p>
 * The oracle is the lexer: every {@code Comment} token it produced must be in the model exactly once, with the text and
 * position it was written with, and the model must hold nothing else. The lexer is the right oracle because it is what
 * decides where a comment begins and ends - a {@code #} inside a string is part of the string - and because there is no
 * writer to round-trip through until the serializer learns to write comments.
 *
 * <p>
 * Each comment is also checked against the entry or container that holds it, which is what makes completeness more than
 * a count: a comment recorded against the wrong entry is still recorded exactly once.
 */
class TomlCommentSuiteTest {

  @TestFactory
  Stream<DynamicTest> commentsOfValidDocuments() throws IOException {
    Path suiteDir = suiteDir();
    return Files
        .readAllLines(suiteDir.resolve("files-toml-1.1.0"), UTF_8)
        .stream()
        .filter(file -> file.startsWith("valid/") && file.endsWith(".toml"))
        .map(file -> DynamicTest.dynamicTest(file, () -> checkComments(suiteDir.resolve(file))));
  }

  private static void checkComments(Path file) throws IOException {
    String document = new String(Files.readAllBytes(file), UTF_8);
    MutableTomlTable table = Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());

    Recorder recorder = new Recorder();
    recorder.walk(table, "");
    assertTrue(recorder.failures.isEmpty(), () -> String.join("\n", recorder.failures));
    assertEquals(lexedText(document), recorder.text, "the comments of the document, by line");
    Map<Integer, Integer> columns = lexedColumns(document);
    recorder.columns
        .forEach(
            (line, column) -> assertEquals(columns.get(line), column, "the column of the comment on line " + line));
  }

  /**
   * The text of each comment of a document as the lexer sees it, by line.
   *
   * <p>
   * A comment runs to the end of its line, so a line holds at most one and its line identifies it. The text is the form
   * the API uses, which is what was written after {@code "# "}.
   */
  private static Map<Integer, String> lexedText(String document) {
    Map<Integer, String> text = new TreeMap<>();
    for (Token token : lex(document)) {
      String written = token.getText().substring(1);
      text.put(token.getLine(), written.startsWith(" ") ? written.substring(1) : written);
    }
    return text;
  }

  /**
   * The column each comment of a document begins at, by line.
   *
   * <p>
   * Only the first line of a run is checked, as that is the only one a run records a column for.
   */
  private static Map<Integer, Integer> lexedColumns(String document) {
    Map<Integer, Integer> columns = new TreeMap<>();
    for (Token token : lex(document)) {
      columns.put(token.getLine(), token.getCharPositionInLine() + 1);
    }
    return columns;
  }

  private static List<Token> lex(String document) {
    CommonTokenStream stream = new CommonTokenStream(new TomlLexer(CharStreams.fromString(document)));
    stream.fill();
    List<Token> comments = new ArrayList<>();
    for (Token token : stream.getTokens()) {
      if (token.getType() == TomlLexer.Comment) {
        comments.add(token);
      }
    }
    return comments;
  }

  /**
   * Walks a parsed document, collecting what it holds against each line and checking each comment against its owner.
   */
  private static final class Recorder {
    final Map<Integer, String> text = new TreeMap<>();
    final Map<Integer, Integer> columns = new TreeMap<>();
    final List<String> failures = new ArrayList<>();

    void walk(MutableTomlTable table, String path) {
      unattached(table.comments(), path.isEmpty() ? "the root table" : path);
      for (String key : table.keySet()) {
        List<String> keyPath = Collections.singletonList(key);
        String entry = path + key;
        TomlPosition position = table.inputPositionOf(keyPath);
        attached(table.comments(keyPath), entry, position);
        descend(table.get(keyPath), entry);
      }
    }

    void walk(MutableTomlArray array, String path) {
      unattached(array.comments(), path);
      for (int i = 0; i < array.size(); ++i) {
        String entry = path + "[" + i + "]";
        attached(array.comments(i), entry, array.inputPositionOf(i));
        descend(array.get(i), entry);
      }
    }

    private void descend(Object value, String entry) {
      if (value instanceof MutableTomlTable table) {
        walk(table, entry + ".");
      } else if (value instanceof MutableTomlArray array) {
        walk(array, entry);
      }
    }

    private void attached(List<TomlComment> comments, String entry, TomlPosition position) {
      if (comments.size() > 2) {
        failures.add(entry + " has " + comments.size() + " comments attached to it");
      }
      CommentPlacement previous = null;
      for (TomlComment comment : comments) {
        CommentPlacement placement = comment.placement();
        TomlPosition at = comment.position();
        if (placement == null) {
          failures.add(entry + " has an unattached comment among the comments attached to it");
        } else if (placement == previous) {
          failures.add(entry + " has two " + placement + " comments");
        } else if (placement == CommentPlacement.ABOVE) {
          if (previous != null) {
            failures.add(entry + " has the comment above it after the one that trails it");
          }
          int lastLine = at.line() + comment.lines().size() - 1;
          if (lastLine != position.line() - 1) {
            failures.add(entry + " has a comment above it ending on line " + lastLine + ", not on the line above it");
          }
        } else {
          if (comment.lines().size() != 1) {
            failures.add(entry + " has a comment of " + comment.lines().size() + " lines trailing it");
          }
          if (at.line() < position.line()) {
            failures.add(entry + " has a comment trailing it on line " + at.line() + ", above where it begins");
          }
        }
        previous = placement;
        record(comment, entry);
      }
    }

    private void unattached(List<TomlComment> comments, String container) {
      int previousLine = 0;
      for (TomlComment comment : comments) {
        if (comment.placement() != null) {
          failures.add(container + " holds a comment placed " + comment.placement() + " among its unattached ones");
        }
        TomlPosition at = comment.position();
        if (at.line() <= previousLine) {
          failures.add(container + " holds unattached comments out of document order at line " + at.line());
        }
        previousLine = at.line() + comment.lines().size() - 1;
        record(comment, container);
      }
    }

    private void record(TomlComment comment, String owner) {
      TomlPosition at = comment.position();
      List<String> lines = comment.lines();
      for (int i = 0; i < lines.size(); ++i) {
        // The lines of a run are contiguous, so each sits at the run's line plus its offset within the run.
        int line = at.line() + i;
        if (text.put(line, lines.get(i)) != null) {
          failures.add("the comment on line " + line + " was recorded twice, the second time on " + owner);
        }
      }
      columns.put(at.line(), at.column());
    }
  }
}
