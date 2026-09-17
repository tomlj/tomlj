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

import static org.tomlj.ParseTrees.singleTokenText;
import static org.tomlj.TomlVersion.V1_0_0;

import org.tomlj.internal.TomlLexer;
import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

final class ValueVisitor extends TomlParserBaseVisitor<Object> {

  private static final Pattern zeroFloat = Pattern.compile("[+-]?0+(\\.[+-]?0*)?([eE].*)?");
  private final TomlVersion version;

  ValueVisitor(TomlVersion version) {
    this.version = version;
  }

  @Override
  public Object visitString(TomlParser.StringContext ctx) {
    return ctx.accept(new QuotedStringVisitor(version)).toString();
  }

  @Override
  public Object visitDecInt(TomlParser.DecIntContext ctx) {
    String text = singleTokenText(ctx);
    // The lexer matches a run of digits with a leading zero, as it may be the year or hour of a date or time.
    if (hasLeadingZero(text)) {
      throw new TomlParseError("Leading zeros are not allowed", new TomlPosition(ctx));
    }
    return toLong(stripUnderscores(text), 10, ctx);
  }

  private static boolean hasLeadingZero(String text) {
    int start = (text.charAt(0) == '+' || text.charAt(0) == '-') ? 1 : 0;
    return text.length() > (start + 1) && text.charAt(start) == '0';
  }

  // Removes the underscores that a number may use to group its digits. Not replaceAll, which compiled a regular
  // expression for every number in the document, where replace searches for the underscore directly and returns the
  // number unchanged when it holds none.
  private static String stripUnderscores(String text) {
    return text.replace("_", "");
  }

  @Override
  public Object visitHexInt(TomlParser.HexIntContext ctx) {
    return toLong(stripUnderscores(singleTokenText(ctx).substring(2)), 16, ctx);
  }

  @Override
  public Object visitOctInt(TomlParser.OctIntContext ctx) {
    return toLong(stripUnderscores(singleTokenText(ctx).substring(2)), 8, ctx);
  }

  @Override
  public Object visitBinInt(TomlParser.BinIntContext ctx) {
    return toLong(stripUnderscores(singleTokenText(ctx).substring(2)), 2, ctx);
  }

  private Long toLong(String s, int radix, ParserRuleContext ctx) {
    try {
      return Long.valueOf(s, radix);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Integer is too large", new TomlPosition(ctx));
    }
  }

  @Override
  public Object visitRegularFloat(TomlParser.RegularFloatContext ctx) {
    return toDouble(stripUnderscores(singleTokenText(ctx)), ctx);
  }

  @Override
  public Object visitRegularFloatInf(TomlParser.RegularFloatInfContext ctx) {
    return (singleTokenText(ctx).startsWith("-")) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
  }

  @Override
  public Object visitRegularFloatNaN(TomlParser.RegularFloatNaNContext ctx) {
    return Double.NaN;
  }

  private Double toDouble(String s, ParserRuleContext ctx) {
    try {
      double value = Double.parseDouble(s);
      if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) {
        throw new TomlParseError("Float is too large", new TomlPosition(ctx));
      }
      if (value == 0d && !zeroFloat.matcher(s).matches()) {
        throw new TomlParseError("Float is too small", new TomlPosition(ctx));
      }
      return value;
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid floating point number: " + e.getMessage(), new TomlPosition(ctx));
    }
  }

  @Override
  public Object visitTrueBool(TomlParser.TrueBoolContext ctx) {
    return true;
  }

  @Override
  public Object visitFalseBool(TomlParser.FalseBoolContext ctx) {
    return false;
  }

  @Override
  public Object visitOffsetDateTime(TomlParser.OffsetDateTimeContext ctx) {
    LocalDate date = ctx.date().accept(new LocalDateVisitor());
    LocalTime time = ctx.time().accept(new LocalTimeVisitor(version));
    ZoneOffset offset = ctx.timeOffset().accept(new ZoneOffsetVisitor());
    // A part is null when the parser recovered from a syntax error inside it, which it has already reported.
    if (date == null || time == null || offset == null) {
      return null;
    }
    return OffsetDateTime.of(date, time, offset);
  }

  @Override
  public Object visitLocalDateTime(TomlParser.LocalDateTimeContext ctx) {
    LocalDate date = ctx.date().accept(new LocalDateVisitor());
    LocalTime time = ctx.time().accept(new LocalTimeVisitor(version));
    // A part is null when the parser recovered from a syntax error inside it, which it has already reported.
    if (date == null || time == null) {
      return null;
    }
    return LocalDateTime.of(date, time);
  }

  @Override
  public Object visitLocalDate(TomlParser.LocalDateContext ctx) {
    return ctx.date().accept(new LocalDateVisitor());
  }

  @Override
  public Object visitLocalTime(TomlParser.LocalTimeContext ctx) {
    return ctx.time().accept(new LocalTimeVisitor(version));
  }

  /**
   * Read an array, with the comments written between its brackets.
   *
   * <p>
   * Walked as one flat sequence of values, line breaks and commas, because what a comment documents is what was written
   * beside it: a line break holds the comment ending the line of the value before it and the run above the value after
   * it, and the array itself holds every comment that documents neither.
   */
  @Override
  public Object visitArray(TomlParser.ArrayContext ctx) {
    ListTomlArray array = new ListTomlArray(false, new TomlPosition(ctx));
    List<ParseTree> nodes = Comments.flatten(ctx);
    for (int i = 0; i < nodes.size(); ++i) {
      ParseTree node = nodes.get(i);
      if (node instanceof TomlParser.ValContext) {
        append(array, (TomlParser.ValContext) node, nodes, i);
      } else if (node instanceof TomlParser.LineBreakContext) {
        Comments.unattached(nodes, i, array);
      }
    }
    return array;
  }

  private void append(ListTomlArray array, TomlParser.ValContext ctx, List<ParseTree> nodes, int index) {
    List<TomlComment> comments = TomlComment.attached(Comments.above(nodes, index), Comments.after(nodes, index));
    Object value = ctx.accept(this);
    if (value == null) {
      return;
    }
    TomlPosition position = new TomlPosition(ctx);
    try {
      array.appendParsed(value, position, comments);
    } catch (TomlInvalidTypeException e) {
      throw new TomlParseError(e.getMessage(), position);
    }
  }

  /**
   * Read an inline table, with the comments written between its braces.
   *
   * <p>
   * Walked as one flat sequence, as {@link #visitArray} is, with the table's key/value pairs where an array has its
   * values.
   */
  @Override
  public Object visitInlineTable(TomlParser.InlineTableContext ctx) {
    if (!version.after(V1_0_0)) {
      checkSingleLineInlineTable(ctx);
    }
    LinkedTomlTable table = LinkedTomlTable.inline(new TomlPosition(ctx));
    // The tables that dotted keys open within this one, which close with it: nothing written later may add to them.
    Map<LinkedTomlTable, TomlPosition> openTables = null;
    List<ParseTree> nodes = Comments.flatten(ctx);
    for (int i = 0; i < nodes.size(); ++i) {
      ParseTree node = nodes.get(i);
      if (node instanceof TomlParser.KeyvalContext) {
        if (openTables == null) {
          openTables = new HashMap<>();
        }
        set(table, openTables, (TomlParser.KeyvalContext) node, nodes, i);
      } else if (node instanceof TomlParser.LineBreakContext) {
        Comments.unattached(nodes, i, table);
      }
    }
    if (openTables != null) {
      openTables.forEach(LinkedTomlTable::define);
    }
    return table;
  }

  private void set(
      LinkedTomlTable table,
      Map<LinkedTomlTable, TomlPosition> openTables,
      TomlParser.KeyvalContext ctx,
      List<ParseTree> nodes,
      int index) {
    TomlParser.KeyContext keyContext = ctx.key();
    TomlParser.ValContext valContext = ctx.val();
    if (keyContext == null || valContext == null) {
      return;
    }
    List<String> path = keyContext.accept(new KeyVisitor(version));
    if (path == null || path.isEmpty()) {
      return;
    }
    List<TomlComment> comments = TomlComment.attached(Comments.above(nodes, index), Comments.after(nodes, index));
    Object value = valContext.accept(this);
    if (value != null) {
      table
          .setParsed(path, Value.of(value, new TomlPosition(valContext)), new TomlPosition(ctx), comments)
          .forEach(entry -> openTables.putIfAbsent(entry.getKey(), entry.getValue()));
    }
  }

  // Newlines and trailing commas in inline tables were added in TOML 1.1.0. Newlines inside the values of an inline
  // table (multi-line strings and arrays) are not direct children of the inline table rules, so are not collected.
  private static void checkSingleLineInlineTable(TomlParser.InlineTableContext ctx) {
    List<Token> unsupported = new ArrayList<>();
    for (TomlParser.LineBreakContext lineBreak : ctx.lineBreak()) {
      unsupported.add(lineBreak.NewLine(0).getSymbol());
    }
    TerminalNode trailingComma = ctx.Comma();
    if (trailingComma != null) {
      unsupported.add(trailingComma.getSymbol());
    }
    TomlParser.InlineTableValuesContext valuesContext = ctx.inlineTableValues();
    if (valuesContext != null) {
      for (TomlParser.LineBreakContext lineBreak : valuesContext.lineBreak()) {
        unsupported.add(lineBreak.NewLine(0).getSymbol());
      }
      for (TomlParser.InlineTableValueContext value : valuesContext.inlineTableValue()) {
        if (value.lineBreak() != null) {
          unsupported.add(value.lineBreak().NewLine(0).getSymbol());
        }
      }
    }
    if (unsupported.isEmpty()) {
      return;
    }
    Token first = Collections.min(unsupported, Comparator.comparingInt(Token::getTokenIndex));
    String message = (first.getType() == TomlLexer.Comma) ? "A trailing comma is not allowed in an inline table"
        : "Newlines are not allowed in an inline table";
    throw new TomlParseError(
        message + " (TOML versions before 1.1.0)",
        TomlPosition.positionAt(first.getLine(), first.getCharPositionInLine() + 1));
  }

  @Override
  protected Object aggregateResult(Object aggregate, Object nextResult) {
    return nextResult;
  }

  @Override
  protected Object defaultResult() {
    return null;
  }
}
