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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A comment in a TOML document, either attached to the entry it documents or unattached.
 *
 * <p>
 * A comment attached to an entry is either the run of comment lines directly above it ({@link Placement#ABOVE}) or the
 * comment on its line ({@link Placement#AFTER}); every other comment is unattached, and belongs to the table or array
 * it was written in. An entry has at most one comment of each placement.
 */
public final class TomlComment implements TomlElement {

  /**
   * Where a comment sits relative to the entry it documents.
   */
  public enum Placement {
    /**
     * A run of comment lines written directly above the entry it documents.
     */
    ABOVE,
    /**
     * The comment written on the same line as the entry, after it.
     */
    AFTER
  }

  // Each line as written after the '#', kept verbatim so that a writer can reproduce it; lines() strips one leading
  // space.
  private final List<String> rawLines;
  private final TomlPosition position;
  private final @Nullable Placement placement;

  /**
   * Record a comment from the tokens the lexer matched for it.
   *
   * @param tokens The comment tokens, one per line, on consecutive lines of the document.
   * @param placement Where the comment sits relative to what it documents, or {@code null} if it documents nothing.
   * @return A comment.
   */
  static TomlComment of(List<Token> tokens, @Nullable Placement placement) {
    assert !tokens.isEmpty();
    List<String> rawLines = new ArrayList<>(tokens.size());
    for (Token token : tokens) {
      // Drop the '#' that opens the line.
      rawLines.add(token.getText().substring(1));
    }
    Token first = tokens.get(0);
    TomlPosition position = TomlPosition.positionAt(first.getLine(), first.getCharPositionInLine() + 1);
    return new TomlComment(rawLines, position, placement);
  }

  /**
   * Collect the comments attached to one expression, in the order they were written.
   *
   * @param above The run written above the expression, or {@code null} if there is none.
   * @param after The comment trailing the expression, or {@code null} if there is none.
   * @return The comments, as an unmodifiable list of at most two.
   */
  static List<TomlComment> attached(@Nullable TomlComment above, @Nullable TomlComment after) {
    if (above == null && after == null) {
      return Collections.emptyList();
    }
    if (above == null) {
      return Collections.singletonList(after);
    }
    if (after == null) {
      return Collections.singletonList(above);
    }
    return Collections.unmodifiableList(Arrays.asList(above, after));
  }

  private TomlComment(List<String> rawLines, TomlPosition position, @Nullable Placement placement) {
    this.rawLines = rawLines;
    this.position = position;
    this.placement = placement;
  }

  /**
   * The text of each line, one entry per line of the run, in document order.
   *
   * <p>
   * Each entry is the text after the {@code #}, with one leading space removed if there is one, so {@code # foo} and
   * {@code #foo} both give {@code foo}, while a line written with two spaces keeps its second. A bare {@code #} gives
   * an empty string. No entry contains a newline.
   *
   * <p>
   * An attached {@link Placement#AFTER} comment has exactly one line.
   *
   * @return The text of each line, in document order. Unmodifiable.
   */
  public List<String> lines() {
    List<String> lines = new ArrayList<>(rawLines.size());
    for (String rawLine : rawLines) {
      lines.add(rawLine.startsWith(" ") ? rawLine.substring(1) : rawLine);
    }
    return Collections.unmodifiableList(lines);
  }

  /**
   * The lines of this comment, joined with {@code \n}.
   *
   * @return The lines of this comment, joined with {@code \n}.
   */
  public String text() {
    return String.join("\n", lines());
  }

  /**
   * The position of the {@code #} opening the first line of this comment.
   *
   * <p>
   * The lines of a run are contiguous, so line {@code i} of the run is on line {@code position().line() + i}.
   *
   * @return The position of the {@code #} opening the first line, or {@code null} if this comment was not read from a
   *         document.
   */
  @Override
  @Nullable
  public TomlPosition position() {
    return position;
  }

  /**
   * Where this comment sits relative to the entry it documents.
   *
   * @return {@link Placement#ABOVE} or {@link Placement#AFTER} for an attached comment, or {@code null} for an
   *         unattached one.
   */
  @Nullable
  public Placement placement() {
    return placement;
  }

  @Override
  public String toString() {
    return "TomlComment{" + text() + ", " + placement + " at " + position + "}";
  }
}
