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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntegerStack;

/**
 * The superclass of the generated {@link TomlLexer}, holding the code called by the actions in {@code TomlLexer.g4}.
 *
 * <p>
 * Kept out of the grammar so that it is formatted and checked like the rest of the sources, which generated code is
 * not.
 */
public abstract class AbstractTomlLexer extends Lexer {

  // State is made public to allow incremental lexers to save and restore it (e.g. NetBeans)
  public final IntegerStack arrayDepthStack = new IntegerStack();
  public int arrayDepth = 0;

  // Whether a stray character has been read in the value being lexed, which no line that follows can complete.
  private boolean strayInValue = false;

  // Whether the end of the input ends a line, see nextToken().
  private boolean endOfInputEndsLine = true;
  // Whether anything has been read on the current line, other than whitespace.
  private boolean lineOpen = false;

  AbstractTomlLexer(CharStream input) {
    super(input);
  }

  /**
   * Read the end of the input as the end of a line, or not.
   *
   * <p>
   * On, which is the default, a document whose last line has no newline is read as though it had one, so that the
   * parser sees every line ended the same way and no rule needs to say that a line may end at the end of the input
   * instead. Off when parsing a key on its own, which is not a line.
   *
   * @param endsLine Whether the end of the input ends a line.
   */
  public void setEndOfInputEndsLine(boolean endsLine) {
    this.endOfInputEndsLine = endsLine;
  }

  @Override
  public Token nextToken() {
    Token token = super.nextToken();
    if (token.getType() == Token.EOF) {
      if (!lineOpen || !endOfInputEndsLine) {
        return token;
      }
      // A newline that is not in the input, given the position of the end of the input and no text.
      lineOpen = false;
      return _factory
          .create(
              _tokenFactorySourcePair,
              TomlLexer.NewLine,
              "",
              Token.DEFAULT_CHANNEL,
              token.getStartIndex(),
              token.getStartIndex() - 1,
              token.getLine(),
              token.getCharPositionInLine());
    }
    if (token.getChannel() == Token.DEFAULT_CHANNEL) {
      lineOpen = token.getType() != TomlLexer.NewLine;
    }
    return token;
  }

  boolean inArray() {
    return arrayDepth > 0;
  }

  void pushValueModeIfInArray() {
    if (inArray()) {
      pushMode(TomlLexer.ValueMode);
    }
  }

  void resetValueState() {
    arrayDepthStack.clear();
    arrayDepth = 0;
    strayInValue = false;
  }

  void pushArrayDepth() {
    arrayDepthStack.push(arrayDepth);
    arrayDepth = 0;
  }

  void popArrayDepth() {
    arrayDepth = arrayDepthStack.pop();
  }

  // An array or an inline table may hold newlines between its elements, so a newline inside one does not end it. A
  // value the document never closes would take every line that follows as its content, and the document would lose
  // them all: the lines after an unclosed value are read as the document's again from the first one that no value can
  // hold.

  void valueNewLine() {
    if (!inArray()) {
      // A newline outside an array ends the key/value pair, so the value is missing and the mode must be left.
      popMode();
      return;
    }
    // No line of an array holds a key/value pair, so a line that does belongs to the document.
    if (nextLineStartsTable() || nextLineStartsKeyval()) {
      endValue();
    }
  }

  void inlineTableNewLine() {
    // An inline table holds key/value pairs of its own, so a table header is all that tells the document's lines from
    // its content, unless a stray character has already made it a table no line can complete.
    if (nextLineStartsTable() || (strayInValue && nextLineStartsKeyval())) {
      endValue();
    }
  }

  /**
   * Leave the string being read and, where a value is left around it, end that value if the line that follows belongs
   * to the document.
   *
   * <p>
   * A string its line does not close ends at the end of that line, so the newline that ends it is the value's too, and
   * without this check only the newline after the next line would be.
   */
  void stringNewLine() {
    popMode();
    if (_mode == TomlLexer.ValueMode) {
      valueNewLine();
    } else if (_mode == TomlLexer.InlineTableMode) {
      inlineTableNewLine();
    }
  }

  // A stray character does not leave an array or an inline table: what follows it was written as the value's content,
  // and reading that as the document's turns an element like `[2],` into the header of a table named 2, which then
  // holds the pairs of the lines below. Outside an array there is no value left to read, as a key/value pair ends with
  // its line.

  void valueError() {
    strayInValue = true;
    if (!inArray()) {
      popMode();
    }
  }

  void inlineTableError() {
    strayInValue = true;
  }

  /**
   * Leave the value being read, regardless of what it is nested in, and read what follows as the document again.
   */
  private void endValue() {
    _modeStack.clear();
    mode(TomlLexer.DEFAULT_MODE);
    resetValueState();
  }

  /**
   * Check whether the line that follows holds a table header and nothing else, e.g. {@code [server]}.
   */
  private boolean nextLineStartsTable() {
    int i = skipBlanks(1);
    if (_input.LA(i) != '[') {
      return false;
    }
    i++;
    boolean arrayTable = _input.LA(i) == '[';
    if (arrayTable) {
      i++;
    }
    i = scanKey(i);
    if (i < 0 || _input.LA(i) != ']') {
      return false;
    }
    i++;
    if (arrayTable) {
      if (_input.LA(i) != ']') {
        return false;
      }
      i++;
    }
    return endsLine(i);
  }

  /**
   * Check whether the line that follows starts a key/value pair, e.g. {@code port = 8000}.
   */
  private boolean nextLineStartsKeyval() {
    int i = scanKey(1);
    return i >= 0 && _input.LA(i) == '=';
  }

  /**
   * Scan the key at {@code start}, returning the index after it, or -1 where there is none or where every part of it
   * could also be a value.
   *
   * <p>
   * A key that could be a value leaves the line ambiguous, and it is left to the value being read: {@code [2]} is an
   * array holding 2 as much as it is the header of a table named {@code 2}, and {@code 8=000} inside an array is a
   * broken element rather than a key/value pair. A quoted key is a string, so it is ambiguous in the same way; not
   * reading one also keeps this check to the few characters that decide it.
   */
  private int scanKey(int start) {
    boolean couldBeValue = true;
    int i = start;
    while (true) {
      i = skipBlanks(i);
      int from = i;
      while (isKeyChar(_input.LA(i))) {
        i++;
      }
      if (i == from) {
        return -1;
      }
      if (!isValueWord(from, i)) {
        couldBeValue = false;
      }
      i = skipBlanks(i);
      if (_input.LA(i) != '.') {
        return couldBeValue ? -1 : i;
      }
      i++;
    }
  }

  /**
   * Check whether the characters from {@code from} to {@code to} could start a value rather than name a table.
   */
  private boolean isValueWord(int from, int to) {
    int c = _input.LA(_input.LA(from) == '-' ? from + 1 : from);
    if (c >= '0' && c <= '9') {
      // A number, a date or a time.
      return true;
    }
    return matches(from, to, "true")
        || matches(from, to, "false")
        || matches(from, to, "inf")
        || matches(from, to, "nan")
        || matches(from, to, "-inf")
        || matches(from, to, "-nan");
  }

  private boolean matches(int from, int to, String word) {
    if (to - from != word.length()) {
      return false;
    }
    for (int i = 0; i < word.length(); i++) {
      if (_input.LA(from + i) != word.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private int skipBlanks(int start) {
    int i = start;
    while (_input.LA(i) == ' ' || _input.LA(i) == '\t') {
      i++;
    }
    return i;
  }

  /**
   * Check whether only whitespace and a comment are left before the end of the line.
   */
  private boolean endsLine(int start) {
    int i = skipBlanks(start);
    if (_input.LA(i) == '#') {
      while (_input.LA(i) != IntStream.EOF && _input.LA(i) != '\n' && _input.LA(i) != '\r') {
        i++;
      }
    }
    int c = _input.LA(i);
    return c == IntStream.EOF || c == '\n' || (c == '\r' && _input.LA(i + 1) == '\n');
  }

  private static boolean isKeyChar(int c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
  }

  // A run of digits starts a date or a time when a dash or a colon follows it, and is a decimal integer otherwise.
  // An action reads that following character. A semantic predicate can read it too, but ANTLR then caches no DFA edge
  // for input that reaches the predicate, leaving every digit of every number to ATN simulation: with one here, an
  // array of 500,000 integers lexed about 60 times slower.
  void decimalIntegerOrDateStart() {
    if ("-:".indexOf(_input.LA(1)) < 0) {
      pushValueModeIfInArray();
      popMode();
    } else if (isDigits(getText())) {
      pushValueModeIfInArray();
      setType(TomlLexer.DateDigits);
      mode(TomlLexer.DateMode);
    } else {
      // A dash or a colon follows, but a run holding a sign or an underscore starts no date or time, and a dash or a
      // colon ends no integer.
      setType(TomlLexer.Error);
      popMode();
    }
  }

  private static boolean isDigits(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }
}
