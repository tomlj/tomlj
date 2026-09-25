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

import org.tomlj.internal.AbstractTomlParser;
import org.tomlj.internal.TomlLexer;
import org.tomlj.internal.TomlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AccumulatingErrorListener extends BaseErrorListener implements ErrorReporter {

  // The tokens a key and a value can start with, which are named as "a key" and "a value" where all of them are
  // expected. Anywhere else the tokens that are expected are named one by one, as they are the whole of what fits.
  private static final IntervalSet COMMENT = new IntervalSet(TomlLexer.Comment);
  private static final IntervalSet END_OF_INPUT = new IntervalSet(TomlLexer.EOF);
  private static final IntervalSet KEY_START =
      new IntervalSet(TomlLexer.UnquotedKey, TomlLexer.Apostrophe, TomlLexer.QuotationMark);
  private static final IntervalSet VALUE_START = new IntervalSet(
      TomlLexer.Apostrophe,
      TomlLexer.QuotationMark,
      TomlLexer.TripleApostrophe,
      TomlLexer.TripleQuotationMark,
      TomlLexer.DecimalInteger,
      TomlLexer.BinaryInteger,
      TomlLexer.OctalInteger,
      TomlLexer.HexInteger,
      TomlLexer.FloatingPoint,
      TomlLexer.FloatingPointInf,
      TomlLexer.FloatingPointNaN,
      TomlLexer.TrueBoolean,
      TomlLexer.FalseBoolean,
      TomlLexer.DateDigits,
      TomlLexer.ArrayStart,
      TomlLexer.InlineTableStart);

  private final List<TomlParseError> errors = new ArrayList<>();
  private final List<Integer> syntaxErrorLines = new ArrayList<>();
  private int lastOffendingTokenIndex = -1;

  @Override
  public void syntaxError(
      Recognizer<?, ?> recognizer,
      Object offendingSymbol,
      int line,
      int charPosition,
      String msg,
      RecognitionException e) {

    // Recovery can leave the parser on a token that a rule has already rejected (e.g. an unterminated array followed
    // by a line that cannot start an expression), and a second report of that token adds nothing.
    if (offendingSymbol instanceof Token) {
      int tokenIndex = ((Token) offendingSymbol).getTokenIndex();
      if (tokenIndex >= 0 && tokenIndex == lastOffendingTokenIndex) {
        return;
      }
      lastOffendingTokenIndex = tokenIndex;
    }

    TomlPosition position = TomlPosition.positionAt(line, charPosition + 1);
    syntaxErrorLines.add(line);

    if (e instanceof AbstractTomlParser.NestingTooDeepException) {
      reportError(e.getMessage(), position);
      return;
    }

    Token opened = openingOfUnclosedValue(e, offendingSymbol, recognizer);

    if (e instanceof InputMismatchException || e instanceof NoViableAltException) {
      String message = getMessage(
          e.getOffendingToken(),
          getExpected(withoutLineEnd(e.getExpectedTokens(), opened), e.getCtx()),
          opened);
      reportError(message, position);
      return;
    }

    if (offendingSymbol instanceof Token && recognizer instanceof Parser) {
      Parser parser = (Parser) recognizer;
      String message = getMessage(
          (Token) offendingSymbol,
          getExpected(withoutLineEnd(parser.getExpectedTokens(), opened), parser.getContext()),
          opened);
      reportError(message, position);
      return;
    }

    reportError(msg, position);
  }

  @Override
  public void reportError(TomlParseError error) {
    errors.add(error);
  }

  @Override
  public boolean hasSyntaxErrorBetween(int firstLine, int lastLine) {
    for (int errorLine : syntaxErrorLines) {
      if (errorLine >= firstLine && errorLine <= lastLine) {
        return true;
      }
    }
    return false;
  }

  private void reportError(String message, TomlPosition position) {
    reportError(new TomlParseError(message, position));
  }

  List<TomlParseError> errors() {
    return errors;
  }

  private String getMessage(Token token, String expected, @Nullable Token opened) {
    String message = "Unexpected " + getTokenName(token) + ", expected " + expected;
    if (opened == null || opened.getLine() >= token.getLine()) {
      // A value opened on the line being reported on is already in front of the reader.
      return message;
    }
    String kind = opened.getType() == TomlLexer.InlineTableStart ? "inline table" : "array";
    TomlPosition position = TomlPosition.positionAt(opened.getLine(), opened.getCharPositionInLine() + 1);
    return message + "; the " + kind + " opened at " + position + " is unclosed";
  }

  /**
   * The bracket or brace that opened an array or inline table that nothing closes, where this error is the end of that
   * value, and null everywhere else. What such a document is missing is the closing delimiter, and the line it is
   * missing from is rarely the line the error is reported on.
   */
  @Nullable
  private static Token openingOfUnclosedValue(
      @Nullable RecognitionException e,
      @Nullable Object offendingSymbol,
      Recognizer<?, ?> recognizer) {
    if (e instanceof LineRecoveryStrategy.UnterminatedValueException) {
      Token opened = ((LineRecoveryStrategy.UnterminatedValueException) e).opened();
      if (opened != null) {
        return opened;
      }
    }
    // At the end of the input, a value the parser is still inside can never be closed.
    if (!(offendingSymbol instanceof Token)
        || ((Token) offendingSymbol).getType() != TomlLexer.EOF
        || !(recognizer instanceof Parser)) {
      return null;
    }
    for (RuleContext context = ((Parser) recognizer).getContext(); context != null; context = context.parent) {
      if (context instanceof TomlParser.ArrayContext || context instanceof TomlParser.InlineTableContext) {
        return ((ParserRuleContext) context).getStart();
      }
    }
    return null;
  }

  /**
   * The expected tokens, without the newline where the error ends an unclosed value. An array or inline table may hold
   * a newline, but once the lexer has left the value or the input has ended, no newline can close it.
   */
  private static IntervalSet withoutLineEnd(IntervalSet expectedTokens, @Nullable Token opened) {
    return (opened == null) ? expectedTokens : expectedTokens.subtract(IntervalSet.of(TomlLexer.NewLine));
  }

  private static String getTokenName(Token token) {
    int tokenType = token.getType();
    switch (tokenType) {
      case TomlLexer.NewLine:
        // The lexer ends the last line of the input with a newline of its own, which has no text, where the document
        // has none.
        return token.getText().isEmpty() ? "end of input" : "end of line";
      case TomlLexer.EOF:
        return "end of input";
      default:
        String text = token.getText();
        if (tokenType == TomlLexer.StringChars) {
          // A run of string characters is a single token. Name only its first character, where the input went wrong,
          // so that a long run does not end up in the message.
          text = text.substring(0, text.offsetByCodePoints(0, 1));
        }
        if (isOnlyQuotes(text)) {
          return text;
        }
        return "'" + Toml.tomlEscape(text) + '\'';
    }
  }

  private static String getExpected(IntervalSet expectedTokens, @Nullable RuleContext context) {
    // The lexer ends the last line of the input with a newline where the document has none, so where a line of the
    // document may end, the input may end as well, though the parser never sees it there.
    if (expectedTokens.contains(TomlLexer.NewLine) && context instanceof TomlParser.TomlContext) {
      return getExpected(expectedTokens.or(END_OF_INPUT));
    }
    return getExpected(expectedTokens);
  }

  /**
   * Check whether every token of {@code subset} is in {@code set}.
   */
  private static boolean contains(IntervalSet set, IntervalSet subset) {
    return subset.subtract(set).isNil();
  }

  private static String getExpected(IntervalSet expectedTokens) {
    // Where every token that could start a key or a value is expected, the word says what the list of them says, and
    // the reader has one thing to look for rather than nine. A value is checked first, as a string starts either.
    // A comment may be written wherever a newline may, so it is left out of every list.
    IntervalSet remaining = expectedTokens.subtract(COMMENT);
    List<TokenName> names = new ArrayList<>();
    if (contains(remaining, VALUE_START)) {
      names.add(TokenName.VALUE);
      remaining = remaining.subtract(VALUE_START);
    }
    if (contains(remaining, KEY_START)) {
      names.add(TokenName.KEY);
      remaining = remaining.subtract(KEY_START);
    }
    remaining
        .getIntervals()
        .stream()
        .flatMap(i -> IntStream.rangeClosed(i.a, i.b).boxed())
        .flatMap(TokenName::namesForToken)
        .forEach(names::add);

    List<String> sortedNames =
        names.stream().sorted().distinct().map(TokenName::displayName).collect(Collectors.toList());

    StringBuilder builder = new StringBuilder();
    int count = sortedNames.size();
    for (int i = 0; i < count; ++i) {
      builder.append(sortedNames.get(i));
      if (i < (count - 2)) {
        builder.append(", ");
      } else if (i == (count - 2)) {
        if (count >= 3) {
          builder.append(',');
        }
        builder.append(" or ");
      }
    }

    return builder.toString();
  }

  private static boolean isOnlyQuotes(String text) {
    int length = text.length();
    if (length == 0) {
      return false;
    }
    char first = text.charAt(0);
    if (first != '\'' && first != '\"') {
      return false;
    }
    for (int i = 1; i < length; ++i) {
      if (text.charAt(i) != first) {
        return false;
      }
    }
    return true;
  }
}
