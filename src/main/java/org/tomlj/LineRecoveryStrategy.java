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

import org.tomlj.internal.TomlParser;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * An error strategy that recovers from unexpected input between expressions by skipping the rest of the line.
 *
 * <p>
 * Where a line cannot start an expression (e.g. {@code @}), or has input left over after one, ANTLR's default strategy
 * only resynchronizes if it can delete a single token, and otherwise abandons the document rule, discarding every later
 * line. In the document rule this strategy instead reports the unexpected token, skips to the end of the line and lets
 * parsing continue on the next line. Errors inside an expression are left to the default strategy, whose recovery
 * already stops at the end of the line, except within an array or inline table, where a value may span lines and the
 * default strategy would otherwise carry the value on over every line that follows.
 */
final class LineRecoveryStrategy extends DefaultErrorStrategy {

  private static final IntervalSet LINE_END = IntervalSet.of(TomlParser.NewLine);

  @Override
  public void sync(Parser recognizer) {
    if (recognizer.getContext() instanceof TomlParser.TomlContext
        && !recognizer.getExpectedTokens().contains(recognizer.getInputStream().LA(1))) {
      // Reports nothing if already recovering, e.g. after an expression whose own recovery stopped before the line end.
      reportUnwantedToken(recognizer);
      consumeUntil(recognizer, LINE_END);
      return;
    }
    if (endsUnterminatedValue(recognizer)) {
      // An array or inline table may hold newlines between its elements, so the default strategy treats a line it
      // cannot parse as input to skip within the value, and carries on matching the lines after it - to the end of the
      // document, since nothing closes the value. A line that cannot continue the value ends it instead: the rule
      // reports the line's first token and recovers, which stops at the end of that line, leaving the document rule to
      // parse the lines that follow.
      throw new InputMismatchException(recognizer);
    }
    // Also resets the state the default strategy keeps for reporting what a later rule expected.
    super.sync(recognizer);
  }

  /**
   * Check whether the parser is inside an array or inline table, at the start of a line that cannot continue it.
   */
  private boolean endsUnterminatedValue(Parser recognizer) {
    if (inErrorRecoveryMode(recognizer)) {
      return false;
    }
    Token previous = recognizer.getInputStream().LT(-1);
    if (previous == null || previous.getType() != TomlParser.NewLine) {
      return false;
    }
    boolean inValue = false;
    for (RuleContext context = recognizer.getContext(); context != null; context = context.parent) {
      if (context instanceof TomlParser.ArrayContext || context instanceof TomlParser.InlineTableContext) {
        inValue = true;
        break;
      }
    }
    // Computing what the parser expects walks the rule invocation stack, so it is left until last.
    return inValue && !recognizer.getExpectedTokens().contains(recognizer.getInputStream().LA(1));
  }

  @Override
  protected Token singleTokenDeletion(Parser recognizer) {
    // A run of string characters outside a string (e.g. the rest of a line after a broken string) is a single token,
    // but it is not the single stray token that deletion is meant for: deleting it lets the parser carry on as though
    // the line were whole, e.g. still inside an inline table whose closing brace was in the run, and fail on the lines
    // that follow. Recover as the default strategy does from more than one unexpected token.
    Token token = recognizer.getInputStream().LT(1);
    if (token.getType() == TomlParser.StringChars) {
      String text = token.getText();
      if (text.codePointCount(0, text.length()) > 1) {
        return null;
      }
    }
    return super.singleTokenDeletion(recognizer);
  }
}
