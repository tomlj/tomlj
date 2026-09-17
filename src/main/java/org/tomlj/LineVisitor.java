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
import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;

final class LineVisitor extends TomlParserBaseVisitor<LinkedTomlTable> {

  private final TomlVersion version;
  private final ErrorReporter errorReporter;
  private final LinkedTomlTable rootTable;
  private LinkedTomlTable currentTable;
  // The comments documenting the expression being visited, which the document rule reads from the tree around it.
  private List<TomlComment> attached = Collections.emptyList();
  // The number of tables and arrays enclosing the entries of currentTable, not counting the root table. Starts at 0.
  private int currentDepth;
  private final Map<LinkedTomlTable, TomlPosition> openTables;
  // The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
  // in the document.
  private final int maxNestingDepth;

  LineVisitor(TomlVersion version, ErrorReporter errorReporter, int maxNestingDepth) {
    this.version = version;
    this.errorReporter = errorReporter;
    this.rootTable = new LinkedTomlTable(TomlPosition.positionAt(1, 1));
    this.currentTable = rootTable;
    this.openTables = new HashMap<>();
    this.maxNestingDepth = maxNestingDepth;
  }

  /**
   * Visit the lines of the document, handing each expression the comments written around it.
   *
   * <p>
   * Walked here rather than left to {@link #visitChildren}, because what a comment is attached to depends on what the
   * tree holds beside it: the run before an expression and the comment ending its line are attached to it, and every
   * other run is unattached and belongs to a container. A run glued to the line above it belongs to the container open
   * where it was written, which is known when the run is reached. A run separated by a blank line from what precedes it
   * belongs to the container of the expression that follows, which is not known yet, so such runs are held until that
   * expression is reached.
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
          separated.forEach(container::addComment);
          separated = null;
        }
        attached = TomlComment.attached(Comments.above(previous), Comments.after(next));
        child.accept(this);
      } else if (child instanceof TomlParser.CommentRunContext && !(next instanceof TomlParser.ExpressionContext)) {
        // A run directly above an expression is handed to it when it is reached; this one documents nothing.
        TomlComment comment = Comments.of((TomlParser.CommentRunContext) child, null);
        if (Comments.glued(previous, beforePrevious)) {
          currentTable.addComment(comment);
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
      separated.forEach(rootTable::addComment);
    }
    return rootTable;
  }

  @Override
  public LinkedTomlTable visitKeyval(TomlParser.KeyvalContext ctx) {
    // A key/value pair is written inside whatever section is open, and so is any comment written around it.
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
        currentTable
            .setParsed(path, Value.of(value, new TomlPosition(valContext)), new TomlPosition(ctx), comments)
            .forEach(entry -> openTables.putIfAbsent(entry.getKey(), entry.getValue()));
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
      // The table named by the header's last key is enclosed by whatever its leading keys walk through.
      int depth = headerDepth(path);
      if (depth > maxNestingDepth) {
        throw new TomlParseError(AbstractTomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
      }
      currentTable = rootTable.createParsedTable(path, new TomlPosition(ctx), comments);
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
      // The array named by the header's last key is enclosed by whatever its leading keys walk through, and its new
      // element table is enclosed by that array as well.
      int depth = headerDepth(path);
      if ((long) depth + 1 > maxNestingDepth) {
        throw new TomlParseError(AbstractTomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
      }
      currentTable = rootTable.createParsedTableArray(path, new TomlPosition(ctx), comments);
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
   * A header the parser completed by conjuring its closing bracket is the exception: nothing was discarded, and the key
   * read is the one written, so {@code [a} opens table {@code a} with only the missing bracket reported.
   */
  private static boolean hasRecoveredKey(ParserRuleContext ctx, int endTokenType) {
    if (ctx.exception != null) {
      return true;
    }
    for (int i = 0; i < ctx.getChildCount(); i++) {
      ParseTree child = ctx.getChild(i);
      if (child instanceof ErrorNode) {
        // A token the parser conjured is not in the input, so it has no index; any other error node is input that the
        // parser discarded to make the header match.
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
