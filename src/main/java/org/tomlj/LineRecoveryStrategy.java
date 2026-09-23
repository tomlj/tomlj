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

import org.tomlj.internal.TomlParser;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An error strategy that recovers from unexpected input between expressions by skipping the rest of the line.
 *
 * <p>
 * Where a line cannot start an expression (e.g. {@code @}), or has input left over after one, ANTLR's default strategy
 * only resynchronizes if it can delete a single token, and otherwise abandons the document rule, discarding every later
 * line. In the document rule this strategy instead reports the unexpected token, skips to the end of the line and lets
 * parsing continue on the next line. Errors inside an expression are left to the default strategy, whose recovery
 * already stops at the end of the line, except within an array or inline table, where a value may span lines and the
 * default strategy would otherwise carry the value on over every line that follows.
 */
final class LineRecoveryStrategy extends DefaultErrorStrategy {

  private static final IntervalSet LINE_END = IntervalSet.of(TomlParser.NewLine);

  // The token the leftover of an abandoned value ends at. Until the parser reaches it, a line it cannot parse is that
  // value's content rather than an expression the document got wrong.
  private int leftoverEnd = -1;

  @Override
  public void sync(Parser recognizer) {
    if (recognizer.getContext() instanceof TomlParser.TomlContext) {
      if (recognizer.getInputStream().LT(1).getTokenIndex() < leftoverEnd) {
        // What is left of a value the parser has given up on was written as that value's content, so each line of it
        // is passed over, the mistake having been reported once, where the value was given up on. A line of it that an
        // expression could be written as is passed over as well: it is content the document never held, and parsing it
        // reports what it gets wrong, e.g. a string element as a key with no value after it.
        consumeUntil(recognizer, LINE_END);
        return;
      }
      if (!recognizer.getExpectedTokens().contains(recognizer.getInputStream().LA(1))) {
        // Reports nothing if already recovering, e.g. after an expression whose own recovery stopped before the line
        // end.
        reportUnwantedToken(recognizer);
        consumeUntil(recognizer, LINE_END);
        return;
      }
    }
    ParserRuleContext value = unterminatedValue(recognizer);
    if (value != null) {
      // An array or inline table may hold newlines between its elements, so the default strategy treats a line it
      // cannot parse as input to skip within the value, and carries on matching the lines after it - to the end of the
      // document, since nothing closes the value. Ending the value here leaves the rest of the document to the document
      // rule instead. A line that cannot continue the value is reported and skipped, as the rule's own recovery stops
      // at the end of a line; a line the lexer has already left the value for is kept whole, as the value ends before
      // the newline that starts it and the document rule matches that newline.
      // Only a value the lexer has left is known to be unclosed: a line that cannot continue a value ends it here, but
      // a closing bracket or brace may still follow, as in an array whose element is a stray character.
      Token opened = recognizer.getInputStream().LA(1) == TomlParser.NewLine ? value.getStart() : null;
      if (opened == null) {
        // The lexer is still reading the value, so the lines that follow hold what is left of it.
        leftoverEnd = endOfLeftover(recognizer);
      }
      throw new UnterminatedValueException(recognizer, opened);
    }
    // Also resets the state the default strategy keeps for reporting what a later rule expected.
    super.sync(recognizer);
  }

  /**
   * The array or inline table the parser is inside that ends here, because the line that follows belongs to the
   * document or because this line cannot continue it, or null where the value carries on.
   */
  @Nullable
  private ParserRuleContext unterminatedValue(Parser recognizer) {
    TokenStream input = recognizer.getInputStream();
    boolean beforeNewLine = input.LA(1) == TomlParser.NewLine;
    if (beforeNewLine) {
      // Checked while recovering from an error inside the value as well: the lexer has read the line that follows as
      // the document's, so the value ends here regardless of what went wrong inside it.
      if (!startsDocumentLine(input.LA(2))) {
        return null;
      }
    } else {
      if (inErrorRecoveryMode(recognizer)) {
        return null;
      }
      Token previous = input.LT(-1);
      if (previous == null || previous.getType() != TomlParser.NewLine) {
        return null;
      }
    }
    ParserRuleContext value = null;
    for (RuleContext context = recognizer.getContext(); context != null; context = context.parent) {
      if (context instanceof TomlParser.ArrayContext || context instanceof TomlParser.InlineTableContext) {
        value = (ParserRuleContext) context;
        break;
      }
    }
    if (value == null) {
      return null;
    }
    if (beforeNewLine) {
      // An inline table holds key/value pairs of its own, so only a table header ends one; the lexer leaves it for
      // nothing else.
      if (input.LA(2) == TomlParser.UnquotedKey && value instanceof TomlParser.InlineTableContext) {
        return null;
      }
      return value;
    }
    // Computing what the parser expects walks the rule invocation stack, so it is left until last.
    return recognizer.getExpectedTokens().contains(input.LA(1)) ? null : value;
  }

  /**
   * The token index just past what is left of the value being given up on: the delimiter that closes it, the header of
   * a table, which the lexer leaves any value for, or the end of the input, whichever comes first.
   */
  private static int endOfLeftover(Parser recognizer) {
    TokenStream input = recognizer.getInputStream();
    int depth = 1;
    boolean lineStart = false;
    for (int ahead = 1;; ahead++) {
      int type = input.LA(ahead);
      if (type == Token.EOF) {
        return input.LT(ahead).getTokenIndex();
      }
      if (lineStart && startsDocumentLine(type)) {
        // A line the document can own is the document's: the lexer leaves a value for one, and a key/value pair
        // written there is parsed, so the parser must report what it gets wrong rather than pass over it.
        return input.LT(ahead).getTokenIndex();
      }
      if (type == TomlParser.ArrayStart || type == TomlParser.InlineTableStart) {
        depth++;
      } else if (type == TomlParser.ArrayEnd || type == TomlParser.InlineTableEnd) {
        depth--;
        if (depth == 0) {
          return input.LT(ahead).getTokenIndex() + 1;
        }
      }
      lineStart = type == TomlParser.NewLine;
    }
  }

  /**
   * Check whether a token type can only start a line of the document. Inside a value the lexer produces one only where
   * it has left a value the document never closed, having read the line that follows as the document's own.
   */
  private static boolean startsDocumentLine(int type) {
    return type == TomlParser.TableKeyStart || type == TomlParser.ArrayTableKeyStart || type == TomlParser.UnquotedKey;
  }

  /**
   * Recover where the document rule fails to match a token.
   *
   * <p>
   * The only token the document rule matches after calling another rule is the newline ending an expression's line, so
   * what it fails to match there is input left on the line after the expression and the comment following it: a
   * character no comment may hold, as everything else is caught where the rule decides what the line holds next. As
   * there, the input is reported and the rest of the line skipped, and the newline ending it is matched, so that the
   * document rule carries on with the next line rather than giving up the document.
   */
  @Override
  public Token recoverInline(Parser recognizer) throws RecognitionException {
    if (recognizer.getContext() instanceof TomlParser.TomlContext) {
      reportUnwantedToken(recognizer);
      consumeUntil(recognizer, LINE_END);
      Token lineEnd = recognizer.getCurrentToken();
      if (lineEnd.getType() == TomlParser.NewLine) {
        reportMatch(recognizer);
        recognizer.consume();
        return lineEnd;
      }
    }
    return super.recoverInline(recognizer);
  }

  @Override
  protected Token singleTokenDeletion(Parser recognizer) {
    Token token = recognizer.getInputStream().LT(1);
    // Deleting a newline carries the expression the parser is recovering on into the line below, e.g. letting a table
    // header whose key is missing take the key of the next line, and the key/value pair written there with it. What a
    // line is missing is not on the next line, so a newline is never the stray token deletion is meant for.
    if (token.getType() == TomlParser.NewLine) {
      return null;
    }
    // A run of string characters outside a string (e.g. the rest of a line after a broken string) is a single token,
    // but it is not the single stray token that deletion is meant for: deleting it lets the parser carry on as though
    // the line were whole, e.g. still inside an inline table whose closing brace was in the run, and fail on the lines
    // that follow. Recover as the default strategy does from more than one unexpected token.
    if (token.getType() == TomlParser.StringChars) {
      String text = token.getText();
      if (text.codePointCount(0, text.length()) > 1) {
        return null;
      }
    }
    return super.singleTokenDeletion(recognizer);
  }

  /**
   * A mismatch that ends an array or inline table, carrying the bracket or brace that opened it where nothing closes
   * it. The error is reported where the value is left, which is rarely the line the delimiter is missing from.
   */
  static final class UnterminatedValueException extends InputMismatchException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final transient Token opened;

    UnterminatedValueException(Parser recognizer, @Nullable Token opened) {
      super(recognizer);
      this.opened = opened;
    }

    @Nullable
    Token opened() {
      return opened;
    }
  }
}
