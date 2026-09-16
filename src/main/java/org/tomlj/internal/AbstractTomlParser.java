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
package org.tomlj.internal;

import org.tomlj.TomlParseOptions;

import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The superclass of the generated {@link TomlParser}, holding the code called by the actions in {@code TomlParser.g4}.
 *
 * <p>
 * Kept out of the grammar so that it is formatted and checked like the rest of the sources, which generated code is
 * not.
 */
public abstract class AbstractTomlParser extends Parser {

  // The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
  // in a document. Nesting is limited so that the stack depth needed by the recursive descent parser, the visitors
  // that build the model and the serializers stays bounded, whatever the input. Defaults to
  // TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH and is changed with setMaxNestingDepth(int).
  private int maxNestingDepth = TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH;

  AbstractTomlParser(TokenStream input) {
    super(input);
  }

  /**
   * Set the maximum number of tables and arrays, not counting the root table, that may enclose any value, table or
   * array in the document.
   *
   * @param maxNestingDepth The maximum nesting depth.
   */
  public void setMaxNestingDepth(int maxNestingDepth) {
    this.maxNestingDepth = maxNestingDepth;
  }

  /** Thrown by the parser when a value is nested deeper than the parser's maximum nesting depth. */
  public static final class NestingTooDeepException extends InputMismatchException {
    private final int maxNestingDepth;

    NestingTooDeepException(AbstractTomlParser parser) {
      super(parser);
      this.maxNestingDepth = parser.maxNestingDepth;
    }

    @Override
    public String getMessage() {
      return nestingTooDeepMessage(maxNestingDepth);
    }
  }

  public static String nestingTooDeepMessage(int maxNestingDepth) {
    return "Nesting is too deep (more than "
        + maxNestingDepth
        + " level"
        + (maxNestingDepth == 1 ? "" : "s")
        + " of tables and arrays)";
  }

  // Called before each value. Counts the tables and arrays between the value and the table holding the current
  // expression (each key of a dotted key adds a table and each array adds a level, while the final key of the
  // expression names the value itself), records the deepest count seen on the expression's key/value pair so that
  // LineVisitor can add the depth of the current table, and rejects the value once the count exceeds the limit. The
  // rejected value is skipped whole so that parsing resumes after it with a single error reported.
  void checkNestingDepth() {
    int depth = -1;
    TomlParser.KeyvalContext outermost = null;
    for (RuleContext ctx = _ctx; ctx != null; ctx = ctx.parent) {
      if (ctx instanceof TomlParser.ArrayContext) {
        depth++;
      } else if (ctx instanceof TomlParser.KeyvalContext) {
        outermost = (TomlParser.KeyvalContext) ctx;
        TomlParser.KeyContext key = outermost.key();
        if (key != null) {
          depth += simpleKeyCount(key);
        }
      }
    }
    if (outermost != null && depth > outermost.nesting) {
      outermost.nesting = depth;
    }
    if (depth > maxNestingDepth) {
      NestingTooDeepException e = new NestingTooDeepException(this);
      skipValue();
      throw e;
    }
  }

  // The number of keys in a dotted key. Counted over the children rather than read from key.simpleKey().size(), as the
  // generated accessor copies every matching child into a new list, and only its size is wanted here. checkNestingDepth
  // runs once per value, so that list would otherwise be built once per value in the document.
  private static int simpleKeyCount(TomlParser.KeyContext key) {
    int count = 0;
    for (int i = 0; i < key.getChildCount(); i++) {
      if (key.getChild(i) instanceof TomlParser.SimpleKeyContext) {
        count++;
      }
    }
    return count;
  }

  // Consumes the tokens of the value about to be parsed: a single token, or an array or inline table together with
  // everything nested inside it.
  private void skipValue() {
    int open = 0;
    do {
      int type = _input.LA(1);
      if (type == Token.EOF) {
        return;
      }
      if (type == TomlParser.ArrayStart || type == TomlParser.InlineTableStart) {
        open++;
      } else if (type == TomlParser.ArrayEnd || type == TomlParser.InlineTableEnd) {
        open--;
      }
      _input.consume();
    } while (open > 0);
  }
}
