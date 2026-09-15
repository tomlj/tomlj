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
}
