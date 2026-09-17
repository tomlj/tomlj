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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A comment in a TOML document, either attached to an entry or unattached.
 *
 * <p>
 * A comment attached to an entry is either the run of comment lines directly above it ({@link Placement#ABOVE}) or the
 * comment on its line ({@link Placement#AFTER}); every other comment is unattached, and belongs to the table or array
 * it was written in. An entry has at most one comment of each placement.
 */
public final class TomlComment implements TomlElement {

  /**
   * Where a comment sits relative to the entry it is attached to.
   */
  public enum Placement {
    /**
     * A run of comment lines written directly above the entry it is attached to.
     */
    ABOVE,
    /**
     * The comment written on the same line as the entry, after it.
     */
    AFTER,
    /**
     * A comment attached to no entry: a run that is not directly above one, or a comment on a line with none. It
     * belongs to the table or array it was written in.
     */
    UNATTACHED
  }

  // Each line as written after the '#', kept verbatim so that a writer can reproduce it; lines() strips one leading
  // space.
  private final List<String> rawLines;
  private final @Nullable TomlPosition position;
  private final Placement placement;

  /**
   * Record a comment from the tokens the lexer matched for it.
   *
   * @param tokens The comment tokens, one per line, on consecutive lines of the document.
   * @param placement Where the comment sits relative to the entry it is attached to, or {@link Placement#UNATTACHED}.
   * @return A comment.
   */
  static TomlComment of(List<Token> tokens, Placement placement) {
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
   * List the arguments that are not {@code null}, in argument order.
   *
   * @param first The first comment, or {@code null}.
   * @param second The second comment, or {@code null}.
   * @return The arguments that are not {@code null}, as an unmodifiable list of at most two.
   */
  static List<TomlComment> withoutNulls(@Nullable TomlComment first, @Nullable TomlComment second) {
    if (first == null && second == null) {
      return Collections.emptyList();
    }
    if (first == null) {
      return Collections.singletonList(second);
    }
    if (second == null) {
      return Collections.singletonList(first);
    }
    return Collections.unmodifiableList(Arrays.asList(first, second));
  }

  /**
   * Copy each comment without its position, as {@link #withoutPosition()} gives it, keeping the ABOVE comment before
   * the AFTER one.
   *
   * @param comments Attached comments, at most one of each placement, as {@link TomlEntry#comments()} lists them.
   * @return The copies, as {@link #withoutNulls} lists them.
   * @throws IllegalArgumentException If a comment is unattached.
   */
  static List<TomlComment> copyWithoutPositions(List<TomlComment> comments) {
    TomlComment above = null;
    TomlComment after = null;
    for (TomlComment comment : comments) {
      if (comment.placement == Placement.ABOVE) {
        above = comment.withoutPosition();
      } else if (comment.placement == Placement.AFTER) {
        after = comment.withoutPosition();
      } else {
        throw new IllegalArgumentException("comment must have a placement of ABOVE or AFTER");
      }
    }
    return withoutNulls(above, after);
  }

  /**
   * Check that a placement is one an entry holds a comment at.
   *
   * @param placement The placement.
   * @return {@code placement}.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link Placement#UNATTACHED}.
   */
  static Placement requireAttached(Placement placement) {
    requireNonNull(placement);
    if (placement == Placement.UNATTACHED) {
      throw new IllegalArgumentException("placement must be ABOVE or AFTER");
    }
    return placement;
  }

  private TomlComment(List<String> rawLines, @Nullable TomlPosition position, Placement placement) {
    this.rawLines = rawLines;
    this.position = position;
    this.placement = requireNonNull(placement);
  }

  /**
   * Build a comment from lines of text, as the editing API accepts them.
   *
   * <p>
   * A line holding a newline is split there, so the text of a whole run can be given as one line. Each line is then
   * validated against the grammar's comment rule: a control character other than tab, DEL, or a lone surrogate is
   * rejected, since none of those can be written as part of a comment; a surrogate pair is one character and is
   * accepted.
   *
   * @param lines The text of each line, as {@link #lines()} would return it, or holding newlines where the run breaks.
   * @param placement Where the comment sits relative to the entry it is attached to, or {@link Placement#UNATTACHED}.
   * @return A comment with no position.
   * @throws NullPointerException If {@code lines}, a line, or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, a line cannot be written as a TOML comment, or
   *         {@code placement} is {@link Placement#AFTER} and {@code lines} has more than one line once split.
   */
  static TomlComment ofLines(List<String> lines, Placement placement) {
    requireNonNull(lines);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("A comment needs at least one line");
    }
    List<String> rawLines = new ArrayList<>(lines.size());
    for (String text : lines) {
      requireNonNull(text);
      for (String line : text.split("\n", -1)) {
        validateLine(line);
        rawLines.add(line.isEmpty() ? "" : (" " + line));
      }
    }
    if (placement == Placement.AFTER && rawLines.size() > 1) {
      throw new IllegalArgumentException("A comment after an entry has one line");
    }
    return new TomlComment(rawLines, null, placement);
  }

  // Rejects a character the lexer's comment rule cannot match: a control character other than tab, DEL, or a lone
  // surrogate. A surrogate pair decodes to one code point outside this range, so it passes.
  private static void validateLine(String line) {
    int length = line.length();
    for (int i = 0; i < length;) {
      int codePoint = line.codePointAt(i);
      if (isRejected(codePoint)) {
        throw new IllegalArgumentException("A comment cannot contain " + String.format("U+%04X", codePoint));
      }
      i += Character.charCount(codePoint);
    }
  }

  private static boolean isRejected(int codePoint) {
    return codePoint <= 0x08
        || (codePoint >= 0x0A && codePoint <= 0x1F)
        || codePoint == 0x7F
        || (codePoint >= 0xD800 && codePoint <= 0xDFFF);
  }

  /**
   * This comment without its position: itself if it has none, otherwise a copy with the same lines and placement.
   *
   * <p>
   * The editing API stores a fresh entity, with no position, when it attaches a comment read from a document.
   *
   * @return A comment with the same lines and placement, and no position.
   */
  TomlComment withoutPosition() {
    return (position == null) ? this : new TomlComment(rawLines, null, placement);
  }

  /**
   * Check that this comment is unattached.
   *
   * @return This comment.
   * @throws IllegalArgumentException If this comment is attached.
   */
  TomlComment requireUnattached() {
    if (placement != Placement.UNATTACHED) {
      throw new IllegalArgumentException("comment must be unattached");
    }
    return this;
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
   * Where this comment sits relative to the entry it is attached to.
   *
   * @return {@link Placement#ABOVE} or {@link Placement#AFTER} for an attached comment, or {@link Placement#UNATTACHED}
   *         for an unattached one.
   */
  public Placement placement() {
    return placement;
  }

  @Override
  public String toString() {
    return "TomlComment{" + text() + ", " + placement + " at " + position + "}";
  }
}
