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
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.*;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;

final class LineVisitor extends TomlParserBaseVisitor<LinkedTomlTable> {

  private final TomlVersion version;
  private final ErrorReporter errorReporter;
  private final ParsedTomlTable rootTable;
  private LinkedTomlTable currentTable;
  // The comments attached to the expression being visited, which the document rule reads from the tree around it.
  private List<TomlComment> attached = Collections.emptyList();
  // The number of tables and arrays enclosing the entries of currentTable, not counting the root table. Starts at 0.
  private int currentDepth;
  private final Map<LinkedTomlTable, TomlPosition> openTables;
  // The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
  // in the document.
  private final int maxNestingDepth;

  // The text the document was parsed from, or null when none was kept and nothing is recorded about where each line
  // was written.
  private final @Nullable Source source;
  // The tokens, read to find the newline ending a line and anything the parser skipped before it.
  private final BufferedTokenStream tokens;

  // Where the expression being visited was written, read from the tree and the tokens around it before it is visited,
  // as its attached comments are: the comment run above it, the comment after it, and the newline ending its line. Each
  // is null where the expression has no such part.
  private TomlParser.@Nullable CommentRunContext runAbove;
  private @Nullable Token commentAfter;
  private @Nullable Token newline;
  // Whether the parser reported and skipped input between the expression and the newline ending its line.
  private boolean strayInput;
  // The source the parts above were read from, or null when they are not recorded: when no source was kept, or when the
  // expression being visited matched no token to read them from.
  private @Nullable Source lineSource;

  LineVisitor(
      ParsedTomlTable rootTable,
      TomlVersion version,
      ErrorReporter errorReporter,
      int maxNestingDepth,
      BufferedTokenStream tokens) {
    this.version = version;
    this.errorReporter = errorReporter;
    this.rootTable = rootTable;
    this.currentTable = rootTable;
    this.openTables = new HashMap<>();
    this.maxNestingDepth = maxNestingDepth;
    this.source = rootTable.source();
    this.tokens = tokens;
  }

  /**
   * Visit the lines of the document, handing each expression the comments written around it.
   *
   * <p>
   * Walked here rather than left to {@link #visitChildren}, because what a comment is attached to depends on what the
   * tree holds beside it: the run before an expression and the comment ending its line are attached to it, and every
   * other run is unattached and belongs to a container. A run directly under the line above it belongs to the container
   * open where it was written, which is known when the run is reached. A run separated by a blank line from what
   * precedes it belongs to the container of the expression that follows, which is not known yet, so such runs are held
   * until that expression is reached.
   */
  @Override
  public LinkedTomlTable visitToml(TomlParser.TomlContext ctx) {
    List<TomlComment> separated = null;
    int childCount = ctx.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      ParseTree child = ctx.getChild(i);
      ParseTree previous = (i > 0) ? ctx.getChild(i - 1) : null;
      ParseTree beforePrevious = (i > 1) ? ctx.getChild(i - 2) : null;
      ParseTree next = ((i + 1) < childCount) ? ctx.getChild(i + 1) : null;
      if (child instanceof TomlParser.ExpressionContext) {
        if (separated != null) {
          // A header is written in the document rather than in the section it opens, so the runs before it belong to
          // the root table rather than to that section.
          LinkedTomlTable container =
              (((TomlParser.ExpressionContext) child).table() != null) ? rootTable : currentTable;
          separated.forEach(container::addParsedComment);
          separated = null;
        }
        attached = TomlComment.withoutNulls(Comments.above(previous), Comments.after(next));
        recordExpressionOffsets((TomlParser.ExpressionContext) child, previous, next);
        child.accept(this);
      } else if (child instanceof TomlParser.CommentRunContext && !(next instanceof TomlParser.ExpressionContext)) {
        // A run directly above an expression is handed to it when it is reached; this one is unattached.
        TomlComment comment = commentWithSpan((TomlParser.CommentRunContext) child);
        if (Comments.glued(previous, beforePrevious)) {
          currentTable.addParsedComment(comment);
        } else {
          if (separated == null) {
            separated = new ArrayList<>();
          }
          separated.add(comment);
        }
      }
    }
    if (separated != null) {
      // No expression follows the runs left at the end of the document, so they belong to the root table.
      separated.forEach(rootTable::addParsedComment);
    }
    // No header follows the last section to define the tables its dotted keys opened.
    defineOpenTables();
    rootTable.recordTrailer();
    return rootTable;
  }

  /**
   * Read from the tree and the tokens around an expression where it was written, before it is visited.
   *
   * <p>
   * The comment run above the expression and the comment ending its line are the ones {@link Comments} attaches to it,
   * read here as the nodes that hold their offsets, since the comments themselves carry only their text.
   *
   * @param ctx The expression.
   * @param previous The node written before it, or {@code null} if it is the first thing written.
   * @param next The node written after it.
   */
  private void recordExpressionOffsets(
      TomlParser.ExpressionContext ctx,
      @Nullable ParseTree previous,
      @Nullable ParseTree next) {
    Token stop = ctx.getStop();
    lineSource = (stop != null && stop.getTokenIndex() >= 0) ? source : null;
    if (lineSource == null) {
      return;
    }
    runAbove = (previous instanceof TomlParser.CommentRunContext) ? (TomlParser.CommentRunContext) previous : null;
    commentAfter = null;
    if (next instanceof TerminalNode && !(next instanceof ErrorNode)) {
      Token comment = ((TerminalNode) next).getSymbol();
      if (comment.getType() == TomlLexer.Comment) {
        commentAfter = comment;
      }
    }
    if (!recordLineEnd(stop)) {
      lineSource = null;
    }
  }

  /**
   * Walk the tokens from the end of an expression to the newline ending its line, recording where that newline is and
   * whether the parser skipped anything else on the way.
   *
   * <p>
   * The lexer ends the last line of a document with a newline where the document has none, so a line of a document the
   * parser read without error always ends with one. An unclosed value includes the newline of its last line, and the
   * input then ends without one; there is no line to record, and the pair is rejected anyway.
   *
   * @param stop The last token of the expression.
   * @return {@code true} if the line ends with a newline.
   */
  private boolean recordLineEnd(Token stop) {
    strayInput = false;
    newline = null;
    for (int i = stop.getTokenIndex() + 1; i < tokens.size(); ++i) {
      Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL) {
        continue;
      }
      if (token.getType() == TomlLexer.NewLine) {
        newline = token;
        return true;
      }
      if (token.getType() == Token.EOF) {
        return false;
      }
      // A comment runs to the end of its line, so it is the only thing that may be written between the expression and
      // the newline; anything else is input the parser reported and skipped.
      if (token.getType() != TomlLexer.Comment) {
        strayInput = true;
      }
    }
    return false;
  }

  /**
   * Record an unattached comment run of the document.
   *
   * @param run The run.
   * @return The comment, with where it and the blank lines above it were written if the source was kept.
   */
  private TomlComment commentWithSpan(TomlParser.CommentRunContext run) {
    TomlComment comment = Comments.of(run, TomlComment.Placement.UNATTACHED);
    if (source == null) {
      return comment;
    }
    int start = source.leadingStart(run.getStart().getStartIndex());
    return comment
        .withSpan(
            new SourceSpan.Builder(source, SourceSpan.Kind.COMMENT, start)
                .above(run)
                .build(run.getStop().getStopIndex()));
  }

  @Override
  public LinkedTomlTable visitKeyval(TomlParser.KeyvalContext ctx) {
    // A key/value pair is written inside the section that is open, and so is any comment written around it.
    List<TomlComment> comments = attached;
    TomlParser.KeyContext keyContext = ctx.key();
    TomlParser.ValContext valContext = ctx.val();
    if (keyContext == null || valContext == null) {
      return rootTable;
    }
    try {
      List<String> path = keyContext.accept(new KeyVisitor(version));
      if (path == null || path.isEmpty()) {
        return rootTable;
      }
      Object value = valContext.accept(new ValueVisitor(version));
      if (value != null && !hasSyntaxError(ctx)) {
        if ((long) currentDepth + ctx.nesting > maxNestingDepth) {
          throw new TomlParseError(AbstractTomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
        }
        Value wrapped = Value.of(value, new TomlPosition(valContext));
        currentTable
            .setParsed(path, wrapped, new TomlPosition(ctx), comments)
            .forEach(entry -> openTables.putIfAbsent(entry.getKey(), entry.getValue()));
        recordLine(currentTable, path, keyContext, valContext, wrapped);
      }
      return rootTable;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
      return rootTable;
    }
  }

  @Override
  public LinkedTomlTable visitStandardTable(TomlParser.StandardTableContext ctx) {
    List<TomlComment> comments = attached;
    defineOpenTables();
    if (hasRecoveredKey(ctx, TomlParser.TableKeyEnd)) {
      return rootTable;
    }
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return rootTable;
    }
    try {
      List<String> path = keyContext.accept(new KeyVisitor(version));
      if (path == null) {
        return rootTable;
      }
      // The table named by the header's last key is enclosed by the tables and arrays its leading keys name.
      int depth = headerDepth(path);
      if (depth > maxNestingDepth) {
        throw new TomlParseError(AbstractTomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
      }
      currentTable = rootTable.createParsedTable(path, new TomlPosition(ctx), comments);
      recordHeader(currentTable, path, ctx);
      currentDepth = depth + 1;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
    }
    return rootTable;
  }

  @Override
  public LinkedTomlTable visitArrayTable(TomlParser.ArrayTableContext ctx) {
    List<TomlComment> comments = attached;
    defineOpenTables();
    if (hasRecoveredKey(ctx, TomlParser.ArrayTableKeyEnd)) {
      return rootTable;
    }
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return rootTable;
    }
    try {
      List<String> path = keyContext.accept(new KeyVisitor(version));
      if (path == null) {
        return rootTable;
      }
      // The array named by the header's last key is enclosed by the tables and arrays its leading keys name, and its
      // new element table is enclosed by that array as well.
      int depth = headerDepth(path);
      if ((long) depth + 1 > maxNestingDepth) {
        throw new TomlParseError(AbstractTomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
      }
      currentTable = rootTable.createParsedTableArray(path, new TomlPosition(ctx), comments);
      recordHeader(currentTable, path, ctx);
      currentDepth = depth + 2;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
    }
    return rootTable;
  }

  /**
   * Compute the depth of the table or array named by a header's last key, without creating anything.
   *
   * <p>
   * Walks {@link #rootTable} along the header's leading keys ({@code path} without its last element), mirroring
   * {@code LinkedTomlTable.ensureTable}'s {@code followTableArrays} behaviour: each leading key adds one level, and a
   * leading key that names a (non-empty) array of tables adds a second level, because the header walks into the last
   * table of that array rather than the array itself. A key that is absent, or that names anything else, stops the
   * walk, but every remaining leading key still adds its one level, since {@code createParsedTable} /
   * {@code createParsedTableArray} will report the real error for it.
   */
  private int headerDepth(List<String> path) {
    int depth = 0;
    LinkedTomlTable table = rootTable;
    for (int i = 0; i < path.size() - 1; i++) {
      depth++;
      if (table == null) {
        continue;
      }
      Object value = table.get(Collections.singletonList(path.get(i)));
      if (value instanceof ListTomlArray
          && ((ListTomlArray) value).isTableArray()
          && !((ListTomlArray) value).isEmpty()) {
        ListTomlArray array = (ListTomlArray) value;
        depth++;
        table = (LinkedTomlTable) array.get(array.size() - 1);
      } else if (value instanceof LinkedTomlTable) {
        table = (LinkedTomlTable) value;
      } else {
        table = null;
      }
    }
    return depth;
  }

  /**
   * Record where a key/value pair, and the literal of its value, were written.
   *
   * @param table The table the pair was set in, which the path is relative to.
   * @param path The key path the pair was set at.
   * @param keyContext The key as written.
   * @param valContext The value as written.
   * @param value The value stored.
   */
  private void recordLine(
      LinkedTomlTable table,
      List<String> path,
      TomlParser.KeyContext keyContext,
      TomlParser.ValContext valContext,
      Value value) {
    Source from = lineSource;
    Token lineEnd = newline;
    if (from == null || lineEnd == null) {
      return;
    }
    Entry.KeyValue entry = table.entry(path);
    if (entry == null) {
      return;
    }
    int valueStart = valContext.getStart().getStartIndex();
    int valueStop = valContext.getStop().getStopIndex();
    entry.span = lineSpan(from, SourceSpan.Kind.LINE, keyContext, path.size(), lineEnd)
        .value(valueStart)
        .tail(tailStart(valueStop, lineEnd))
        .build(lineEnd.getStopIndex());
    if (value instanceof Value.Scalar) {
      ((Value.Scalar) value).span = ValueSpan.scalar(from, valueStart, valueStop);
    }
  }

  /**
   * Record where the header that defined a table, or opened an element of an array of tables, was written.
   *
   * @param table The table the header names.
   * @param path The header's key path.
   * @param ctx The header as written, brackets included.
   */
  private void recordHeader(LinkedTomlTable table, List<String> path, ParserRuleContext ctx) {
    Source from = lineSource;
    Token lineEnd = newline;
    if (from == null || lineEnd == null) {
      return;
    }
    // Input skipped after a header is written where the comment on its line would be, so the two never both appear.
    assert !strayInput || commentAfter == null : "a header the parser skipped input after has a comment after it";
    table.headerSpan = lineSpan(from, SourceSpan.Kind.HEADER, ctx, path.size(), lineEnd)
        .tail(tailStart(ctx.getStop().getStopIndex(), lineEnd))
        .build(lineEnd.getStopIndex());
  }

  /**
   * Start the span of the line being visited, with the parts a key/value pair and a header share.
   *
   * @param from The source the line was read from.
   * @param kind LINE or HEADER.
   * @param key The key as written, or the whole header.
   * @param keyParts The number of keys the written key has.
   * @param lineEnd The newline ending the line.
   * @return The builder, whose start is the first offset of the line's leading whitespace: the blank lines above it and
   *         the indentation of its first line.
   */
  private SourceSpan.Builder lineSpan(
      Source from,
      SourceSpan.Kind kind,
      ParserRuleContext key,
      int keyParts,
      Token lineEnd) {
    TomlParser.CommentRunContext run = runAbove;
    Token comment = commentAfter;
    ParserRuleContext first = (run != null) ? run : key;
    SourceSpan.Builder span = new SourceSpan.Builder(from, kind, from.leadingStart(first.getStart().getStartIndex()));
    if (run != null) {
      span.above(run);
    }
    span.key(key, keyParts).newline(lineEnd);
    if (comment != null) {
      span.after(comment);
    }
    return span;
  }

  /**
   * The first offset copied after the value or header of the line being visited.
   *
   * @param stop The last offset of the value, or of the header where there is no value.
   * @param lineEnd The newline ending the line.
   * @return {@code stop + 1}, or the start of the newline where the parser skipped input before it, so that the input
   *         it skipped is not copied.
   */
  private int tailStart(int stop, Token lineEnd) {
    return strayInput ? lineEnd.getStartIndex() : (stop + 1);
  }

  @Override
  protected LinkedTomlTable aggregateResult(LinkedTomlTable aggregate, LinkedTomlTable nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected LinkedTomlTable defaultResult() {
    return rootTable;
  }

  /**
   * Check whether a key/value pair contains a syntax error.
   *
   * <p>
   * The parser recovers from syntax errors and still produces a (partial) parse tree, e.g. {@code key = 4uoxyz} yields
   * a key/value pair for {@code key} with the integer value 4 followed by an error for the trailing {@code uoxyz}. Such
   * values are not what the document expresses, so they are discarded rather than stored.
   *
   * <p>
   * An error is attributed to the pair if it was reported on any of the lines the pair spans (which catches trailing
   * input that the parser rejected after completing the pair), or if the parser recorded a recognition failure within
   * the pair's parse tree (which catches an unterminated array or inline table, whose missing closing bracket is
   * reported at the start of the next expression).
   */
  private boolean hasSyntaxError(TomlParser.KeyvalContext ctx) {
    Token start = ctx.getStart();
    Token stop = ctx.getStop();
    if (start == null || stop == null) {
      return true;
    }
    return errorReporter.hasSyntaxErrorBetween(start.getLine(), stop.getLine()) || hasRecognitionError(ctx);
  }

  /**
   * Check whether a table header's key was changed by error recovery.
   *
   * <p>
   * The parser recovers from syntax errors and still produces a header, e.g. {@code [a b]} yields the header of table
   * {@code a} by discarding {@code b}, and {@code [']} yields the header of a table named {@code ]} by completing the
   * unterminated string. Such a header does not name the table the document meant, so it is discarded rather than
   * opened, leaving the key/value pairs that follow it in the table the document was already in.
   *
   * <p>
   * A header the parser completed by inserting its closing bracket during error recovery is the exception: nothing was
   * discarded, and the key read is the one written, so {@code [a} opens table {@code a} with only the missing bracket
   * reported.
   */
  private static boolean hasRecoveredKey(ParserRuleContext ctx, int endTokenType) {
    if (ctx.exception != null) {
      return true;
    }
    for (int i = 0; i < ctx.getChildCount(); i++) {
      ParseTree child = ctx.getChild(i);
      if (child instanceof ErrorNode) {
        // A token the parser inserted during error recovery is not in the input, so it has no index; any other error
        // node is input that the parser discarded to make the header match.
        Token symbol = ((ErrorNode) child).getSymbol();
        if (symbol.getTokenIndex() >= 0 || symbol.getType() != endTokenType) {
          return true;
        }
      } else if (hasRecognitionError(child)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasRecognitionError(ParseTree tree) {
    if (tree instanceof ErrorNode) {
      return true;
    }
    if (tree instanceof ParserRuleContext && ((ParserRuleContext) tree).exception != null) {
      return true;
    }
    for (int i = 0; i < tree.getChildCount(); i++) {
      if (hasRecognitionError(tree.getChild(i))) {
        return true;
      }
    }
    return false;
  }

  private void defineOpenTables() {
    openTables.forEach(LinkedTomlTable::define);
    openTables.clear();
  }
}
