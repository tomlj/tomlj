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

import org.tomlj.internal.TomlLexer;
import org.tomlj.internal.TomlParser;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.checkerframework.checker.nullness.qual.Nullable;

final class Parser {
  private Parser() {}

  static TomlParseResult parse(CharStream stream, TomlParseOptions options) {
    AccumulatingErrorListener errorListener = new AccumulatingErrorListener();
    MutableTomlTable table = parseTable(stream, options, errorListener);

    return new TomlParseResult() {
      @Override
      public int size() {
        return table.size();
      }

      @Override
      public boolean isEmpty() {
        return table.isEmpty();
      }

      @Override
      public Set<String> keySet() {
        return table.keySet();
      }

      @Override
      public Set<List<String>> keyPathSet(boolean includeTables) {
        return table.keyPathSet(includeTables);
      }

      @Override
      public Set<Map.Entry<String, Object>> entrySet() {
        return table.entrySet();
      }

      @Override
      public Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
        return table.entryPathSet(includeTables);
      }

      @Override
      @Nullable
      public Object get(List<String> path) {
        return table.get(path);
      }

      @Override
      @Nullable
      public TomlPosition inputPositionOf(List<String> path) {
        return table.inputPositionOf(path);
      }

      @Override
      public List<TomlComment> comments(List<String> path) {
        return table.comments(path);
      }

      @Override
      public List<TomlComment> comments() {
        return table.comments();
      }

      @Override
      public Map<String, Object> toMap() {
        return table.toMap();
      }

      @Override
      public List<TomlParseError> errors() {
        return errorListener.errors();
      }
    };
  }

  /**
   * Parse a document into the table it describes.
   *
   * <p>
   * This is the whole of parsing; {@link #parse(CharStream, TomlParseOptions)} only pairs the table with the errors
   * reported while reading it. Tests use it to reach what the table holds but {@link TomlParseResult} does not expose.
   *
   * @param stream The document.
   * @param options The parse options.
   * @param errorListener Where syntax errors and parse errors are reported.
   * @return The table the document describes, which is incomplete if any error was reported.
   */
  static MutableTomlTable parseTable(
      CharStream stream,
      TomlParseOptions options,
      AccumulatingErrorListener errorListener) {
    TomlLexer lexer = new TomlLexer(stream);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    TomlParser parser = new TomlParser(tokens);
    parser.setErrorHandler(new LineRecoveryStrategy());
    parser.removeErrorListeners();
    parser.addErrorListener(errorListener);
    parser.setMaxNestingDepth(options.maxNestingDepth());
    ParseTree tree = parser.toml();
    LineVisitor visitor = new LineVisitor(options.version().canonical, errorListener, options.maxNestingDepth());
    return tree.accept(visitor);
  }

  static List<String> parseDottedKey(String dottedKey) {
    TomlLexer lexer = new TomlLexer(CharStreams.fromString(dottedKey));
    lexer.mode(TomlLexer.TomlKeyMode);
    lexer.setEndOfInputEndsLine(false);
    TomlParser parser = new TomlParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    AccumulatingErrorListener errorListener = new AccumulatingErrorListener();
    parser.addErrorListener(errorListener);
    TomlParser.TomlKeyContext tree = parser.tomlKey();
    List<TomlParseError> errors = errorListener.errors();
    if (!errors.isEmpty()) {
      TomlParseError e = errors.get(0);
      throw new IllegalArgumentException(
          "Invalid key: "
              + e.getMessage()
              + ". Keys containing characters other than A-Z, a-z, 0-9, '_' and '-' must be quoted (e.g. \"@key\")"
              + ", or use the List<String> key path overloads.",
          e);
    }
    try {
      return tree.accept(new KeyVisitor(TomlVersion.HEAD));
    } catch (TomlParseError e) {
      // An invalid escape sequence in a quoted key, which the hint about quoting would not help with
      throw new IllegalArgumentException("Invalid key: " + e.getMessage(), e);
    }
  }
}
