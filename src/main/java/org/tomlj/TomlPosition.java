/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * A position in an input document.
 */
public final class TomlPosition {
  private final int line;
  private final int column;

  /**
   * Create a position.
   *
   * @param line The line.
   * @param column The column.
   * @return A position.
   */
  public static TomlPosition positionAt(int line, int column) {
    if (line < 1) {
      throw new IllegalArgumentException("line must be >= 1");
    }
    if (column < 1) {
      throw new IllegalArgumentException("column must be >= 1");
    }
    return new TomlPosition(line, column);
  }

  private TomlPosition(int line, int column) {
    this.line = line;
    this.column = column;
  }

  TomlPosition(ParserRuleContext ctx) {
    this(ctx, 0);
  }

  TomlPosition(ParserRuleContext ctx, int offset) {
    Token token = ctx.getStart();
    this.line = token.getLine();
    this.column = token.getCharPositionInLine() + 1 + offset;
  }

  /**
   * The line number.
   *
   * <p>
   * The first line of the document is line 1.
   *
   * @return The line number (1..).
   */
  public int line() {
    return line;
  }

  /**
   * The column number.
   *
   * <p>
   * The first column of the document is column 1.
   * 
   * @return The column number (1..).
   */
  public int column() {
    return column;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlPosition)) {
      return false;
    }
    TomlPosition other = (TomlPosition) obj;
    return this.line == other.line && this.column == other.column;
  }

  @Override
  public int hashCode() {
    return 31 * line + column;
  }

  @Override
  public String toString() {
    return "line " + line + ", column " + column;
  }
}
