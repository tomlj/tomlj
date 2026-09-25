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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * A {@link TomlEntry} whose value and attached comments can be edited in place.
 *
 * <p>
 * An entry is obtained from the table or array it belongs to, with {@link MutableTomlTable#entry} or
 * {@link MutableTomlArray#entry}. {@link MutableTomlTable} and {@link MutableTomlArray} also offer a shortcut for each
 * comment setter and remover here, taking the key or index of the entry to change.
 *
 * <p>
 * Not safe for use from multiple threads without external synchronization.
 */
public interface MutableTomlEntry extends TomlEntry {

  /**
   * Replace the value this entry holds, as {@link MutableTomlTable#set} or {@link MutableTomlArray#set} would.
   *
   * <p>
   * Keeps this entry's position, its place in the container's sequence and its attached comments.
   *
   * @param value The replacement value.
   * @return The value this entry held before, as {@link TomlValue#get()} would give it.
   * @throws NullPointerException If {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code value} cannot be converted to a TOML value, or cannot be written as
   *         TOML.
   */
  Object setValue(Object value);

  /**
   * Set the run of comment lines written above this entry, replacing any already there.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it; a line holding a newline is
   *        split there.
   * @return This entry.
   * @throws NullPointerException If a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlEntry setCommentAbove(String... lines) {
    return setCommentAbove(Arrays.asList(lines));
  }

  /**
   * Set the run of comment lines written above this entry, replacing any already there, as
   * {@link #setComment(String, TomlComment.Placement)} does.
   *
   * <p>
   * The lines are joined with {@code '\n'}, so a {@code '\n'} within a line starts a new line, as it does in
   * {@link #setComment(String, TomlComment.Placement)}.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This entry.
   * @throws NullPointerException If {@code lines}, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlEntry setCommentAbove(List<String> lines) {
    requireNonNull(lines);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("A comment needs at least one line");
    }
    lines.forEach(Objects::requireNonNull);
    return setComment(String.join("\n", lines), TomlComment.Placement.ABOVE);
  }

  /**
   * Set the comment written on this entry's line, replacing any already there, as
   * {@link #setComment(String, TomlComment.Placement)} does.
   *
   * @param text The comment text, as {@link TomlComment#lines()} would return its one line.
   * @return This entry.
   * @throws NullPointerException If {@code text} is {@code null}.
   * @throws IllegalArgumentException If {@code text} cannot be written as a TOML comment.
   */
  default MutableTomlEntry setCommentAfter(String text) {
    return setComment(text, TomlComment.Placement.AFTER);
  }

  /**
   * Set an attached comment from its text, replacing any comment already at {@code placement}.
   *
   * <p>
   * For {@link TomlComment.Placement#ABOVE}, {@code text} is the lines of the run joined with {@code '\n'}, as
   * {@link TomlComment#text()} gives it, so {@code "a\nb"} is a run of two lines and {@code ""} is a run of one empty
   * line; for {@link TomlComment.Placement#AFTER}, {@code text} is the one line, so a {@code '\n'} in it is rejected. A
   * line cannot contain a control character other than tab, or a lone surrogate.
   *
   * @param text The comment's text, as {@link TomlComment#text()} would return it.
   * @param placement Where the comment sits relative to this entry, {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This entry.
   * @throws NullPointerException If {@code text} or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}, or a line cannot
   *         be written as a TOML comment.
   */
  MutableTomlEntry setComment(String text, TomlComment.Placement placement);

  /**
   * Set an attached comment, replacing any comment already at its placement.
   *
   * <p>
   * This is how a comment is copied from one entry to another, across documents too: the comment's text is kept, but
   * the copy stored here has no position, regardless of whether {@code comment} had one.
   *
   * @param comment The comment, with {@link TomlComment#placement()} either {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This entry.
   * @throws NullPointerException If {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is unattached.
   */
  MutableTomlEntry setComment(TomlComment comment);

  /**
   * Remove the run of comment lines written above this entry.
   *
   * <p>
   * Removing a comment that is not there changes nothing and is not a modification.
   *
   * @return This entry.
   */
  MutableTomlEntry removeCommentAbove();

  /**
   * Remove the comment written on this entry's line.
   *
   * <p>
   * Removing a comment that is not there changes nothing and is not a modification.
   *
   * @return This entry.
   */
  MutableTomlEntry removeCommentAfter();

  /**
   * Remove the attached comment at a placement.
   *
   * <p>
   * Removing a comment that is not there changes nothing and is not a modification.
   *
   * @param placement Which attached comment to remove, {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This entry.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}.
   */
  MutableTomlEntry removeComment(TomlComment.Placement placement);

  /**
   * Whether this entry was changed through the editing API.
   *
   * @return {@code true} if this entry was added, its value replaced, an attached comment set or removed, or the table
   *         or array it holds changed.
   */
  boolean isModified();
}
