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

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Options controlling how {@link TomlTable#toToml(TomlWriteOptions)} and {@link TomlArray#toToml(TomlWriteOptions)}
 * write TOML.
 *
 * <p>
 * {@link #keep(Keep)} sets how much of the existing structure and format of a parsed document is kept.
 * {@link Keep#LAYOUT}, the default, keeps the layout: a parsed document is written from the text it was parsed from,
 * and only what the editing API changed is written anew. {@link Keep#NOTATION} keeps the notation - the form each key,
 * value and table was written in, the order of lines and sections and the comments - while whitespace, indentation,
 * blank lines and the layout of arrays and inline tables come from these options. {@link Keep#NOTHING} keeps nothing,
 * and writes the whole document in the default style.
 *
 * <p>
 * Each value falls back to the next where there is nothing to keep: a line written anew is written as
 * {@link Keep#NOTATION} writes it, and a table or array with no notation to keep - one built through the editing API,
 * or read from a document parsed with {@link TomlParseOptions#withoutSource()} - is written as {@link Keep#NOTHING}
 * writes it, in the default style.
 */
public final class TomlWriteOptions {

  /**
   * How much of the existing document structure and format is kept.
   *
   * <p>
   * Each value keeps less than the one before it, and where a value has nothing to keep, the next value applies. Where
   * an amount is set on a single table or array through {@link MutableTomlTable#reformat(Keep)} as well as in the
   * options, the table or array keeps the later of the two in this order.
   */
  public enum Keep {
    /**
     * Keep the layout of a parsed document: every line, header and comment the parser accepted is written back as it
     * was read, with its spacing, indentation and blank lines, and only what the editing API changed is written anew.
     *
     * <p>
     * Anything with no layout to keep - a line written anew, a table built with the editing API, a document parsed with
     * {@link TomlParseOptions#withoutSource()}, a table of a parse result rather than the result itself - is written as
     * {@link #NOTATION} writes it.
     */
    LAYOUT,

    /**
     * Keep the notation of a parsed document: the form each key, value and table was written in, whether {@code 0x10}
     * or {@code 16}, a bare or quoted key, a basic or literal string, a header, dotted keys or an inline table. The
     * order of its lines and sections and its comments are kept too, while whitespace, indentation, blank lines and the
     * layout of arrays and inline tables come from the options.
     *
     * <p>
     * Anything with no notation to keep, such as a document parsed without its source, is written as {@link #NOTHING}
     * writes it.
     */
    NOTATION,

    /**
     * Keep nothing: everything is written in the default style.
     */
    NOTHING
  }

  /**
   * The default maximum line width: {@value}.
   *
   * @see #withMaxLineWidth(int)
   */
  public static final int DEFAULT_MAX_LINE_WIDTH = 80;

  private final Keep keep;
  private final int indent;
  private final int maxLineWidth;

  /** The separator new lines end with, or {@code null} for the platform's. */
  @Nullable
  private final String lineSeparator;
  private final TomlVersion version;
  private final boolean alignEntries;

  private TomlWriteOptions(
      Keep keep,
      int indent,
      int maxLineWidth,
      @Nullable String lineSeparator,
      TomlVersion version,
      boolean alignEntries) {
    this.keep = keep;
    this.indent = indent;
    this.maxLineWidth = maxLineWidth;
    this.lineSeparator = lineSeparator;
    this.version = version;
    this.alignEntries = alignEntries;
  }

  /**
   * The default options: {@link Keep#LAYOUT}, no indentation, a maximum line width of {@value #DEFAULT_MAX_LINE_WIDTH},
   * no line separator of their own, so that lines end with the platform's, {@link System#lineSeparator()}, and output
   * written for {@link TomlVersion#LATEST}.
   *
   * @return The default options.
   */
  public static TomlWriteOptions defaults() {
    return new TomlWriteOptions(Keep.LAYOUT, 0, DEFAULT_MAX_LINE_WIDTH, null, TomlVersion.LATEST, false);
  }

  /**
   * Create a copy of these options that keeps a different amount of the existing document structure and format.
   *
   * @param keep How much of the existing document structure and format to keep.
   * @return A new set of options with the given amount to keep.
   */
  public TomlWriteOptions keep(Keep keep) {
    requireNonNull(keep);
    return new TomlWriteOptions(keep, indent, maxLineWidth, lineSeparator, version, alignEntries);
  }

  /**
   * Create a copy of these options that indents nested tables.
   *
   * <p>
   * A header whose path has {@code n} keys is indented by {@code (n - 1) * spaces}, and the entries of a table whose
   * path has {@code n} keys by {@code n * spaces}. The entries of the root table are not indented, and the path of a
   * table in an array of tables is the path of the array. For example, with an indent of 2:
   *
   * <pre>{@code
   * title = "Example"
   *
   * [server]
   *   host = "localhost"
   *
   *   [server.tls]
   *     enabled = true
   *
   * [[products]]
   *   sku = 1
   * }</pre>
   *
   * <p>
   * {@link #withEntriesAlignedWithHeaders(boolean)} indents the entries of a table like its header instead.
   *
   * <p>
   * Regardless of the indent, the elements of a multi-line array are indented two spaces beyond the line the array
   * starts on.
   *
   * @param spaces The number of spaces to indent per level of table nesting. Must not be negative.
   * @return A new set of options with the given indent.
   * @throws IllegalArgumentException If {@code spaces} is negative.
   */
  public TomlWriteOptions withIndent(int spaces) {
    if (spaces < 0) {
      throw new IllegalArgumentException("indent must not be negative: " + spaces);
    }
    return new TomlWriteOptions(keep, spaces, maxLineWidth, lineSeparator, version, alignEntries);
  }

  /**
   * Create a copy of these options that indents the entries of a table like its header, or one level beyond it.
   *
   * <p>
   * With entries aligned, the entries of a table whose path has {@code n} keys are indented by
   * {@code (n - 1) * spaces}, the same as its header, rather than by {@code n * spaces}. For example, with an indent of
   * 2:
   *
   * <pre>{@code
   * title = "Example"
   *
   * [server]
   * host = "localhost"
   *
   *   [server.tls]
   *   enabled = true
   *
   * [[products]]
   * sku = 1
   * }</pre>
   *
   * <p>
   * The default is {@code false}, which indents entries one level beyond their header; see {@link #withIndent(int)}.
   *
   * @param aligned Whether the entries of a table are indented like its header.
   * @return A new set of options with the given alignment.
   */
  public TomlWriteOptions withEntriesAlignedWithHeaders(boolean aligned) {
    return new TomlWriteOptions(keep, indent, maxLineWidth, lineSeparator, version, aligned);
  }

  /**
   * Create a copy of these options that ends each line with a different line separator.
   *
   * <p>
   * The separator is also used for the line breaks inside a multi-line basic string, written for a string value that
   * contains a newline.
   *
   * @param separator The line separator: {@code "\n"} or {@code "\r\n"}, the only newlines that TOML allows.
   * @return A new set of options with the given line separator.
   * @throws IllegalArgumentException If {@code separator} is neither {@code "\n"} nor {@code "\r\n"}.
   */
  public TomlWriteOptions withLineSeparator(String separator) {
    requireNonNull(separator);
    if (!separator.equals("\n") && !separator.equals("\r\n")) {
      throw new IllegalArgumentException("lineSeparator must be \"\\n\" or \"\\r\\n\"");
    }
    return new TomlWriteOptions(keep, indent, maxLineWidth, separator, version, alignEntries);
  }

  /**
   * Create a copy of these options with a different maximum line width.
   *
   * <p>
   * An array is written on one line if that whole line fits within the maximum width, and otherwise with each element
   * on its own line. The width of a line is counted in code points, and includes its indentation, the key before the
   * array, and the comma after an element of an enclosing multi-line array.
   *
   * <p>
   * When writing TOML 1.1.0, an inline table is written the same way, with each entry on its own line. When writing
   * TOML 1.0.0, an inline table is never split, and everything inside one stays on one line; see
   * {@link #withVersion(TomlVersion)}.
   *
   * <p>
   * Lines can still be wider than the maximum: a long key or string is never split, nor is an inline table when writing
   * TOML 1.0.0. With a maximum width of {@code 0}, every non-empty array and inline table is written over multiple
   * lines, except, when writing TOML 1.0.0, an inline table and everything inside one.
   *
   * @param columns The widest a line may be, in code points, for an array to be written on one line. Must not be
   *        negative.
   * @return A new set of options with the given maximum line width.
   * @throws IllegalArgumentException If {@code columns} is negative.
   */
  public TomlWriteOptions withMaxLineWidth(int columns) {
    if (columns < 0) {
      throw new IllegalArgumentException("maxLineWidth must not be negative: " + columns);
    }
    return new TomlWriteOptions(keep, indent, columns, lineSeparator, version, alignEntries);
  }

  /**
   * How much of the existing document structure and format is kept.
   *
   * @return How much of the existing document structure and format is kept.
   * @see #keep(Keep)
   */
  public Keep keep() {
    return keep;
  }

  /**
   * Create a copy of these options that writes for a different version of TOML.
   *
   * <p>
   * The version decides how an inline table that does not fit within the maximum line width is written. When writing
   * TOML 1.1.0, which allows line breaks inside an inline table, such a table is written over several lines, with each
   * entry on its own line, as an array is. When writing TOML 1.0.0, which allows no line break inside an inline table,
   * an inline table is written on one line regardless of its width. The default is {@link TomlVersion#LATEST}.
   *
   * <p>
   * An inline table holding a comment can only be written over several lines, since a comment ends at a line break, so
   * writing one for TOML 1.0.0 throws {@link IllegalArgumentException}.
   *
   * <p>
   * Text copied from a document (a line kept as it was read, or the literal a key or value was written as) is copied
   * only for a version that allows it. Writing for TOML 1.0.0 throws {@link IllegalArgumentException} where it would
   * copy a construct of TOML 1.1.0: an escape sequence {@code \e} or {@code \xHH}, a time without seconds, or a line
   * break or trailing comma inside an inline table, whether the text comes from the document being written, from a
   * value made with {@link TomlValue#parse(String)}, or from a value copied out of another document. A value replaced
   * or removed through the editing API is not copied, and {@link Keep#NOTHING} copies no text.
   *
   * @param version The version of TOML to write for.
   * @return A new set of options with the given version.
   * @see #withMaxLineWidth(int)
   */
  public TomlWriteOptions withVersion(TomlVersion version) {
    requireNonNull(version);
    return new TomlWriteOptions(keep, indent, maxLineWidth, lineSeparator, version, alignEntries);
  }

  /**
   * The number of spaces to indent per level of table nesting.
   *
   * @return The number of spaces to indent per level of table nesting.
   * @see #withIndent(int)
   */
  public int indent() {
    return indent;
  }

  /**
   * Whether the entries of a table are indented like its header.
   *
   * @return {@code true} if the entries of a table are indented like its header, {@code false} if one level beyond it.
   * @see #withEntriesAlignedWithHeaders(boolean)
   */
  public boolean entriesAlignedWithHeaders() {
    return alignEntries;
  }

  /**
   * The width of the indentation of the entries of a table.
   *
   * @param depth The number of keys in the path of the table, or of the array holding it.
   * @return The width of the indentation, in spaces.
   */
  int entryIndent(int depth) {
    return (alignEntries ? Math.max(depth - 1, 0) : depth) * indent;
  }

  /**
   * The line separator written at the end of each line.
   *
   * @return The line separator these options ask for, or {@link System#lineSeparator()} if they ask for none.
   * @see #withLineSeparator(String)
   */
  public String lineSeparator() {
    return (lineSeparator != null) ? lineSeparator : System.lineSeparator();
  }

  /**
   * The line separator these options ask for, if they ask for one.
   *
   * @return The line separator, or {@code null} if these options ask for none.
   */
  @Nullable
  String askedLineSeparator() {
    return lineSeparator;
  }

  /**
   * The widest a line may be for an array to be written on one line.
   *
   * @return The maximum line width, in code points.
   * @see #withMaxLineWidth(int)
   */
  public int maxLineWidth() {
    return maxLineWidth;
  }

  /**
   * The version of TOML the output is written for, {@link TomlVersion#LATEST} by default.
   *
   * @return The version of TOML the output is written for.
   * @see #withVersion(TomlVersion)
   */
  public TomlVersion version() {
    return version;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlWriteOptions)) {
      return false;
    }
    TomlWriteOptions other = (TomlWriteOptions) obj;
    return this.keep == other.keep
        && this.indent == other.indent
        && this.maxLineWidth == other.maxLineWidth
        && Objects.equals(this.lineSeparator, other.lineSeparator)
        && this.version == other.version
        && this.alignEntries == other.alignEntries;
  }

  @Override
  public int hashCode() {
    return Objects.hash(keep, indent, maxLineWidth, lineSeparator, version, alignEntries);
  }

  @Override
  public String toString() {
    return "TomlWriteOptions{keep="
        + keep
        + ", indent="
        + indent
        + ", maxLineWidth="
        + maxLineWidth
        + ", lineSeparator=\""
        + lineSeparator().replace("\r", "\\r").replace("\n", "\\n")
        + "\", version="
        + version
        + ", entriesAlignedWithHeaders="
        + alignEntries
        + '}';
  }
}
