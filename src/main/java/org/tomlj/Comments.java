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

import org.tomlj.internal.TomlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Reads from the parse tree what each comment of a document documents.
 *
 * <p>
 * The grammar says where a comment was written: the comment ending a line sits before that line's newline, and a run of
 * whole comment lines is a {@code commentRun} that takes the newline ending its last line. Where an <em>element</em> is
 * a key/value pair, a table header or a value written in an array, that leaves three cases, decided by the nodes a
 * comment sits between rather than by any line number:
 *
 * <ul>
 * <li>A {@code commentRun} written directly before an element is the comment {@code ABOVE} it. Within brackets a run is
 * the last thing in a {@code lineBreak}, so the run a line break ends with is the comment above whatever the line break
 * precedes.</li>
 * <li>The comment ending the line an element ends on is the comment {@code AFTER} it: the comment written directly
 * after the element, or, within brackets, the one starting the {@code lineBreak} following the element or following the
 * comma that follows it.</li>
 * <li>Every other comment documents nothing and is unattached: a run separated from what follows it by a blank line or
 * a closing bracket, and the comment ending a line no element was written on.</li>
 * </ul>
 *
 * <p>
 * A comma is a separator rather than a line of its own, so it neither ends the line an element is written on nor starts
 * one: {@code 1, # c} trails the value before the comma, and a run above {@code , 2} is the comment above the value
 * after it.
 *
 * <p>
 * An unattached comment belongs to a container rather than to an entry: within brackets to the array or inline table
 * itself, and between table headers to the container the calling visitor chooses by whether the run is glued to the
 * line above it.
 */
final class Comments {

  private Comments() {}

  /**
   * The comment above whatever follows a node.
   *
   * @param previous The node written before an element, or {@code null} if it is the first thing written.
   * @return The comment above the element, or {@code null} if there is none.
   */
  @Nullable
  static TomlComment above(@Nullable ParseTree previous) {
    TomlParser.CommentRunContext run = runAbove(previous);
    return (run != null) ? of(run, TomlComment.Placement.ABOVE) : null;
  }

  /**
   * The comment ending the line an element ends on.
   *
   * @param next The node written after the element, or {@code null} if it is the last thing written.
   * @return The comment after the element, or {@code null} if there is none.
   */
  @Nullable
  static TomlComment after(@Nullable ParseTree next) {
    TerminalNode comment = lineEndComment(next);
    return (comment != null) ? of(comment, TomlComment.Placement.AFTER) : null;
  }

  /**
   * The comment above the element at an index of a flattened array or inline table.
   *
   * @param nodes The flattened nodes.
   * @param index The index of the element.
   * @return The comment above the element, or {@code null} if there is none.
   */
  @Nullable
  static TomlComment above(List<ParseTree> nodes, int index) {
    return above(before(nodes, index));
  }

  /**
   * The run above the element at an index of a flattened array or inline table, as written.
   *
   * <p>
   * The same run {@link #above(List, int)} records, given back as the node it was read from, for a caller that needs
   * where it was written rather than what it says.
   *
   * @param nodes The flattened nodes.
   * @param index The index of the element.
   * @return The run above the element, or {@code null} if there is none.
   */
  static TomlParser.@Nullable CommentRunContext runAbove(List<ParseTree> nodes, int index) {
    return runAbove(before(nodes, index));
  }

  /**
   * The comment after the element at an index of a flattened array or inline table.
   *
   * @param nodes The flattened nodes.
   * @param index The index of the element.
   * @return The comment after the element, or {@code null} if there is none.
   */
  @Nullable
  static TomlComment after(List<ParseTree> nodes, int index) {
    TerminalNode comment = commentAfter(nodes, index);
    return (comment != null) ? of(comment, TomlComment.Placement.AFTER) : null;
  }

  /**
   * The comment after the element at an index of a flattened array or inline table, as written.
   *
   * <p>
   * The same comment {@link #after(List, int)} records, given back as the node it was read from, for a caller that
   * needs where it was written rather than what it says.
   *
   * @param nodes The flattened nodes.
   * @param index The index of the element.
   * @return The comment after the element, or {@code null} if there is none.
   */
  @Nullable
  static TerminalNode commentAfter(List<ParseTree> nodes, int index) {
    ParseTree next = at(nodes, index + 1);
    // A comma does not end the line, so what follows it is still written on the element's line.
    return lineEndComment(isComma(next) ? at(nodes, index + 2) : next);
  }

  /**
   * The comments of a line break that document nothing, in the order they were written.
   *
   * <p>
   * Everything the line break holds except the comment ending the line of the element before it, which is the comment
   * after that element, and the run it ends with when an element follows it, which is the comment above that element.
   *
   * @param nodes The flattened nodes of the array or inline table the line break was written in.
   * @param index The index of the line break.
   * @return Each comment as the node it was written as: a {@code Comment} terminal for one ending a line, a
   *         {@code commentRun} for a run of whole lines.
   */
  static List<ParseTree> unattached(List<ParseTree> nodes, int index) {
    TomlParser.LineBreakContext lineBreak = (TomlParser.LineBreakContext) nodes.get(index);
    List<ParseTree> comments = new ArrayList<>();
    TerminalNode comment = lineEndComment(lineBreak);
    if (comment != null && !endsElementLine(nodes, index)) {
      comments.add(comment);
    }
    List<TomlParser.CommentRunContext> runs = lineBreak.commentRun();
    int count = runs.size();
    if (count > 0 && startsElementLine(nodes, index) && lastChild(lineBreak) instanceof TomlParser.CommentRunContext) {
      // The run the line break ends with is the comment above the element that follows the line break.
      count--;
    }
    for (int i = 0; i < count; ++i) {
      comments.add(runs.get(i));
    }
    return comments;
  }

  /**
   * Whether a line break holds any comment.
   *
   * @param lineBreak The line break.
   * @return {@code true} if the line break holds a comment.
   */
  static boolean holdsComments(TomlParser.LineBreakContext lineBreak) {
    return lineEndComment(lineBreak) != null || !lineBreak.commentRun().isEmpty();
  }

  /**
   * Whether the line above a comment run of the document holds anything, which decides the container an unattached run
   * belongs to.
   *
   * @param previous The node written before the run, or {@code null} if it is the first thing written.
   * @param beforePrevious The node written before that, or {@code null} if there is none.
   * @return {@code true} if the run is glued to the line above it.
   */
  static boolean glued(@Nullable ParseTree previous, @Nullable ParseTree beforePrevious) {
    // A run takes its own newlines, so the node before it is the newline ending the line above, and that line holds
    // something if what precedes its newline is an expression or the comment written after one.
    return isToken(previous, TomlParser.NewLine)
        && (beforePrevious instanceof TomlParser.ExpressionContext || isToken(beforePrevious, TomlParser.Comment));
  }

  /**
   * The children of an array or inline table, with the rules that group its values expanded in place.
   *
   * <p>
   * The grammar wraps the values of an array in {@code arrayValues} and each value in {@code arrayValue}, so that the
   * line breaks written between them are shared between the rules. Flattening those wrappers away leaves one sequence
   * in the order the array was written, where each value, line break and comma sits directly beside what surrounds it.
   *
   * @param ctx An array or inline table.
   * @return The flattened nodes.
   */
  static List<ParseTree> flatten(ParserRuleContext ctx) {
    List<ParseTree> nodes = new ArrayList<>(ctx.getChildCount());
    flattenInto(ctx, nodes);
    return nodes;
  }

  /**
   * Record a comment run.
   *
   * @param run The run.
   * @param placement Where it sits relative to what it documents, or {@code null} if it documents nothing.
   * @return The comment.
   */
  static TomlComment of(TomlParser.CommentRunContext run, TomlComment.@Nullable Placement placement) {
    List<TerminalNode> comments = run.Comment();
    List<Token> tokens = new ArrayList<>(comments.size());
    for (TerminalNode comment : comments) {
      tokens.add(comment.getSymbol());
    }
    return TomlComment.of(tokens, placement);
  }

  /**
   * Record a comment written on one line.
   *
   * @param comment The comment.
   * @param placement Where it sits relative to what it documents, or {@code null} if it documents nothing.
   * @return The comment.
   */
  static TomlComment of(TerminalNode comment, TomlComment.@Nullable Placement placement) {
    return TomlComment.of(Collections.singletonList(comment.getSymbol()), placement);
  }

  /**
   * Record a comment that documents nothing, written as one of the nodes {@link #unattached} gives back.
   *
   * @param node A {@code Comment} terminal or a {@code commentRun}.
   * @return The comment.
   */
  static TomlComment ofUnattached(ParseTree node) {
    return (node instanceof TerminalNode) ? of((TerminalNode) node, null)
        : of((TomlParser.CommentRunContext) node, null);
  }

  private static void flattenInto(ParseTree node, List<ParseTree> nodes) {
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      ParseTree child = node.getChild(i);
      if (isGrouping(child)) {
        flattenInto(child, nodes);
      } else {
        nodes.add(child);
      }
    }
  }

  private static boolean isGrouping(ParseTree node) {
    return node instanceof TomlParser.ArrayValuesContext
        || node instanceof TomlParser.ArrayValueContext
        || node instanceof TomlParser.InlineTableValuesContext
        || node instanceof TomlParser.InlineTableValueContext;
  }

  // The node an element's comment run would be written as, which is the run itself within brackets only when the line
  // break it ends is the one before the element.
  private static TomlParser.@Nullable CommentRunContext runAbove(@Nullable ParseTree previous) {
    // Within brackets the run above an element is the last thing written in the line break before it.
    ParseTree run = (previous instanceof TomlParser.LineBreakContext) ? lastChild(previous) : previous;
    return (run instanceof TomlParser.CommentRunContext) ? (TomlParser.CommentRunContext) run : null;
  }

  // The node written before the element at an index, skipping a comma, which starts no line of its own.
  @Nullable
  private static ParseTree before(List<ParseTree> nodes, int index) {
    ParseTree previous = at(nodes, index - 1);
    // A comma starting the element's line does not separate the element from the run above it.
    return isComma(previous) ? at(nodes, index - 2) : previous;
  }

  @Nullable
  private static ParseTree lastChild(@Nullable ParseTree node) {
    int childCount = (node != null) ? node.getChildCount() : 0;
    return (childCount > 0) ? node.getChild(childCount - 1) : null;
  }

  // The comment ending the first line of a line break, or the comment written after an expression on a line of the
  // document.
  @Nullable
  private static TerminalNode lineEndComment(@Nullable ParseTree node) {
    ParseTree comment =
        (node instanceof TomlParser.LineBreakContext) ? ((TomlParser.LineBreakContext) node).Comment() : node;
    return isToken(comment, TomlParser.Comment) ? (TerminalNode) comment : null;
  }

  // Whether the line a line break ends is the line an element was written on, the line break following that element
  // directly or following the comma that does.
  private static boolean endsElementLine(List<ParseTree> nodes, int index) {
    ParseTree previous = at(nodes, index - 1);
    return isElement(isComma(previous) ? at(nodes, index - 2) : previous);
  }

  // Whether the line after a line break is the line an element is written on, the element following the line break
  // directly or following the comma that does.
  private static boolean startsElementLine(List<ParseTree> nodes, int index) {
    ParseTree next = at(nodes, index + 1);
    return isElement(isComma(next) ? at(nodes, index + 2) : next);
  }

  private static boolean isElement(@Nullable ParseTree node) {
    return node instanceof TomlParser.ValContext || node instanceof TomlParser.KeyvalContext;
  }

  private static boolean isComma(@Nullable ParseTree node) {
    return isToken(node, TomlParser.Comma);
  }
}
