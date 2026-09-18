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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * Options controlling how {@link TomlTable#toToml(TomlOptions)} and {@link TomlArray#toToml(TomlOptions)} write TOML.
 *
 * <p>
 * {@link Style#PRESERVE}, the default, keeps the most: a parsed document is written from the text it was parsed from,
 * and only what the editing API changed is written anew. {@link Style#PRETTIFY} keeps what was written - the order of
 * lines, the comments, the table structure and the literal form of each key and value - but writes the whitespace,
 * indentation and the layout of arrays and inline tables from these options instead of how the document had them.
 * {@link Style#CANONICAL} keeps none of it, writing the whole document in the default style. A table or array with no
 * text to write from - one built through the editing API, or read from a document parsed with
 * {@link TomlParseOptions#withoutSource()} - is written in the default style whatever style is asked for.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public final class TomlOptions {

  /**
   * How much of the way a document was written is kept.
   *
   * <p>
   * The constants are declared in the order of how much they keep: {@link #PRESERVE} keeps the most, {@link #PRETTIFY}
   * keeps what was written but not how, and {@link #CANONICAL} keeps none of it. Where a style is asked for a single
   * table or array as well as for the document, through {@link MutableTomlTable#reformat(Style)}, the later of the two
   * in this order is the one it is written in.
   */
  public enum Style {
    /**
     * Write a parsed document from the text it was parsed from, so that every line, header and comment the parser
     * accepted is written back as it was read, and only what the editing API changed is written anew.
     *
     * <p>
     * Anything with no such text to read - a table built through the editing API, a document parsed with
     * {@link TomlParseOptions#withoutSource()}, a table of a parse result rather than the result itself - is written in
     * the default style, which is what {@link #CANONICAL} writes everything in.
     */
    PRESERVE,

    /**
     * Write a parsed document in a normalized layout: the order of its lines, its comments, its table structure
     * (headers, dotted keys, inline tables) and the literal form of each key and value are kept, while whitespace,
     * indentation, blank lines and the layout of arrays and inline tables follow the options.
     *
     * <p>
     * A document with no text to read is written as {@link #PRESERVE} writes it, in the default style.
     */
    PRETTIFY,

    /**
     * Write everything in the default style, ignoring how the document was written.
     */
    CANONICAL
  }

  /**
   * The default maximum line width: {@value}.
   *
   * @see #withMaxLineWidth(int)
   */
  public static final int DEFAULT_MAX_LINE_WIDTH = 80;

  private final Style style;
  private final int indent;
  private final int maxLineWidth;

  /** The separator new lines end with, or {@code null} for the platform's. */
  @Nullable
  private final String lineSeparator;

  private TomlOptions(Style style, int indent, int maxLineWidth, @Nullable String lineSeparator) {
    this.style = style;
    this.indent = indent;
    this.maxLineWidth = maxLineWidth;
    this.lineSeparator = lineSeparator;
  }

  /**
   * The default options: {@link Style#PRESERVE}, no indentation, a maximum line width of
   * {@value #DEFAULT_MAX_LINE_WIDTH}, and no line separator of their own, so that lines end with the platform's,
   * {@link System#lineSeparator()}.
   *
   * @return The default options.
   */
  public static TomlOptions defaults() {
    return new TomlOptions(Style.PRESERVE, 0, DEFAULT_MAX_LINE_WIDTH, null);
  }

  /**
   * Create a copy of these options that writes in a different style.
   *
   * @param style The style to write in.
   * @return A new set of options with the given style.
   */
  public TomlOptions withStyle(Style style) {
    requireNonNull(style);
    return new TomlOptions(style, indent, maxLineWidth, lineSeparator);
  }

  /**
   * Create a copy of these options that writes a parsed document in a normalized layout: the order of its lines, its
   * comments, its table structure (headers, dotted keys, inline tables) and the literal form of each key and value are
   * kept, while whitespace, indentation, blank lines and the layout of arrays and inline tables follow these options. A
   * document with no retained source is written as it would be anyway.
   *
   * @return A new set of options writing in the prettified style.
   */
  public TomlOptions prettify() {
    return withStyle(Style.PRETTIFY);
  }

  /**
   * Create a copy of these options that writes everything in the default style; see {@link Style#CANONICAL}.
   *
   * @return A new set of options writing in the canonical style.
   */
  public TomlOptions canonical() {
    return withStyle(Style.CANONICAL);
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
   * Whatever the indent, the elements of a multi-line array are indented two spaces beyond the line the array starts
   * on.
   *
   * @param spaces The number of spaces to indent per level of table nesting. Must not be negative.
   * @return A new set of options with the given indent.
   * @throws IllegalArgumentException If {@code spaces} is negative.
   */
  public TomlOptions withIndent(int spaces) {
    if (spaces < 0) {
      throw new IllegalArgumentException("indent must not be negative: " + spaces);
    }
    return new TomlOptions(style, spaces, maxLineWidth, lineSeparator);
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
  public TomlOptions withLineSeparator(String separator) {
    requireNonNull(separator);
    if (!separator.equals("\n") && !separator.equals("\r\n")) {
      throw new IllegalArgumentException("lineSeparator must be \"\\n\" or \"\\r\\n\"");
    }
    return new TomlOptions(style, indent, maxLineWidth, separator);
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
   * Lines can still be wider than the maximum: a long key or string, or an inline table, is never split, and everything
   * inside an inline table stays on one line. With a maximum width of {@code 0}, every non-empty array outside an
   * inline table is written over multiple lines.
   *
   * @param columns The widest a line may be, in code points, for an array to be written on one line. Must not be
   *        negative.
   * @return A new set of options with the given maximum line width.
   * @throws IllegalArgumentException If {@code columns} is negative.
   */
  public TomlOptions withMaxLineWidth(int columns) {
    if (columns < 0) {
      throw new IllegalArgumentException("maxLineWidth must not be negative: " + columns);
    }
    return new TomlOptions(style, indent, columns, lineSeparator);
  }

  /**
   * The style these options write in.
   *
   * @return The style.
   * @see #withStyle(Style)
   */
  public Style style() {
    return style;
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

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlOptions)) {
      return false;
    }
    TomlOptions other = (TomlOptions) obj;
    return this.style == other.style
        && this.indent == other.indent
        && this.maxLineWidth == other.maxLineWidth
        && Objects.equals(this.lineSeparator, other.lineSeparator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(style, indent, maxLineWidth, lineSeparator);
  }

  @Override
  public String toString() {
    return "TomlOptions{style="
        + style
        + ", indent="
        + indent
        + ", maxLineWidth="
        + maxLineWidth
        + ", lineSeparator=\""
        + lineSeparator().replace("\r", "\\r").replace("\n", "\\n")
        + "\"}";
  }
}
