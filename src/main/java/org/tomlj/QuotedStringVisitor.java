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

import static org.tomlj.TomlVersion.V0_5_0;
import static org.tomlj.TomlVersion.V1_0_0;

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import org.antlr.v4.runtime.ParserRuleContext;

final class QuotedStringVisitor extends TomlParserBaseVisitor<StringBuilder> {

  private final TomlVersion version;
  private final StringBuilder builder = new StringBuilder();

  public QuotedStringVisitor(TomlVersion version) {
    this.version = version;
  }

  @Override
  public StringBuilder visitLiteralBody(TomlParser.LiteralBodyContext ctx) {
    return appendText(ctx.getText(), ctx);
  }

  @Override
  public StringBuilder visitMlLiteralBody(TomlParser.MlLiteralBodyContext ctx) {
    return appendText(ctx.getText(), ctx);
  }

  @Override
  public StringBuilder visitBasicUnescaped(TomlParser.BasicUnescapedContext ctx) {
    return appendText(ctx.getText(), ctx);
  }

  @Override
  public StringBuilder visitMlBasicUnescaped(TomlParser.MlBasicUnescapedContext ctx) {
    return appendText(ctx.getText(), ctx);
  }

  private StringBuilder appendText(String text, ParserRuleContext ctx) {
    if (!(version.after(V0_5_0)) && text.indexOf('\t') != -1) {
      throw new TomlParseError(
          "Use \\t to represent a tab in a string (TOML versions before 1.0.0)",
          new TomlPosition(ctx));
    }
    return builder.append(text);
  }

  @Override
  public StringBuilder visitEscaped(TomlParser.EscapedContext ctx) {
    String text = ctx.getText();
    if (text.isEmpty()) {
      return builder;
    }
    assert (text.charAt(0) == '\\');
    if (text.length() == 1) {
      return builder.append('\\');
    }
    switch (text.charAt(1)) {
      case '"':
        return builder.append('"');
      case '\\':
        return builder.append('\\');
      case 'b':
        return builder.append('\b');
      case 'f':
        return builder.append('\f');
      case 'n':
        return builder.append('\n');
      case 'r':
        return builder.append('\r');
      case 't':
        return builder.append('\t');
      case 'e':
        checkEscapeVersion(text, ctx);
        return builder.append('\u001B');
      case 'x':
        checkEscapeVersion(text, ctx);
        return builder.append(convertUnicodeEscape(text, 4, ctx));
      case 'u':
        return builder.append(convertUnicodeEscape(text, 6, ctx));
      case 'U':
        return builder.append(convertUnicodeEscape(text, 10, ctx));
      default:
        throw new TomlParseError("Invalid escape sequence '" + text + "'", new TomlPosition(ctx));
    }
  }

  private void checkEscapeVersion(String text, TomlParser.EscapedContext ctx) {
    // \e and \xHH were added in TOML 1.1.0
    if (!version.after(V1_0_0)) {
      throw new TomlParseError(
          "Invalid escape sequence '" + text + "' (TOML versions before 1.1.0)",
          new TomlPosition(ctx));
    }
  }

  private char[] convertUnicodeEscape(String text, int expectedLength, TomlParser.EscapedContext ctx) {
    // The lexer only produces a full escape when the expected number of hex digits follows; a truncated escape
    // is lexed as an escape of the single character after the backslash
    if (text.length() != expectedLength) {
      throw new TomlParseError("Invalid unicode escape sequence", new TomlPosition(ctx));
    }
    try {
      char[] characters = Character.toChars(Integer.parseInt(text.substring(2), 16));
      if (characters.length == 1 && Character.isSurrogate(characters[0])) {
        throw new IllegalArgumentException();
      }
      return characters;
    } catch (IllegalArgumentException e) {
      throw new TomlParseError("Invalid unicode escape sequence", new TomlPosition(ctx));
    }
  }

  @Override
  protected StringBuilder aggregateResult(StringBuilder aggregate, StringBuilder nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected StringBuilder defaultResult() {
    return builder;
  }
}
