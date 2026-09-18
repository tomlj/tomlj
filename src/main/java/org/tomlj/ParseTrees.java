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

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Helpers for reading the parse tree that the generated parser builds. */
final class ParseTrees {

  private ParseTrees() {}

  /**
   * The node at an index of a list of nodes.
   *
   * @param nodes The nodes.
   * @param index The index, which may be outside the list.
   * @return The node at the index, or {@code null} if the index is outside the list.
   */
  @Nullable
  static ParseTree at(List<ParseTree> nodes, int index) {
    return (index >= 0 && index < nodes.size()) ? nodes.get(index) : null;
  }

  /**
   * Whether a node is a token of a given type.
   *
   * @param node The node, or {@code null}.
   * @param type The token type.
   * @return {@code true} if the node is a terminal holding a token of that type.
   */
  static boolean isToken(@Nullable ParseTree node, int type) {
    return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == type;
  }

  /**
   * The text matched by a rule whose body is a single token.
   *
   * <p>
   * {@link ParserRuleContext#getText()} joins the text of every child through a {@link StringBuilder}, which for a rule
   * holding one token copies that token's text to no purpose. Error recovery can leave such a rule holding something
   * other than the one token, so the shape is checked rather than assumed; anything else falls back to
   * {@code getText()}, which for a lone child returns that child's text unchanged.
   *
   * @param ctx The context of a rule whose body is a single token.
   * @return The text the rule matched.
   */
  static String singleTokenText(ParserRuleContext ctx) {
    if (ctx.getChildCount() == 1) {
      ParseTree child = ctx.getChild(0);
      if (child instanceof TerminalNode) {
        return child.getText();
      }
    }
    return ctx.getText();
  }
}
