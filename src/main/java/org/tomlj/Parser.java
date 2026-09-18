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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

final class Parser {
  private Parser() {}

  static TomlParseResult parse(CharStream stream, TomlParseOptions options) {
    return parseTable(stream, options, new AccumulatingErrorListener());
  }

  /**
   * Parse a document into the table it describes, paired with the errors found while reading it.
   *
   * <p>
   * {@link #parse(CharStream, TomlParseOptions)} does this and nothing more, with an error listener of its own. Tests
   * use this overload directly to supply their own {@link AccumulatingErrorListener}, and to reach the package-private
   * methods {@link LinkedTomlTable} adds to {@link TomlTable}, which {@link TomlParseResult} does not expose.
   *
   * @param stream The document.
   * @param options The parse options.
   * @param errorListener Where syntax errors and parse errors are reported.
   * @return The table the document describes, which is incomplete if any error was reported.
   */
  static ParsedTomlTable parseTable(
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
    ParsedTomlTable rootTable = new ParsedTomlTable(errorListener);
    LineVisitor visitor =
        new LineVisitor(rootTable, options.version().canonical, errorListener, options.maxNestingDepth());
    tree.accept(visitor);
    return rootTable;
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
