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
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * An error strategy that recovers from unexpected input between expressions by skipping the rest of the line.
 *
 * <p>
 * Where a line cannot start an expression (e.g. {@code @}), or has input left over after one, ANTLR's default strategy
 * only resynchronizes if it can delete a single token, and otherwise abandons the document rule, discarding every later
 * line. In the document rule this strategy instead reports the unexpected token, skips to the end of the line and lets
 * parsing continue on the next line. Errors inside an expression are left to the default strategy, whose recovery
 * already stops at the end of the line.
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
    // Also resets the state the default strategy keeps for reporting what a later rule expected.
    super.sync(recognizer);
  }
}
