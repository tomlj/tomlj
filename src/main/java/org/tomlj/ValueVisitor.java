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

import static org.tomlj.ParseTrees.at;
import static org.tomlj.ParseTrees.isToken;
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
import org.checkerframework.checker.nullness.qual.Nullable;

final class ValueVisitor extends TomlParserBaseVisitor<Object> {

  private static final Pattern zeroFloat = Pattern.compile("[+-]?0+(\\.[+-]?0*)?([eE].*)?");
  private final TomlVersion version;

  // The text the document was parsed from, or null when none was kept and nothing is recorded about where the values
  // were written.
  private final @Nullable Source source;

  ValueVisitor(TomlVersion version, @Nullable Source source) {
    this.version = version;
    this.source = source;
  }

  @Override
  public Object visitString(TomlParser.StringContext ctx) {
    return ctx.accept(new QuotedStringVisitor(version, source)).toString();
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
    LocalTime time = ctx.time().accept(new LocalTimeVisitor(version, source));
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
    LocalTime time = ctx.time().accept(new LocalTimeVisitor(version, source));
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
    return ctx.time().accept(new LocalTimeVisitor(version, source));
  }

  /**
   * Read an array, with the comments written between its brackets.
   *
   * <p>
   * Walked as one flat sequence of values, line breaks and commas, because the entry a comment is attached to is the
   * one written beside it: a line break holds the comment ending the line of the value before it and the run above the
   * value after it, and the array itself holds every comment attached to neither, as an unattached comment.
   */
  @Override
  public Object visitArray(TomlParser.ArrayContext ctx) {
    ListTomlArray array = new ListTomlArray(false, new TomlPosition(ctx));
    List<ParseTree> nodes = Comments.flatten(ctx);
    ElementSpans spans = spansBetween(ctx.ArrayStart(), ctx.ArrayEnd());
    for (int i = 0; i < nodes.size(); ++i) {
      ParseTree node = nodes.get(i);
      if (node instanceof TomlParser.ValContext) {
        append(array, (TomlParser.ValContext) node, nodes, i, spans);
      } else if (node instanceof TomlParser.LineBreakContext) {
        addUnattached(array, nodes, i, spans);
      }
    }
    if (spans != null) {
      array.bracketSpan = spans.brackets();
    }
    return array;
  }

  private void append(
      ListTomlArray array,
      TomlParser.ValContext ctx,
      List<ParseTree> nodes,
      int index,
      @Nullable ElementSpans spans) {
    List<TomlComment> comments = TomlComment.withoutNulls(Comments.above(nodes, index), Comments.after(nodes, index));
    Object value = ctx.accept(this);
    if (value == null) {
      return;
    }
    TomlPosition position = new TomlPosition(ctx);
    Entry.Indexed entry;
    try {
      entry = array.appendParsed(value, position, comments);
    } catch (TomlInvalidTypeException e) {
      throw new TomlParseError(e.getMessage(), position);
    }
    if (spans != null) {
      entry.span = spans.element(nodes, index, null, 0, ctx);
      spans.recordScalar(entry.value, ctx);
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
    checkInlineTableVersion(ctx);
    LinkedTomlTable table = LinkedTomlTable.inline(new TomlPosition(ctx));
    // The tables that dotted keys open within this one, which close with it: nothing written later may add to them.
    Map<LinkedTomlTable, TomlPosition> openTables = null;
    List<ParseTree> nodes = Comments.flatten(ctx);
    ElementSpans spans = spansBetween(ctx.InlineTableStart(), ctx.InlineTableEnd());
    for (int i = 0; i < nodes.size(); ++i) {
      ParseTree node = nodes.get(i);
      if (node instanceof TomlParser.KeyvalContext) {
        if (openTables == null) {
          openTables = new HashMap<>();
        }
        set(table, openTables, (TomlParser.KeyvalContext) node, nodes, i, spans);
      } else if (node instanceof TomlParser.LineBreakContext) {
        addUnattached(table, nodes, i, spans);
      }
    }
    if (openTables != null) {
      openTables.forEach(LinkedTomlTable::define);
    }
    if (spans != null) {
      table.bracketSpan = spans.brackets();
    }
    return table;
  }

  private void set(
      LinkedTomlTable table,
      Map<LinkedTomlTable, TomlPosition> openTables,
      TomlParser.KeyvalContext ctx,
      List<ParseTree> nodes,
      int index,
      @Nullable ElementSpans spans) {
    TomlParser.KeyContext keyContext = ctx.key();
    TomlParser.ValContext valContext = ctx.val();
    if (keyContext == null || valContext == null) {
      return;
    }
    List<String> path = keyContext.accept(new KeyVisitor(version, source));
    if (path == null || path.isEmpty()) {
      return;
    }
    List<TomlComment> comments = TomlComment.withoutNulls(Comments.above(nodes, index), Comments.after(nodes, index));
    Object value = valContext.accept(this);
    if (value == null) {
      return;
    }
    table
        .setParsed(path, Value.of(value, new TomlPosition(valContext)), new TomlPosition(ctx), comments)
        .forEach(entry -> openTables.putIfAbsent(entry.getKey(), entry.getValue()));
    if (spans == null) {
      return;
    }
    // A dotted key records its parts on the entry it ends at, which is the one in the table the key opened.
    Entry.KeyValue entry = table.entry(path);
    if (entry != null) {
      entry.span = spans.element(nodes, index, keyContext, path.size(), valContext);
      spans.recordScalar(entry.value, valContext);
    }
  }

  /**
   * Add the unattached comments of a line break to the array or inline table they were written in.
   *
   * @param container The array or inline table.
   * @param nodes Its flattened nodes.
   * @param index The index of the line break.
   * @param spans Where its elements are being recorded, or {@code null} if nothing is recorded.
   */
  private static void addUnattached(
      ElementContainer<?> container,
      List<ParseTree> nodes,
      int index,
      @Nullable ElementSpans spans) {
    TomlParser.LineBreakContext lineBreak = (TomlParser.LineBreakContext) nodes.get(index);
    for (ParseTree node : Comments.unattached(nodes, index)) {
      TomlComment comment = Comments.ofUnattached(node);
      container.addParsedComment((spans != null) ? comment.withSpan(spans.comment(node, lineBreak)) : comment);
    }
  }

  /**
   * Start recording where the elements written between a pair of brackets sit.
   *
   * @param open The opening bracket.
   * @param close The closing bracket.
   * @return The record, or {@code null} if no source was kept, or if the bracket is a token the parser inserted during
   *         error recovery.
   */
  @Nullable
  private ElementSpans spansBetween(@Nullable TerminalNode open, @Nullable TerminalNode close) {
    if (source == null || open == null || close == null) {
      return null;
    }
    Token first = open.getSymbol();
    Token last = close.getSymbol();
    if (first.getTokenIndex() < 0 || last.getTokenIndex() < 0) {
      return null;
    }
    return new ElementSpans(source, first.getStartIndex(), last.getStartIndex());
  }

  /**
   * Where each element of one array or inline table, and each comment written between its brackets, sits in the source.
   *
   * <p>
   * The elements are recorded as they are read, in the order they were written, and between them a cursor holds the
   * first offset not yet in a span. Each element's span covers the text from the cursor - its leading whitespace, and
   * the comment run above it - through its key and value to the end of its tail: the comma that follows it, the comment
   * after it, and the newline ending its line. What is left between the last of them and the closing bracket is the
   * container's trailer.
   */
  private static final class ElementSpans {

    private final Source source;
    private final int open;
    private final int close;
    private int cursor;

    ElementSpans(Source source, int open, int close) {
      this.source = source;
      this.open = open;
      this.close = close;
      this.cursor = open + 1;
    }

    /**
     * Record where an element was written, taking its text from the cursor to the end of its tail.
     *
     * @param nodes The flattened nodes of the array or inline table.
     * @param index The index of the element.
     * @param keyContext The key as written, or {@code null} for an element of an array.
     * @param keyParts The number of keys the written key has, or {@code 0} for an element of an array.
     * @param valContext The value as written.
     * @return The span.
     */
    SourceSpan element(
        List<ParseTree> nodes,
        int index,
        TomlParser.@Nullable KeyContext keyContext,
        int keyParts,
        TomlParser.ValContext valContext) {
      SourceSpan.Builder span = new SourceSpan.Builder(source, SourceSpan.Kind.ELEMENT, cursor);
      TomlParser.CommentRunContext run = Comments.runAbove(nodes, index);
      if (run != null) {
        span.above(run);
      }
      if (keyContext != null) {
        span.key(keyContext, keyParts);
      }
      span.value(valContext.getStart().getStartIndex());
      TerminalNode after = Comments.commentAfter(nodes, index);
      if (after != null) {
        span.after(after.getSymbol());
      }
      int valueStop = valContext.getStop().getStopIndex();
      span.tail(valueStop + 1);
      int stop = valueStop;
      ParseTree next = at(nodes, index + 1);
      if (isToken(next, TomlParser.Comma)) {
        Token comma = ((TerminalNode) next).getSymbol();
        span.comma(comma.getStartIndex());
        stop = comma.getStopIndex();
        next = at(nodes, index + 2);
      }
      if (next instanceof TomlParser.LineBreakContext) {
        // The line break's first newline ends the element's line, and the element's span includes it and the comment
        // after the element. The newlines and runs written after it are separate lines, and belong to what follows.
        TerminalNode newline = ((TomlParser.LineBreakContext) next).NewLine(0);
        if (newline != null && newline.getSymbol().getStopIndex() > stop) {
          stop = newline.getSymbol().getStopIndex();
        }
      }
      cursor = stop + 1;
      return span.build(stop);
    }

    /**
     * Record where an unattached comment was written: its span covers the text from the cursor to the newline ending
     * its last line.
     *
     * @param node The comment, as {@link Comments#unattached} returns it.
     * @param lineBreak The line break it was written in, which holds the newline ending a comment that ends a line.
     * @return The span.
     */
    SourceSpan comment(ParseTree node, TomlParser.LineBreakContext lineBreak) {
      int start = cursor;
      int commentStart;
      int commentStop;
      if (node instanceof TerminalNode) {
        Token written = ((TerminalNode) node).getSymbol();
        commentStart = written.getStartIndex();
        // A comment ending a line includes that line's newline, which the line break holds directly after it.
        TerminalNode newline = lineBreak.NewLine(0);
        commentStop = (newline != null && newline.getSymbol().getStopIndex() > written.getStopIndex())
            ? newline.getSymbol().getStopIndex()
            : written.getStopIndex();
      } else {
        TomlParser.CommentRunContext run = (TomlParser.CommentRunContext) node;
        commentStart = run.getStart().getStartIndex();
        commentStop = run.getStop().getStopIndex();
      }
      cursor = commentStop + 1;
      return new SourceSpan.Builder(source, SourceSpan.Kind.COMMENT, start)
          .above(commentStart, commentStop)
          .build(commentStop);
    }

    /**
     * Record the literal a scalar element was written as.
     *
     * @param value The value stored, which records nothing if it is a table or an array: those record their own
     *        brackets.
     * @param valContext The value as written.
     */
    void recordScalar(Value value, TomlParser.ValContext valContext) {
      if (value instanceof Value.Scalar) {
        ((Value.Scalar) value).span =
            ValueSpan.scalar(source, valContext.getStart().getStartIndex(), valContext.getStop().getStopIndex());
      }
    }

    /**
     * The brackets of the array or inline table, with the whitespace left before the closing one.
     *
     * @return The span.
     */
    ValueSpan brackets() {
      return ValueSpan.brackets(source, open, close, cursor);
    }
  }

  // Newlines and trailing commas in inline tables were added in TOML 1.1.0: reading for 1.0.0 rejects them, and reading
  // for a later version records them, so that a writer copying the text for 1.0.0 refuses to. Newlines inside the
  // values of an inline table (multi-line strings and arrays) are not direct children of the inline table rules, so
  // are not collected.
  private void checkInlineTableVersion(TomlParser.InlineTableContext ctx) {
    List<Token> unsupported = lineBreaksAndTrailingComma(ctx);
    if (unsupported.isEmpty()) {
      return;
    }
    if (!version.after(V1_0_0)) {
      Token first = Collections.min(unsupported, Comparator.comparingInt(Token::getTokenIndex));
      String message = (first.getType() == TomlLexer.Comma) ? "A trailing comma is not allowed in an inline table"
          : "Newlines are not allowed in an inline table";
      throw new TomlParseError(
          message + " (TOML versions before 1.1.0)",
          TomlPosition.positionAt(first.getLine(), first.getCharPositionInLine() + 1));
    }
    if (source != null) {
      for (Token token : unsupported) {
        String what = (token.getType() == TomlLexer.Comma) ? "A trailing comma in an inline table"
            : "A newline inside an inline table";
        source
            .requireVersion(
                token.getStartIndex(),
                TomlVersion.V1_1_0,
                what,
                TomlPosition.positionAt(token.getLine(), token.getCharPositionInLine() + 1));
      }
    }
  }

  /**
   * The tokens of an inline table that TOML 1.0.0 does not allow: the newline of each line break between its braces,
   * and the comma after its last entry.
   *
   * @param ctx The inline table.
   * @return The tokens, in no particular order.
   */
  private static List<Token> lineBreaksAndTrailingComma(TomlParser.InlineTableContext ctx) {
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
    return unsupported;
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
