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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

final class AccumulatingErrorListener extends BaseErrorListener implements ErrorReporter {

  // The tokens a key and a value can start with, which are named as "a key" and "a value" where all of them are
  // expected. Anywhere else the tokens that are expected are named one by one, as they are the whole of what fits.
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

    if (e instanceof InputMismatchException || e instanceof NoViableAltException) {
      String message = getMessage(e.getOffendingToken(), getExpected(e));
      reportError(message, position);
      return;
    }

    if (offendingSymbol instanceof Token && recognizer instanceof Parser) {
      String message = getMessage((Token) offendingSymbol, getExpected(((Parser) recognizer).getExpectedTokens()));
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

  private String getMessage(Token token, String expected) {
    return "Unexpected " + getTokenName(token) + ", expected " + expected;
  }

  private static String getTokenName(Token token) {
    int tokenType = token.getType();
    switch (tokenType) {
      case TomlLexer.NewLine:
        return "end of line";
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

  private static String getExpected(RecognitionException e) {
    IntervalSet expectedTokens = e.getExpectedTokens();
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
    IntervalSet remaining = expectedTokens;
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
