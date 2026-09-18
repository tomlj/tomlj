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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * A {@link TomlTable} whose values can be edited in place.
 *
 * <p>
 * A value set through this interface is any value a {@link TomlTable} can hold. An {@code Integer}, {@code Short} or
 * {@code Byte} is widened to a {@code Long} and a {@code Float} to a {@code Double}; a {@code Map} with {@code String}
 * keys becomes a table and a {@code Collection} an array, their values converted the same way. {@code null} throws a
 * {@link NullPointerException} and anything else an {@link IllegalArgumentException}.
 *
 * <p>
 * A key or a {@link String} value must not contain an unpaired surrogate, an {@link java.time.OffsetDateTime}'s offset
 * must be a whole number of minutes, and a {@link java.time.LocalDate}, {@link java.time.LocalDateTime} or
 * {@link java.time.OffsetDateTime} must have a year between 0 and 9999, since none of these can be written as TOML.
 *
 * <p>
 * A {@link TomlTable} or {@link TomlArray} stored as a value is stored as a deep copy, so later changes to the original
 * are not seen; the stored copy is edited through the getters that return it.
 *
 * <p>
 * An entry's attached comments are edited through its {@link MutableTomlKeyValue} (or {@link MutableTomlEntry})
 * obtained from {@link #entry}, or through the shortcuts here. An unattached comment is added after the last element
 * with {@link #addComment} and removed with {@link #removeComment}; it can also be inserted before or after an entry
 * with {@link #insertCommentBefore} or {@link #insertCommentAfter}. An entry or a comment can also be inserted before
 * or after any element of this table's sequence, as {@link #elements()} returns it, with {@link #insertBefore},
 * {@link #insertAfter}, {@link #insertCommentBefore}, or {@link #insertCommentAfter}.
 *
 * <p>
 * A table is written back out with {@link #toToml()}, and {@link #reformat(TomlOptions.Style)} gives it, and everything
 * nested in it, a style of its own.
 *
 * <p>
 * Not safe for use from multiple threads without external synchronization.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface MutableTomlTable extends TomlTable {

  /**
   * Create a new, empty table.
   *
   * @return A new, empty table.
   */
  static MutableTomlTable create() {
    return new LinkedTomlTable();
  }

  /**
   * Get the table at a key, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address"}).
   * @return The table.
   * @throws IllegalArgumentException If the key cannot be parsed, or contains an unpaired surrogate.
   * @throws TomlInvalidTypeException If an element of the path exists and is not a table.
   */
  default MutableTomlTable getOrCreateTable(String dottedKey) {
    requireNonNull(dottedKey);
    return getOrCreateTable(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the table at a path, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * <p>
   * A table created here is an entry with no position and no comments.
   *
   * @param path The key path.
   * @return The table, or this table if {@code path} is empty.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws IllegalArgumentException If a path element contains an unpaired surrogate.
   * @throws TomlInvalidTypeException If an element of the path exists and is not a table.
   */
  MutableTomlTable getOrCreateTable(List<String> path);

  /**
   * Get the array at a key, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.addresses"}).
   * @return The array.
   * @throws IllegalArgumentException If the key cannot be parsed, or contains an unpaired surrogate.
   * @throws TomlInvalidTypeException If the value exists and is not an array, or an element of the path preceding the
   *         final key exists and is not a table.
   */
  default MutableTomlArray getOrCreateArray(String dottedKey) {
    requireNonNull(dottedKey);
    return getOrCreateArray(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Get the array at a path, creating it, and any intermediate table that does not already exist, if necessary.
   *
   * <p>
   * An array or table created here is an entry with no position and no comments.
   *
   * @param path The key path.
   * @return The array.
   * @throws IllegalArgumentException If {@code path} is empty, or a path element contains an unpaired surrogate.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws TomlInvalidTypeException If the value exists and is not an array, or an element of the path preceding the
   *         final key exists and is not a table.
   */
  MutableTomlArray getOrCreateArray(List<String> path);

  /**
   * Set a value in this table, replacing any value already there, as {@link #set(List, Object)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey} or {@code value} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed or contains an unpaired surrogate, or {@code value}
   *         cannot be converted to a TOML value or cannot be written as TOML.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  default MutableTomlTable set(String dottedKey, Object value) {
    requireNonNull(dottedKey);
    return set(Parser.parseDottedKey(dottedKey), value);
  }

  /**
   * Set a value in this table, replacing any value already there.
   *
   * <p>
   * Any intermediate table on the path that does not exist is created. Replacing a value keeps the entry: its position,
   * its place in iteration order and its attached comments. Adding one creates an entry with no position and no
   * comments. A table or array is stored as a deep copy, made as {@link #copyOf(TomlTable)} makes one, so it has no
   * positions.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before any intermediate table is created.
   *
   * @param path The key path.
   * @param value The value to set.
   * @return This table.
   * @throws IllegalArgumentException If {@code path} is empty, a path element contains an unpaired surrogate, or
   *         {@code value} cannot be converted to a TOML value or cannot be written as TOML.
   * @throws NullPointerException If a path element, or {@code value}, is {@code null}.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  MutableTomlTable set(List<String> path, Object value);

  /**
   * Insert a value into this table before an existing entry, as {@link #insertBefore(List, String, Object)} does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param key The key of the new entry, a single literal key, not a dotted key.
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code key}, or {@code value} is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code key} contains an unpaired surrogate, or
   *         {@code value} cannot be converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in the anchor's table.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertBefore(String anchorDottedKey, String key, Object value) {
    requireNonNull(anchorDottedKey);
    return insertBefore(Parser.parseDottedKey(anchorDottedKey), key, value);
  }

  /**
   * Insert a value into this table before an existing entry.
   *
   * <p>
   * The anchor is the path of an existing entry; the new entry is placed immediately before it, in the table that holds
   * it. {@code key} is one literal key in that table, not a dotted key. The new entry has no position and no attached
   * comments, and {@code value} is converted as {@link #set(List, Object)} converts one.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before the anchor path is walked.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param key The key of the new entry.
   * @param value The value to set.
   * @return This table.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code key} contains an unpaired surrogate, or
   *         {@code value} cannot be converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in the anchor's table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code key}, or {@code value} is {@code null}.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  MutableTomlTable insertBefore(List<String> anchorPath, String key, Object value);

  /**
   * Insert a value into this table after an existing entry, as {@link #insertAfter(List, String, Object)} does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param key The key of the new entry, a single literal key, not a dotted key.
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code key}, or {@code value} is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code key} contains an unpaired surrogate, or
   *         {@code value} cannot be converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in the anchor's table.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertAfter(String anchorDottedKey, String key, Object value) {
    requireNonNull(anchorDottedKey);
    return insertAfter(Parser.parseDottedKey(anchorDottedKey), key, value);
  }

  /**
   * Insert a value into this table after an existing entry.
   *
   * <p>
   * The anchor is the path of an existing entry; the new entry is placed immediately after it, in the table that holds
   * it. {@code key} is one literal key in that table, not a dotted key. The new entry has no position and no attached
   * comments, and {@code value} is converted as {@link #set(List, Object)} converts one.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before the anchor path is walked.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param key The key of the new entry.
   * @param value The value to set.
   * @return This table.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code key} contains an unpaired surrogate, or
   *         {@code value} cannot be converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in the anchor's table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code key}, or {@code value} is {@code null}.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  MutableTomlTable insertAfter(List<String> anchorPath, String key, Object value);

  /**
   * Insert a value into this table immediately before an element of its sequence.
   *
   * <p>
   * The anchor is an element of this table's own {@link #elements()}, matched by identity: an element of a sub-table, a
   * copy, or another document is rejected, even if it is equal. It may be an entry or an unattached comment; "before an
   * entry" means before the entry itself, so a comment attached above it stays attached to it. The new entry has no
   * position and no attached comments, and {@code value} is converted as {@link #set(List, Object)} converts one.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before {@code anchor} is checked.
   *
   * @param anchor The element to insert before.
   * @param key The key of the new entry.
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code key}, or {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code key} contains an unpaired surrogate, or {@code value} cannot be
   *         converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in this table.
   */
  MutableTomlTable insertBefore(TomlElement anchor, String key, Object value);

  /**
   * Insert a value into this table immediately after an element of its sequence.
   *
   * <p>
   * The new entry is placed immediately after {@code anchor}, matched as
   * {@link #insertBefore(TomlElement, String, Object)} matches one. The new entry has no position and no attached
   * comments, and {@code value} is converted as {@link #set(List, Object)} converts one.
   *
   * <p>
   * A rejected call leaves this table as it was: {@code value} is converted before {@code anchor} is checked.
   *
   * @param anchor The element to insert after.
   * @param key The key of the new entry.
   * @param value The value to set.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code key}, or {@code value} is {@code null}.
   * @throws IllegalArgumentException If {@code key} contains an unpaired surrogate, or {@code value} cannot be
   *         converted to a TOML value or cannot be written as TOML.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   * @throws TomlKeyAlreadySetException If {@code key} is already set in this table.
   */
  MutableTomlTable insertAfter(TomlElement anchor, String key, Object value);

  /**
   * Remove a value from this table, as {@link #remove(List)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return The value that was removed, or {@code null} if the key was not set.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  @Nullable
  default Object remove(String dottedKey) {
    requireNonNull(dottedKey);
    return remove(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Remove a value from this table.
   *
   * <p>
   * The entry goes, with the comments attached to it. The unattached comments among this table's elements stay where
   * they are.
   *
   * @param path The key path.
   * @return The value that was removed, or {@code null} if the key was not set, or if an element of the path preceding
   *         the final key is missing.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  @Nullable
  Object remove(List<String> path);

  /**
   * Remove every entry from this table.
   *
   * <p>
   * The unattached comments among this table's elements stay where they are. Clearing a table that is already empty is
   * not a modification.
   */
  void clear();

  /**
   * Whether this table was changed through this interface.
   *
   * <p>
   * A change is an entry added, replaced or removed, in this table or in any table or array nested within it. A table
   * read from a document, or newly created, reports {@code false}; nothing resets this once it is {@code true}.
   *
   * @return {@code true} if this table was changed through this interface.
   */
  boolean isModified();

  /**
   * Whether an entry of this table was changed through this interface, as {@link #isModified(List)} reports.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return {@code true} if the entry was changed; {@code false} if the key is not set.
   * @throws IllegalArgumentException If the key cannot be parsed.
   */
  default boolean isModified(String dottedKey) {
    requireNonNull(dottedKey);
    return isModified(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Whether an entry of this table was changed through this interface.
   *
   * <p>
   * A change is the entry added or replaced, or a change within the table or array it holds.
   *
   * @param path The key path.
   * @return {@code true} if the entry was changed; {@code false} if the key is not set. Equivalent to
   *         {@link #isModified()} if {@code path} is empty.
   */
  boolean isModified(List<String> path);

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(String...)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, or a line, is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, {@code lines} is empty, or a line cannot be written
   *         as a TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAbove(String dottedKey, String... lines) {
    requireNonNull(dottedKey);
    return setCommentAbove(Parser.parseDottedKey(dottedKey), Arrays.asList(lines));
  }

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(List)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, {@code lines} is empty, or a line cannot be written
   *         as a TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAbove(String dottedKey, List<String> lines) {
    requireNonNull(dottedKey);
    return setCommentAbove(Parser.parseDottedKey(dottedKey), lines);
  }

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(String...)} does.
   *
   * @param path The key path.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If a path element, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty, {@code lines} is empty, or a line cannot be written as a
   *         TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAbove(List<String> path, String... lines) {
    return setCommentAbove(path, Arrays.asList(lines));
  }

  /**
   * Set the run of comment lines written above an entry, as {@link MutableTomlEntry#setCommentAbove(List)} does.
   *
   * @param path The key path.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If a path element, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty, {@code lines} is empty, or a line cannot be written as a
   *         TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAbove(List<String> path, List<String> lines) {
    entryOrThrow(path).setCommentAbove(lines);
    return this;
  }

  /**
   * Set the comment written on an entry's line, as {@link MutableTomlEntry#setCommentAfter(String)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param text The comment text, as {@link TomlComment#lines()} would return its one line.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, or {@code text}, is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, or {@code text} cannot be written as a TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAfter(String dottedKey, String text) {
    requireNonNull(dottedKey);
    return setCommentAfter(Parser.parseDottedKey(dottedKey), text);
  }

  /**
   * Set the comment written on an entry's line, as {@link MutableTomlEntry#setCommentAfter(String)} does.
   *
   * @param path The key path.
   * @param text The comment text, as {@link TomlComment#lines()} would return its one line.
   * @return This table.
   * @throws NullPointerException If a path element, or {@code text}, is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty, or {@code text} cannot be written as a TOML comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setCommentAfter(List<String> path, String text) {
    entryOrThrow(path).setCommentAfter(text);
    return this;
  }

  /**
   * Set an attached comment on an entry from its text, as
   * {@link MutableTomlEntry#setComment(String, TomlComment.Placement)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param text The comment's text, as {@link TomlComment#text()} would return it.
   * @param placement Where the comment sits relative to the entry.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, {@code text}, or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, or a line of {@code text} cannot be written as a TOML
   *         comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setComment(String dottedKey, String text, TomlComment.Placement placement) {
    requireNonNull(dottedKey);
    return setComment(Parser.parseDottedKey(dottedKey), text, placement);
  }

  /**
   * Set an attached comment on an entry from its text, as
   * {@link MutableTomlEntry#setComment(String, TomlComment.Placement)} does.
   *
   * @param path The key path.
   * @param text The comment's text, as {@link TomlComment#text()} would return it.
   * @param placement Where the comment sits relative to the entry.
   * @return This table.
   * @throws NullPointerException If a path element, {@code text}, or {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty, or a line of {@code text} cannot be written as a TOML
   *         comment.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setComment(List<String> path, String text, TomlComment.Placement placement) {
    entryOrThrow(path).setComment(text, placement);
    return this;
  }

  /**
   * Set an attached comment on an entry, as {@link MutableTomlEntry#setComment(TomlComment)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param comment The comment, with {@link TomlComment#placement()} either {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed, or {@code comment} is unattached.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setComment(String dottedKey, TomlComment comment) {
    requireNonNull(dottedKey);
    return setComment(Parser.parseDottedKey(dottedKey), comment);
  }

  /**
   * Set an attached comment on an entry, as {@link MutableTomlEntry#setComment(TomlComment)} does.
   *
   * @param path The key path.
   * @param comment The comment, with {@link TomlComment#placement()} either {@link TomlComment.Placement#ABOVE} or
   *        {@link TomlComment.Placement#AFTER}.
   * @return This table.
   * @throws NullPointerException If a path element, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty, or {@code comment} is unattached.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable setComment(List<String> path, TomlComment comment) {
    entryOrThrow(path).setComment(comment);
    return this;
  }

  /**
   * Remove the run of comment lines written above an entry, as {@link MutableTomlEntry#removeCommentAbove()} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return This table.
   * @throws NullPointerException If {@code dottedKey} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeCommentAbove(String dottedKey) {
    requireNonNull(dottedKey);
    return removeCommentAbove(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Remove the run of comment lines written above an entry, as {@link MutableTomlEntry#removeCommentAbove()} does.
   *
   * @param path The key path.
   * @return This table.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeCommentAbove(List<String> path) {
    entryOrThrow(path).removeCommentAbove();
    return this;
  }

  /**
   * Remove the comment written on an entry's line, as {@link MutableTomlEntry#removeCommentAfter()} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @return This table.
   * @throws NullPointerException If {@code dottedKey} is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeCommentAfter(String dottedKey) {
    requireNonNull(dottedKey);
    return removeCommentAfter(Parser.parseDottedKey(dottedKey));
  }

  /**
   * Remove the comment written on an entry's line, as {@link MutableTomlEntry#removeCommentAfter()} does.
   *
   * @param path The key path.
   * @return This table.
   * @throws NullPointerException If a path element is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeCommentAfter(List<String> path) {
    entryOrThrow(path).removeCommentAfter();
    return this;
  }

  /**
   * Remove the attached comment at a placement, as {@link MutableTomlEntry#removeComment(TomlComment.Placement)} does.
   *
   * @param dottedKey A dotted key (e.g. {@code "server.address.port"}).
   * @param placement Which attached comment to remove.
   * @return This table.
   * @throws NullPointerException If {@code dottedKey}, or {@code placement}, is {@code null}.
   * @throws IllegalArgumentException If the key cannot be parsed.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeComment(String dottedKey, TomlComment.Placement placement) {
    requireNonNull(dottedKey);
    return removeComment(Parser.parseDottedKey(dottedKey), placement);
  }

  /**
   * Remove the attached comment at a placement, as {@link MutableTomlEntry#removeComment(TomlComment.Placement)} does.
   *
   * @param path The key path.
   * @param placement Which attached comment to remove.
   * @return This table.
   * @throws NullPointerException If a path element, or {@code placement}, is {@code null}.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  default MutableTomlTable removeComment(List<String> path, TomlComment.Placement placement) {
    entryOrThrow(path).removeComment(placement);
    return this;
  }

  /**
   * Add an unattached comment, after the elements already written, as {@link #addComment(List)} does.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlTable addComment(String... lines) {
    return addComment(Arrays.asList(lines));
  }

  /**
   * Add an unattached comment, after the elements already written.
   *
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code lines}, or a line, is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   */
  default MutableTomlTable addComment(List<String> lines) {
    return addComment(TomlComment.ofLines(lines, null));
  }

  /**
   * Add a comment, after the elements already written.
   *
   * <p>
   * The comment's position is ignored: the copy stored here has none. This is how an unattached comment is copied from
   * one table or array to another, across documents too.
   *
   * @param comment The comment to add, with no placement.
   * @return This table.
   * @throws NullPointerException If {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is attached.
   */
  MutableTomlTable addComment(TomlComment comment);

  /**
   * Insert an unattached comment into this table before an existing entry, as {@link #insertCommentBefore(List, List)}
   * does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentBefore(String anchorDottedKey, String... lines) {
    requireNonNull(anchorDottedKey);
    return insertCommentBefore(Parser.parseDottedKey(anchorDottedKey), Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table before an existing entry, as {@link #insertCommentBefore(List, List)}
   * does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentBefore(String anchorDottedKey, List<String> lines) {
    requireNonNull(anchorDottedKey);
    return insertCommentBefore(Parser.parseDottedKey(anchorDottedKey), lines);
  }

  /**
   * Insert an unattached comment into this table before an existing entry, as {@link #insertCommentBefore(List, List)}
   * does.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentBefore(List<String> anchorPath, String... lines) {
    return insertCommentBefore(anchorPath, Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table before an existing entry, as
   * {@link #insertCommentBefore(List, TomlComment)} places one.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentBefore(List<String> anchorPath, List<String> lines) {
    return insertCommentBefore(anchorPath, TomlComment.ofLines(lines, null));
  }

  /**
   * Insert a comment into this table before an existing entry, as {@link #insertCommentBefore(List, TomlComment)} does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, or {@code comment} is attached.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentBefore(String anchorDottedKey, TomlComment comment) {
    requireNonNull(anchorDottedKey);
    return insertCommentBefore(Parser.parseDottedKey(anchorDottedKey), comment);
  }

  /**
   * Insert a comment into this table before an existing entry.
   *
   * <p>
   * The comment is inserted as {@link #addComment(TomlComment)} adds one, its position ignored, immediately before the
   * anchor entry. The anchor's own {@link TomlComment.Placement#ABOVE} run stays attached to it, so the new comment is
   * written above that run.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, or {@code comment} is attached.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  MutableTomlTable insertCommentBefore(List<String> anchorPath, TomlComment comment);

  /**
   * Insert an unattached comment into this table after an existing entry, as {@link #insertCommentAfter(List, List)}
   * does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentAfter(String anchorDottedKey, String... lines) {
    requireNonNull(anchorDottedKey);
    return insertCommentAfter(Parser.parseDottedKey(anchorDottedKey), Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table after an existing entry, as {@link #insertCommentAfter(List, List)}
   * does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentAfter(String anchorDottedKey, List<String> lines) {
    requireNonNull(anchorDottedKey);
    return insertCommentAfter(Parser.parseDottedKey(anchorDottedKey), lines);
  }

  /**
   * Insert an unattached comment into this table after an existing entry, as {@link #insertCommentAfter(List, List)}
   * does.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentAfter(List<String> anchorPath, String... lines) {
    return insertCommentAfter(anchorPath, Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table after an existing entry, as
   * {@link #insertCommentAfter(List, TomlComment)} places one.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, {@code lines} is empty, or a line cannot be
   *         written as a TOML comment.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentAfter(List<String> anchorPath, List<String> lines) {
    return insertCommentAfter(anchorPath, TomlComment.ofLines(lines, null));
  }

  /**
   * Insert a comment into this table after an existing entry, as {@link #insertCommentAfter(List, TomlComment)} does.
   *
   * @param anchorDottedKey A dotted key naming the entry to insert beside (e.g. {@code "server.address"}).
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If {@code anchorDottedKey}, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If the anchor key cannot be parsed, or {@code comment} is attached.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of the anchor path preceding the final key exists and is not a
   *         table.
   */
  default MutableTomlTable insertCommentAfter(String anchorDottedKey, TomlComment comment) {
    requireNonNull(anchorDottedKey);
    return insertCommentAfter(Parser.parseDottedKey(anchorDottedKey), comment);
  }

  /**
   * Insert a comment into this table after an existing entry.
   *
   * <p>
   * The comment is inserted as {@link #addComment(TomlComment)} adds one, its position ignored, immediately after the
   * anchor entry in this table's {@link #elements()}.
   *
   * @param anchorPath The key path of the existing entry to insert beside.
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If an element of {@code anchorPath}, or {@code comment}, is {@code null}.
   * @throws IllegalArgumentException If {@code anchorPath} is empty, or {@code comment} is attached.
   * @throws NoSuchElementException If the anchor is not set.
   * @throws TomlInvalidTypeException If an element of {@code anchorPath} preceding the final key exists and is not a
   *         table.
   */
  MutableTomlTable insertCommentAfter(List<String> anchorPath, TomlComment comment);

  /**
   * Insert an unattached comment into this table immediately before an element of its sequence, as
   * {@link #insertCommentBefore(TomlElement, List)} does.
   *
   * @param anchor The element to insert before.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  default MutableTomlTable insertCommentBefore(TomlElement anchor, String... lines) {
    return insertCommentBefore(anchor, Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table immediately before an element of its sequence, as
   * {@link #insertCommentBefore(TomlElement, TomlComment)} places one.
   *
   * @param anchor The element to insert before.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  default MutableTomlTable insertCommentBefore(TomlElement anchor, List<String> lines) {
    return insertCommentBefore(anchor, TomlComment.ofLines(lines, null));
  }

  /**
   * Insert a comment into this table immediately before an element of its sequence.
   *
   * <p>
   * The comment is inserted as {@link #addComment(TomlComment)} adds one, its position ignored. The anchor is an
   * element of this table's own {@link #elements()}, matched by identity: an element of a sub-table, a copy, or another
   * document is rejected, even if it is equal. It may be an entry or an unattached comment; "before an entry" means
   * before the entry itself, so its own attached comments stay attached to it.
   *
   * @param anchor The element to insert before.
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If {@code anchor} or {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is attached.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  MutableTomlTable insertCommentBefore(TomlElement anchor, TomlComment comment);

  /**
   * Insert an unattached comment into this table immediately after an element of its sequence, as
   * {@link #insertCommentAfter(TomlElement, List)} does.
   *
   * @param anchor The element to insert after.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  default MutableTomlTable insertCommentAfter(TomlElement anchor, String... lines) {
    return insertCommentAfter(anchor, Arrays.asList(lines));
  }

  /**
   * Insert an unattached comment into this table immediately after an element of its sequence, as
   * {@link #insertCommentAfter(TomlElement, TomlComment)} places one.
   *
   * @param anchor The element to insert after.
   * @param lines The text of each line, as {@link TomlComment#lines()} would return it.
   * @return This table.
   * @throws NullPointerException If {@code anchor}, {@code lines}, or a line is {@code null}.
   * @throws IllegalArgumentException If {@code lines} is empty, or a line cannot be written as a TOML comment.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  default MutableTomlTable insertCommentAfter(TomlElement anchor, List<String> lines) {
    return insertCommentAfter(anchor, TomlComment.ofLines(lines, null));
  }

  /**
   * Insert a comment into this table immediately after an element of its sequence.
   *
   * <p>
   * The comment is inserted as {@link #addComment(TomlComment)} adds one, its position ignored, immediately after
   * {@code anchor}, matched as {@link #insertCommentBefore(TomlElement, TomlComment)} matches one.
   *
   * @param anchor The element to insert after.
   * @param comment The comment to insert, with no placement.
   * @return This table.
   * @throws NullPointerException If {@code anchor} or {@code comment} is {@code null}.
   * @throws IllegalArgumentException If {@code comment} is attached.
   * @throws NoSuchElementException If {@code anchor} is not an element of this table's {@link #elements()}.
   */
  MutableTomlTable insertCommentAfter(TomlElement anchor, TomlComment comment);

  /**
   * Remove an unattached comment, by identity.
   *
   * @param comment The comment to remove.
   * @return {@code true} if {@code comment} was among this table's {@link #elements()}, and was removed. An attached
   *         comment is never among them, so removing one here always returns {@code false}.
   */
  boolean removeComment(TomlComment comment);

  /**
   * Write this table, and everything in it, in a style of its own the next time the document is written.
   *
   * <p>
   * A parse result is written from the text it was parsed from, so a table keeps its layout until it is changed.
   * Reformatting it discards that: with {@link TomlOptions.Style#PRETTIFY} its lines keep their order, comments and
   * literal forms and take the layout the options give, and with {@link TomlOptions.Style#CANONICAL} it is written
   * entirely in the default style, as a table built with the editing API is, in the place its header had. The style
   * applies to every table and array nested in this one. It cannot be undone, and a later call keeps the stronger of
   * the two styles. A copy of this table is reformatted the same way.
   *
   * @param style The style: {@link TomlOptions.Style#PRETTIFY} or {@link TomlOptions.Style#CANONICAL}.
   * @return This table.
   * @throws NullPointerException If {@code style} is {@code null}.
   * @throws IllegalArgumentException If {@code style} is {@link TomlOptions.Style#PRESERVE}.
   */
  MutableTomlTable reformat(TomlOptions.Style style);

  /**
   * Get the entry for a key, or throw if the key is not set.
   *
   * @param path The key path.
   * @return The entry.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  private MutableTomlKeyValue entryOrThrow(List<String> path) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    MutableTomlKeyValue entry = entry(path);
    if (entry == null) {
      throw new NoSuchElementException(Toml.joinKeyPath(path) + " is not set");
    }
    return entry;
  }

  /**
   * Create a deep copy of a table.
   *
   * <p>
   * The copy is independent of {@code table}: a nested table or array is copied recursively, and later changes to
   * either are not seen by the other. Every entry of the copy has no input position and reports as modified, since none
   * of it was read from a document; the copy itself reports {@link #isModified()} {@code false} while it has no
   * entries, like {@link #create()}. The comments attached to each entry, and the unattached comments among the table's
   * elements, are kept.
   *
   * @param table The table to copy.
   * @return A new, independent table with the same entries.
   * @throws IllegalArgumentException If a key in {@code table} contains an unpaired surrogate, or a value in it cannot
   *         be written as TOML.
   */
  static MutableTomlTable copyOf(TomlTable table) {
    requireNonNull(table);
    if (table instanceof LinkedTomlTable) {
      return ((LinkedTomlTable) table).copy();
    }
    return LinkedTomlTable.copyFrom(table);
  }

  /**
   * Create a table from a {@link Map}.
   *
   * <p>
   * Each key of {@code map} is one literal key, not a dotted key. Each value is converted as any value set through this
   * interface is.
   *
   * @param map The map to copy.
   * @return A new table with one entry per entry of {@code map}.
   * @throws NullPointerException If a value in {@code map} is {@code null}.
   * @throws IllegalArgumentException If a key in {@code map} contains an unpaired surrogate, or a value in it cannot be
   *         converted to a TOML value or cannot be written as TOML.
   */
  static MutableTomlTable copyOf(Map<String, ?> map) {
    requireNonNull(map);
    MutableTomlTable table = create();
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      table.set(Collections.singletonList(entry.getKey()), entry.getValue());
    }
    return table;
  }

  @Override
  @Nullable
  default MutableTomlKeyValue entry(String dottedKey) {
    requireNonNull(dottedKey);
    return entry(Parser.parseDottedKey(dottedKey));
  }

  @Override
  @Nullable
  MutableTomlKeyValue entry(List<String> path);

  @Override
  @Nullable
  default MutableTomlTable getTable(String dottedKey) {
    requireNonNull(dottedKey);
    return getTable(Parser.parseDottedKey(dottedKey));
  }

  @Override
  @Nullable
  default MutableTomlTable getTable(List<String> path) {
    TomlTable value = TomlTable.super.getTable(path);
    return (value != null) ? (MutableTomlTable) value : null;
  }

  @Override
  @Nullable
  default MutableTomlArray getArray(String dottedKey) {
    requireNonNull(dottedKey);
    return getArray(Parser.parseDottedKey(dottedKey));
  }

  @Override
  @Nullable
  default MutableTomlArray getArray(List<String> path) {
    TomlArray value = TomlTable.super.getArray(path);
    return (value != null) ? (MutableTomlArray) value : null;
  }
}
