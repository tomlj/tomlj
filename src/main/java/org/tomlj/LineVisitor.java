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

import static org.tomlj.TomlVersion.V0_4_0;

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;

final class LineVisitor extends TomlParserBaseVisitor<MutableTomlTable> {

  private final TomlVersion version;
  private final ErrorReporter errorReporter;
  private final MutableTomlTable rootTable;
  private MutableTomlTable currentTable;
  // The number of tables and arrays enclosing the entries of currentTable, not counting the root table. Starts at 0.
  private int currentDepth;
  private final Map<MutableTomlTable, TomlPosition> openTables;
  // The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
  // in the document.
  private final int maxNestingDepth;

  LineVisitor(TomlVersion version, ErrorReporter errorReporter, int maxNestingDepth) {
    this.version = version;
    this.errorReporter = errorReporter;
    this.rootTable = new MutableTomlTable(version, TomlPosition.positionAt(1, 1));
    this.currentTable = rootTable;
    this.openTables = new HashMap<>();
    this.maxNestingDepth = maxNestingDepth;
  }

  @Override
  public MutableTomlTable visitKeyval(TomlParser.KeyvalContext ctx) {
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
      // TOML 0.4.0 doesn't support dotted keys
      if (!version.after(V0_4_0) && path.size() > 1) {
        throw new TomlParseError("Dotted keys are not supported", new TomlPosition(keyContext));
      }
      Object value = valContext.accept(new ValueVisitor(version));
      if (value != null && !hasSyntaxError(ctx)) {
        if ((long) currentDepth + ctx.nesting > maxNestingDepth) {
          throw new TomlParseError(TomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx));
        }
        currentTable
            .set(path, value, new TomlPosition(ctx))
            .forEach(entry -> openTables.putIfAbsent(entry.getKey(), entry.getValue()));
      }
      return rootTable;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
      return rootTable;
    }
  }

  @Override
  public MutableTomlTable visitStandardTable(TomlParser.StandardTableContext ctx) {
    defineOpenTables();
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return rootTable;
    }
    List<String> path = keyContext.accept(new KeyVisitor(version));
    if (path == null) {
      return rootTable;
    }
    // The table named by the header's last key is enclosed by whatever its leading keys walk through.
    int depth = headerDepth(path);
    if (depth > maxNestingDepth) {
      errorReporter
          .reportError(new TomlParseError(TomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx)));
      return rootTable;
    }
    try {
      currentTable = rootTable.createTable(path, new TomlPosition(ctx));
      currentDepth = depth + 1;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
    }
    return rootTable;
  }

  @Override
  public MutableTomlTable visitArrayTable(TomlParser.ArrayTableContext ctx) {
    defineOpenTables();
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return rootTable;
    }
    List<String> path = keyContext.accept(new KeyVisitor(version));
    if (path == null) {
      return rootTable;
    }
    // The array named by the header's last key is enclosed by whatever its leading keys walk through, and its new
    // element table is enclosed by that array as well.
    int depth = headerDepth(path);
    if ((long) depth + 1 > maxNestingDepth) {
      errorReporter
          .reportError(new TomlParseError(TomlParser.nestingTooDeepMessage(maxNestingDepth), new TomlPosition(ctx)));
      return rootTable;
    }
    try {
      currentTable = rootTable.createTableArray(path, new TomlPosition(ctx));
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
   * {@code MutableTomlTable.ensureTable}'s {@code followTableArrays} behaviour: each leading key adds one level, and a
   * leading key that names a (non-empty) array of tables adds a second level, because the header walks into the last
   * table of that array rather than the array itself. A key that is absent, or that names anything else, stops the
   * walk, but every remaining leading key still adds its one level, since {@code createTable} /
   * {@code createTableArray} will report the real error for it.
   */
  private int headerDepth(List<String> path) {
    int depth = 0;
    MutableTomlTable table = rootTable;
    for (int i = 0; i < path.size() - 1; i++) {
      depth++;
      if (table == null) {
        continue;
      }
      Object value = table.get(Collections.singletonList(path.get(i)));
      if (value instanceof MutableTomlArray
          && ((MutableTomlArray) value).isTableArray()
          && !((MutableTomlArray) value).isEmpty()) {
        MutableTomlArray array = (MutableTomlArray) value;
        depth++;
        table = (MutableTomlTable) array.get(array.size() - 1);
      } else if (value instanceof MutableTomlTable) {
        table = (MutableTomlTable) value;
      } else {
        table = null;
      }
    }
    return depth;
  }

  @Override
  protected MutableTomlTable aggregateResult(MutableTomlTable aggregate, MutableTomlTable nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected MutableTomlTable defaultResult() {
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
    openTables.forEach(MutableTomlTable::define);
    openTables.clear();
  }
}
