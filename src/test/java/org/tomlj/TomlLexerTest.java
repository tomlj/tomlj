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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.tomlj.internal.TomlLexer;

import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

class TomlLexerTest {

  @Test
  void lineEndingBackslashIsNotANewLine() {
    // The backslash ending line 1 continues the string, so only the newlines ending lines 2 and 3 are NewLine tokens.
    TomlLexer lexer = new TomlLexer(CharStreams.fromString("a = \"\"\"x\\\n  y\"\"\"\nb = 1\n"));
    List<Integer> newLineLines = lexer
        .getAllTokens()
        .stream()
        .filter(token -> token.getType() == TomlLexer.NewLine)
        .map(Token::getLine)
        .collect(Collectors.toList());
    assertEquals(List.of(2, 3), newLineLines);
  }

  @Test
  void runsOfStringCharactersAreSingleTokens() {
    // Escapes, newlines, and quotes inside multi-line strings are tokens of their own.
    assertEquals(List.of("abc ", "\\t", " def"), stringTokens("a = \"abc \\t def\""));
    assertEquals(List.of("a", "\"", "\"", "b", "\n", "c d"), stringTokens("a = \"\"\"a\"\"b\nc d\"\"\""));
    assertEquals(List.of("abc \\t def"), stringTokens("a = 'abc \\t def'"));
    assertEquals(List.of("a", "'", "'", "b", "\n", "c d"), stringTokens("a = '''a''b\nc d'''"));
  }

  @Test
  void aRunOfDigitsIsOneTokenTypedByWhatFollowsIt() {
    assertEquals("DecimalInteger '12345678'", firstValueToken("a = 12345678"));
    // Not an integer, but the value visitor reports that: the run may still be the year or hour of a date or time.
    assertEquals("DecimalInteger '0123'", firstValueToken("a = 0123"));
    assertEquals("DateDigits '1979'", firstValueToken("a = 1979-05-27"));
    assertEquals("DateDigits '07'", firstValueToken("a = 07:32:00"));
    // A sign or an underscore in the run rules out a date or a time.
    assertEquals("Error '+1979'", firstValueToken("a = +1979-05-27"));
    assertEquals("Error '1_979'", firstValueToken("a = 1_979-05-27"));
  }

  @Test
  void anUnclosedValueIsLeftWhereTheNextLineCanOnlyBeTheDocument() {
    // Nothing closes the array, so without leaving it the lexer would read `[tbl]` as an array nested inside it, and
    // the pair below as its content.
    assertEquals(
        List
            .of(
                "UnquotedKey",
                "Equals",
                "ArrayStart",
                "DecimalInteger",
                "Comma",
                "NewLine",
                "TableKeyStart",
                "UnquotedKey",
                "TableKeyEnd",
                "NewLine",
                "UnquotedKey",
                "Equals",
                "DecimalInteger",
                "NewLine"),
        tokenNames("a = [1,\n[tbl]\nb = 2\n"));
  }

  @Test
  void aStrayCharacterDoesNotLeaveTheArray() {
    // `[2]` is an element of the array the document does close, not the header of a table named 2.
    assertEquals(
        List
            .of(
                "UnquotedKey",
                "Equals",
                "ArrayStart",
                "Error",
                "Comma",
                "NewLine",
                "ArrayStart",
                "DecimalInteger",
                "ArrayEnd",
                "Comma",
                "NewLine",
                "ArrayEnd",
                "NewLine"),
        tokenNames("a = [@,\n[2],\n]\n"));
  }

  @Test
  void anUnclosedStringEndsWithItsLine() {
    // The string ends where its line does, and the array ends with it, so `b = 2` is a line of the document.
    assertEquals(
        List
            .of(
                "UnquotedKey",
                "Equals",
                "ArrayStart",
                "QuotationMark",
                "StringChars",
                "NewLine",
                "UnquotedKey",
                "Equals",
                "DecimalInteger",
                "NewLine"),
        tokenNames("a = [\"x\nb = 2\n"));
    // A line that no document line can be is the array's own content, and the array carries on.
    assertEquals(
        List
            .of(
                "UnquotedKey",
                "Equals",
                "ArrayStart",
                "Apostrophe",
                "StringChars",
                "NewLine",
                "DecimalInteger",
                "ArrayEnd",
                "NewLine"),
        tokenNames("a = ['x\n2]\n"));
  }

  private static String firstValueToken(String input) {
    TomlLexer lexer = new TomlLexer(CharStreams.fromString(input));
    boolean afterEquals = false;
    for (Token token : lexer.getAllTokens()) {
      if (afterEquals && token.getChannel() == Token.DEFAULT_CHANNEL) {
        return lexer.getVocabulary().getSymbolicName(token.getType()) + " '" + token.getText() + "'";
      }
      afterEquals = afterEquals || token.getType() == TomlLexer.Equals;
    }
    throw new IllegalArgumentException("no value token in " + input);
  }

  private static List<String> stringTokens(String input) {
    return new TomlLexer(CharStreams.fromString(input))
        .getAllTokens()
        .stream()
        .filter(token -> token.getType() == TomlLexer.StringChars || token.getType() == TomlLexer.EscapeSequence)
        .map(Token::getText)
        .collect(Collectors.toList());
  }

  private static List<String> tokenNames(String input) {
    TomlLexer lexer = new TomlLexer(CharStreams.fromString(input));
    return lexer
        .getAllTokens()
        .stream()
        .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
        .map(token -> lexer.getVocabulary().getSymbolicName(token.getType()))
        .collect(Collectors.toList());
  }
}
