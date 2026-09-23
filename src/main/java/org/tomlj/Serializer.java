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
import static org.tomlj.TomlType.typeFor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Writes tables and arrays as TOML.
 *
 * <p>
 * The table being written is the document root. A table is written in two parts: first its {@code key = value} entries
 * and its unattached comments, in their order in the table; then its sub-tables and arrays of tables, also in their
 * order in the table. A sub-table is written under a {@code [header]} naming its path from the root, unless it holds
 * only sub-tables and arrays of tables, whose headers imply it. Each table in an array of tables is written under a
 * {@code [[header]]}. Tables and arrays inside arrays are written inline.
 *
 * <p>
 * An array is written on one line if that line fits within the maximum line width, and otherwise with each element on
 * its own line. When writing TOML 1.1.0, which allows line breaks inside an inline table, an inline table is written
 * the same way, with each entry on its own line. When writing TOML 1.0.0, which allows no line break inside an inline
 * table, an inline table is written on one line regardless of its width.
 *
 * <p>
 * A string is written as a multi-line basic string when it is the value of a {@code key = value} line and contains a
 * newline. Every other string, whether a key or a value in an array or inline table, is written as a single-line basic
 * string.
 *
 * <p>
 * The default style writes comments from the model: the run above an entry or header, the comment after it, and the
 * comments unattached to any entry, in the container they were written in. An array or inline table holding a comment
 * is written over several lines for every TOML version, since a comment ends at a line break. TOML 1.0.0 allows no line
 * break inside an inline table, so writing an inline table holding a comment for TOML 1.0.0 throws
 * {@link IllegalArgumentException}. Where a comment is written decides which container a re-parse reads it in, and a
 * blank line separates an unattached comment from the element it would otherwise be attached to: a run separated by a
 * blank line from what follows it belongs to the container of the next expression, and a run written directly under a
 * line belongs to the container open where it was written.
 *
 * <p>
 * Unless the options ask for {@link TomlWriteOptions.Keep#NOTHING}, a key or a scalar that has a span is written with
 * the text of that span rather than in the form this writer would give it, and the dotted keys of an inline table are
 * written as the document wrote them. The layout, the spacing and the indentation around them come from the options.
 */
final class Serializer {
  private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  // The indentation of the elements of a multi-line array, relative to the line the array starts on
  private static final String ARRAY_ELEMENT_INDENT = "  ";

  private final Appendable out;
  // The spaces to indent per level of table nesting
  private final int indent;
  // The widest a line holding a single-line array or inline table may be, in code points
  private final int maxLineWidth;
  private final String lineSeparator;
  // The version of TOML written for, which decides whether an inline table may be written over several lines
  private final TomlVersion version;
  // Whether a line that has a span is copied rather than written anew. Set only for a block of a document being written
  // from its source, where a copied entry keeps the literal it was read as.
  private final boolean copySourceLines;
  // Whether the notation is kept: a key or scalar that has a span is written with the text of that span. Set unless the
  // options ask for NOTHING, so that a line LAYOUT writes anew is written as NOTATION writes it.
  private final boolean keepNotation;
  private boolean written = false;
  // A pending blank line, written only once something follows it, so that the document never ends with one
  private boolean blankLineOwed = false;

  private Serializer(Appendable out, TomlWriteOptions options, boolean copySourceLines) {
    this.out = out;
    this.indent = options.indent();
    this.maxLineWidth = options.maxLineWidth();
    this.lineSeparator = options.lineSeparator();
    this.version = options.version();
    this.keepNotation = (options.keep() != TomlWriteOptions.Keep.NOTHING);
    this.copySourceLines = copySourceLines && (options.keep() == TomlWriteOptions.Keep.LAYOUT);
  }

  /**
   * A writer of the default style.
   *
   * @param out The output.
   * @param options The options to write with.
   * @return A writer.
   */
  static Serializer defaultStyle(Appendable out, TomlWriteOptions options) {
    return new Serializer(out, options, false);
  }

  /**
   * A writer of a block of a document written keeping its layout: the default style, except that a line that has a span
   * is copied rather than written anew, so that a copied entry keeps the literal and the spacing it was read with. When
   * the options keep only the notation, every line is written anew and only the literal forms are kept.
   *
   * @param out The output.
   * @param options The options to write with.
   * @return A writer.
   */
  static Serializer blockStyle(Appendable out, TomlWriteOptions options) {
    return new Serializer(out, options, true);
  }

  static void toToml(TomlTable table, Appendable appendable, TomlWriteOptions options) throws IOException {
    requireNonNull(table);
    requireNonNull(appendable);
    requireNonNull(options);
    if (options.keep() != TomlWriteOptions.Keep.NOTHING && table instanceof ParsedTomlTable) {
      ParsedTomlTable parsed = (ParsedTomlTable) table;
      if (parsed.source() != null) {
        SourcePreservingSerializer.toToml(parsed, appendable, options);
        return;
      }
    }
    defaultStyle(appendable, options).writeEntries(table, new ArrayList<>());
  }

  static void toToml(TomlArray array, Appendable appendable, TomlWriteOptions options) throws IOException {
    requireNonNull(array);
    requireNonNull(appendable);
    requireNonNull(options);
    // The array starts at column 0 outside any table, so the indent never applies
    defaultStyle(appendable, options).writeValue(array, "", 0, 0);
  }

  /**
   * Write the elements of a table: its values and unattached comments, then its sub-tables and arrays of tables.
   *
   * <p>
   * An unattached comment is written among the values when no section of this table precedes it, and among the sections
   * otherwise. The second case only arises in the root table, where a run written between two headers, or after the
   * last one, belongs to the root; in any other table a run written after a section would have been read in that
   * section, so it is written among the values, where a re-parse reads it in this table.
   *
   * @param table The table.
   * @param path The keys from the root to the table, or to the array holding it. Restored before returning.
   */
  private void writeEntries(TomlTable table, List<String> path) throws IOException {
    List<TomlElement> elements = table.elements();
    String lineIndent = indentFor(path.size());
    int firstSection = path.isEmpty() ? firstSectionIndex(elements, keepNotation) : -1;
    int lastLine = lastLineIndex(elements, keepNotation);
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlComment) {
        if (firstSection < 0 || i < firstSection) {
          writeUnattachedComment((TomlComment) element, lineIndent, i < lastLine);
        }
      } else if (isLine((TomlEntry) element, keepNotation)) {
        TomlKeyValue pair = (TomlKeyValue) element;
        writeKeyValue(lineIndent, Collections.singletonList(pair.key()), pair);
      }
    }
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlComment) {
        if (firstSection >= 0 && i > firstSection) {
          writeUnattachedComment((TomlComment) element, lineIndent, true);
        }
        continue;
      }
      TomlEntry entry = (TomlEntry) element;
      if (isLine(entry, keepNotation)) {
        continue;
      }
      path.add(((TomlKeyValue) entry).key());
      writeSections(entry, path);
      path.remove(path.size() - 1);
    }
  }

  /**
   * Write an entry that is not written as a {@code key = value} line: a sub-table, under a header naming its path
   * unless the headers of its own sub-tables imply it, or an array of tables, as one {@code [[header]]} section per
   * element.
   *
   * @param entry The entry holding the table, or the array of tables.
   * @param path The keys from the root to the value. Restored before returning.
   */
  void writeSections(TomlEntry entry, List<String> path) throws IOException {
    Object value = entry.value().get();
    if (value instanceof TomlTable) {
      TomlTable subTable = (TomlTable) value;
      if (needsHeader(subTable, entry.comments())) {
        writeHeader("[", path, "]", entry.comments());
      }
      writeEntries(subTable, path);
      return;
    }
    for (TomlElement element : ((TomlArray) value).elements()) {
      writeArrayTableSection((TomlEntry) element, path);
    }
  }

  /**
   * Write one table of an array of tables as a {@code [[header]]} section.
   *
   * @param tableEntry The entry of the array holding the table.
   * @param path The keys from the root to the array.
   */
  void writeArrayTableSection(TomlEntry tableEntry, List<String> path) throws IOException {
    writeHeader("[[", path, "]]", tableEntry.comments());
    writeEntries((TomlTable) tableEntry.value().get(), path);
  }

  private void writeHeader(String open, List<String> path, String close, List<TomlComment> comments)
      throws IOException {
    blankLine();
    String headerIndent = indentFor(path.size() - 1);
    writeCommentAbove(comments, headerIndent);
    beginLine(headerIndent);
    StringBuilder header = new StringBuilder(open);
    appendKeyPath(header, path);
    out.append(header.append(close));
    writeCommentAfter(comments);
    endLine();
  }

  /**
   * Write a table header line from the text the document wrote it as, with the comments of the entry holding the table
   * written from the model around it.
   *
   * @param lineIndent The indentation of the line.
   * @param header The header, its brackets included, as the document wrote it.
   * @param comments The comments attached to the entry holding the table.
   */
  void writeHeaderText(String lineIndent, String header, List<TomlComment> comments) throws IOException {
    writeCommentAbove(comments, lineIndent);
    beginLine(lineIndent);
    out.append(header);
    writeCommentAfter(comments);
    endLine();
  }

  /**
   * Write a {@code key = value} line.
   *
   * @param lineIndent The indentation of the line.
   * @param keyPath The key, as the keys of a dotted key.
   * @param entry The entry the line holds.
   */
  void writeKeyValue(String lineIndent, List<String> keyPath, TomlEntry entry) throws IOException {
    if (copySourceLines && writeSourceLine(lineIndent, keyPath, entry)) {
      return;
    }
    List<TomlComment> comments = entry.comments();
    writeCommentAbove(comments, lineIndent);
    beginLine(lineIndent);
    StringBuilder prefix = new StringBuilder();
    appendEntryKey(prefix, keyPath, entry, keepNotation);
    prefix.append(" = ");
    out.append(prefix);
    writeEntryValue(entry.value(), lineIndent, width(lineIndent) + prefix.codePointCount(0, prefix.length()));
    writeCommentAfter(comments);
    endLine();
  }

  /**
   * Write a line from its span: its key, the spacing around the {@code =}, its value and the comment after it, as they
   * were written. The key is written anew when the line was written with a dotted key of a different number of parts
   * than the key it is written with here, and the line ends with the line separator from the options.
   *
   * @param lineIndent The indentation of the line.
   * @param keyPath The key, as the keys of a dotted key.
   * @param entry The entry the line holds.
   * @return {@code false} if the line has no span, its comments were edited, or its value has no span or was edited.
   */
  private boolean writeSourceLine(String lineIndent, List<String> keyPath, TomlEntry entry) throws IOException {
    if (!(entry instanceof Entry)) {
      return false;
    }
    Entry parsed = (Entry) entry;
    SourceSpan span = parsed.span;
    if (span == null || span.kind != SourceSpan.Kind.LINE || parsed.commentsModified()) {
      return false;
    }
    int valueStop = writtenValueStop(parsed.value);
    if (valueStop < 0) {
      return false;
    }
    writeCommentAbove(entry.comments(), lineIndent);
    beginLine(lineIndent);
    StringBuilder text = new StringBuilder();
    if (span.keyParts == keyPath.size()) {
      text.append(span.source.text(span.keyStart, span.keyStop));
    } else {
      appendKeyPath(text, keyPath);
    }
    text.append(span.source.text(span.keyStop + 1, valueStop));
    text.append(span.source.text(span.tailStart, span.newlineStart - 1));
    out.append(text);
    endLine();
    return true;
  }

  /**
   * The last offset of a value as the document it was read from wrote it.
   *
   * @param value The value.
   * @return The last offset of its literal, or of the closing bracket of a table or array that has not been edited
   *         since, or {@code -1} if the value has no span or was edited.
   */
  private static int writtenValueStop(Value value) {
    if (value instanceof Value.Scalar) {
      ValueSpan span = ((Value.Scalar) value).span;
      return (span != null) ? span.stop : -1;
    }
    ElementContainer<?> container = (ElementContainer<?>) value;
    ValueSpan brackets = container.bracketSpan;
    return (brackets != null && !container.isModified()) ? brackets.stop : -1;
  }

  /**
   * Write the value an entry holds on its own line: the literal the document wrote it as, where the notation is kept
   * and the value has a span, and otherwise the value in the default style.
   *
   * @param value The value.
   * @param lineIndent The indentation of the line the value starts on.
   * @param column The width of that line before the value, in code points.
   */
  private void writeEntryValue(TomlValue value, String lineIndent, int column) throws IOException {
    String literal = keepNotation ? literalOf(value) : null;
    if (literal != null) {
      out.append(literal);
      return;
    }
    writeLineValue(value.get(), lineIndent, column);
  }

  /**
   * Write the value an entry holds partway along a line, the literal it was written as where the notation is kept.
   *
   * @param value The value.
   * @param lineIndent The indentation of the line the value starts on.
   * @param column The width of that line before the value, in code points.
   * @param trailing The width of what follows the value on its last line, in code points.
   */
  private void writeNestedValue(TomlValue value, String lineIndent, int column, int trailing) throws IOException {
    String literal = keepNotation ? literalOf(value) : null;
    if (literal != null) {
      out.append(literal);
      return;
    }
    writeValue(value.get(), lineIndent, column, trailing);
  }

  /**
   * Write the value of a {@code key = value} line. A multi-line string is written only here.
   *
   * @param value The value.
   * @param lineIndent The indentation of the line the value starts on.
   * @param column The width of that line before the value, in code points.
   */
  private void writeLineValue(Object value, String lineIndent, int column) throws IOException {
    if (value instanceof String && ((String) value).indexOf('\n') >= 0) {
      // A string with a newline is written as a multi-line basic string, so it stays readable rather than escaped
      // onto one line. The key's indentation applies only to this line; content lines are never indented.
      StringBuilder text = new StringBuilder();
      appendMultilineString(text, (String) value, lineSeparator);
      out.append(text);
    } else {
      writeValue(value, lineIndent, column, 0);
    }
  }

  /**
   * Write a value that starts partway along a line. Unlike {@link #writeLineValue(Object, String, int)}, a string is
   * always written on one line: this writer writes a multi-line string only as the value of a {@code key = value} line.
   *
   * @param value The value.
   * @param lineIndent The indentation of the line the value starts on.
   * @param column The width of that line before the value, in code points.
   * @param trailing The width of what follows the value on its last line, in code points.
   */
  private void writeValue(Object value, String lineIndent, int column, int trailing) throws IOException {
    if (holdsComments(value)) {
      if (value instanceof TomlTable && !version.after(TomlVersion.V1_0_0)) {
        throw new IllegalArgumentException(
            "An inline table holding a comment cannot be written for TOML 1.0.0, which allows no line break inside an inline table");
      }
      writeOverLines(value, lineIndent);
      return;
    }

    StringBuilder text = new StringBuilder();
    if (!canWriteOverLines(value)) {
      appendInline(value, text, Integer.MAX_VALUE, keepNotation);
      out.append(text);
      return;
    }

    int width = maxLineWidth - column - trailing;
    // A code point takes at most two chars, so text longer than twice the width cannot fit and need not be completed
    int limit = width > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : 2 * width;
    if (width >= 0
        && appendInline(value, text, limit, keepNotation)
        && text.codePointCount(0, text.length()) <= width) {
      out.append(text);
      return;
    }
    writeOverLines(value, lineIndent);
  }

  /**
   * Whether a value without comments may be written over several lines: a non-empty array, or a non-empty inline table
   * when writing a version of TOML after 1.0.0, since 1.0.0 allows no line break inside an inline table.
   *
   * @param value The value.
   */
  private boolean canWriteOverLines(Object value) {
    if (value instanceof TomlArray) {
      return !((TomlArray) value).isEmpty();
    }
    return value instanceof TomlTable && version.after(TomlVersion.V1_0_0) && !((TomlTable) value).isEmpty();
  }

  /**
   * Write an array or inline table over several lines, with each element on its own line: one that does not fit on one
   * line, or one holding a comment, since a comment ends at a line break.
   *
   * @param container The array or inline table.
   * @param lineIndent The indentation of the line the container starts on.
   */
  private void writeOverLines(Object container, String lineIndent) throws IOException {
    boolean isTable = container instanceof TomlTable;
    List<TomlElement> elements = isTable ? ((TomlTable) container).elements() : ((TomlArray) container).elements();
    String elementIndent = lineIndent + ARRAY_ELEMENT_INDENT;
    out.append(isTable ? '{' : '[');
    endLine();
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlComment) {
        writeCommentLines((TomlComment) element, elementIndent);
        // Without a blank line the run would join the next run, or be attached ABOVE the next element
        if ((i + 1) < elements.size()) {
          blankLine();
        }
        continue;
      }
      TomlEntry entry = (TomlEntry) element;
      writeCommentAbove(entry.comments(), elementIndent);
      beginLine(elementIndent);
      int column = width(elementIndent);
      if (isTable) {
        StringBuilder prefix = new StringBuilder();
        appendEntryKey(prefix, Collections.singletonList(((TomlKeyValue) entry).key()), entry, keepNotation);
        prefix.append(" = ");
        out.append(prefix);
        column += prefix.codePointCount(0, prefix.length());
      }
      writeNestedValue(entry.value(), elementIndent, column, 1);
      out.append(',');
      writeCommentAfter(entry.comments());
      endLine();
    }
    beginLine(lineIndent);
    out.append(isTable ? '}' : ']');
  }

  /**
   * Write an unattached comment, with a blank line below it so that a re-parse does not attach it ABOVE what follows.
   *
   * @param comment The comment.
   * @param lineIndent The indentation of the lines around it.
   * @param blankAbove Whether to separate it from the line above with a blank line. A run written directly under a line
   *        belongs to the container open where it was written, which is this container; a separated run belongs to the
   *        container of the expression that follows it.
   */
  private void writeUnattachedComment(TomlComment comment, String lineIndent, boolean blankAbove) throws IOException {
    if (blankAbove) {
      blankLine();
    }
    writeCommentLines(comment, lineIndent);
    blankLine();
  }

  private void writeCommentAbove(List<TomlComment> comments, String lineIndent) throws IOException {
    for (TomlComment comment : comments) {
      if (comment.placement() == TomlComment.Placement.ABOVE) {
        writeCommentLines(comment, lineIndent);
      }
    }
  }

  private void writeCommentAfter(List<TomlComment> comments) throws IOException {
    TomlComment after = null;
    for (TomlComment comment : comments) {
      if (comment.placement() == TomlComment.Placement.AFTER) {
        after = comment;
      }
    }
    if (after != null) {
      out.append("  #").append(after.rawLines().get(0));
    }
  }

  void writeCommentLines(TomlComment comment, String lineIndent) throws IOException {
    for (String rawLine : comment.rawLines()) {
      beginLine(lineIndent);
      out.append('#').append(rawLine);
      endLine();
    }
  }

  /**
   * Request a blank line, which is written once a line follows it. Blank lines never double up, and the output neither
   * starts nor ends with one.
   */
  private void blankLine() {
    if (written) {
      blankLineOwed = true;
    }
  }

  private void beginLine(String lineIndent) throws IOException {
    if (blankLineOwed) {
      out.append(lineSeparator);
      blankLineOwed = false;
    }
    out.append(lineIndent);
  }

  /**
   * The indentation of the lines of a table whose path has a number of keys.
   *
   * @param depth The number of keys in the path.
   * @return The indentation.
   */
  private String indentFor(int depth) {
    return spaces(depth * indent);
  }

  private static String spaces(int width) {
    StringBuilder text = new StringBuilder(width);
    for (int i = 0; i < width; i++) {
      text.append(' ');
    }
    return text.toString();
  }

  /**
   * The width of an indentation, in code points, every character of which is one column wide.
   *
   * @param lineIndent The indentation.
   * @return Its width.
   */
  private static int width(String lineIndent) {
    return lineIndent.codePointCount(0, lineIndent.length());
  }

  private void endLine() throws IOException {
    out.append(lineSeparator);
    written = true;
  }

  /**
   * Append the single-line form of a value an entry holds, the literal it was written as where those are kept.
   *
   * @param value The value.
   * @param text The text to append to.
   * @param limit The length, in chars, that the text may reach.
   * @param literals Whether a value that has a span is written with the text of that span.
   * @return {@code false} if the text grew longer than {@code limit}, in which case the value may be incomplete.
   */
  private static boolean appendInlineEntryValue(TomlValue value, StringBuilder text, int limit, boolean literals) {
    String literal = literals ? literalOf(value) : null;
    if (literal != null) {
      text.append(literal);
      return text.length() <= limit;
    }
    return appendInline(value.get(), text, limit, literals);
  }

  /**
   * Append the single-line form of a value, giving up once the text is longer than a limit.
   *
   * @param value The value.
   * @param text The text to append to.
   * @param limit The length, in chars, that the text may reach.
   * @param literals Whether a value that has a span is written with the text of that span.
   * @return {@code false} if the text grew longer than {@code limit}, in which case the value may be incomplete.
   */
  private static boolean appendInline(Object value, StringBuilder text, int limit, boolean literals) {
    Optional<TomlType> tomlType = typeFor(value);
    assert tomlType.isPresent();
    switch (tomlType.get()) {
      case STRING:
        appendString(text, (String) value);
        break;
      case INTEGER:
        text.append((long) (Long) value);
        break;
      case FLOAT:
        appendFloat(text, (Double) value);
        break;
      case OFFSET_DATE_TIME:
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo((OffsetDateTime) value, text);
        break;
      case LOCAL_DATE_TIME:
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.formatTo((LocalDateTime) value, text);
        break;
      case LOCAL_DATE:
        DateTimeFormatter.ISO_LOCAL_DATE.formatTo((LocalDate) value, text);
        break;
      case LOCAL_TIME:
        DateTimeFormatter.ISO_LOCAL_TIME.formatTo((LocalTime) value, text);
        break;
      case BOOLEAN:
        text.append((boolean) (Boolean) value);
        break;
      case ARRAY:
        return appendInlineArray((TomlArray) value, text, limit, literals);
      case TABLE:
        return appendInlineTable((TomlTable) value, text, limit, literals);
    }
    return text.length() <= limit;
  }

  private static boolean appendInlineArray(TomlArray array, StringBuilder text, int limit, boolean literals) {
    text.append('[');
    // The array holds no comment, so every element of it is an entry
    List<TomlElement> elements = array.elements();
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        text.append(", ");
      }
      if (!appendInlineEntryValue(((TomlEntry) elements.get(i)).value(), text, limit, literals)) {
        return false;
      }
    }
    text.append(']');
    return text.length() <= limit;
  }

  private static boolean appendInlineTable(TomlTable table, StringBuilder text, int limit, boolean literals) {
    List<InlineItem> items = inlineItems(table, literals);
    if (items.isEmpty()) {
      text.append("{}");
      return text.length() <= limit;
    }
    text.append("{ ");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        text.append(", ");
      }
      InlineItem item = items.get(i);
      appendEntryKey(text, item.keyPath, item.entry, literals);
      text.append(" = ");
      if (!appendInlineEntryValue(item.entry.value(), text, limit, literals)) {
        return false;
      }
    }
    text.append(" }");
    return text.length() <= limit;
  }

  /**
   * The entries written between the braces of an inline table: its own entries, and, where the literal forms of a
   * document are kept, the entries of the tables its dotted keys opened, which are written between those braces with
   * the dotted keys the document wrote them with.
   *
   * @param table The inline table.
   * @param literals Whether the document's own dotted keys are written.
   * @return The entries, in the order they are written.
   */
  private static List<InlineItem> inlineItems(TomlTable table, boolean literals) {
    List<InlineItem> items = new ArrayList<>();
    collectInlineItems(table, Collections.emptyList(), literals, items);
    if (literals) {
      orderInlineItems(items);
    }
    return items;
  }

  private static void collectInlineItems(
      TomlTable table,
      List<String> relative,
      boolean literals,
      List<InlineItem> items) {
    for (TomlElement element : table.elements()) {
      TomlKeyValue pair = (TomlKeyValue) element;
      List<String> keyPath = keyPath(relative, pair.key());
      if (literals && writtenAsDottedKeys(pair)) {
        collectInlineItems((TomlTable) pair.value().get(), keyPath, literals, items);
      } else {
        items.add(new InlineItem(keyPath, pair));
      }
    }
  }

  /**
   * Put the entries of an inline table in the order the document wrote them: an entry the document wrote sorts where it
   * was written, and one it did not after the entry written before it. A dotted key opens a table of its own, so the
   * entries of <code>{ a.b = 1, c = 2, a.d = 3 }</code> are collected a table at a time rather than in the order they
   * were written in.
   *
   * @param items The entries, in the order the tables holding them were walked.
   */
  @SuppressWarnings("ReferenceEquality") // compares the key spans' sources by identity
  private static void orderInlineItems(List<InlineItem> items) {
    Source source = null;
    int previous = -1;
    for (int i = 0; i < items.size(); i++) {
      InlineItem item = items.get(i);
      item.order = i;
      SourceSpan span = writtenKeySpan(item.entry, item.keyPath.size());
      if (span != null && (source == null || span.source == source)) {
        source = span.source;
        item.anchor = span.start;
        previous = Math.max(previous, span.stop);
      } else {
        item.anchor = previous;
      }
    }
    items.sort(Comparator.comparingInt((InlineItem item) -> item.anchor).thenComparingInt(item -> item.order));
  }

  /**
   * Whether an entry of an inline table holds a table that is written as the dotted keys of that inline table rather
   * than as a table of its own.
   *
   * <p>
   * A dotted key writes one entry, so a table with no entries cannot be written as dotted keys, and a comment written
   * among those keys would be read in the inline table rather than in this one, which is also why a table whose own
   * entry carries a comment is written as a value of its own.
   *
   * @param pair The entry of the inline table.
   * @return {@code true} if the entry holds such a table.
   */
  private static boolean writtenAsDottedKeys(TomlKeyValue pair) {
    Object value = pair.value().get();
    if (!pair.comments().isEmpty() || !(value instanceof LinkedTomlTable) || ((LinkedTomlTable) value).isInline()) {
      return false;
    }
    boolean hasEntry = false;
    for (TomlElement element : ((LinkedTomlTable) value).elements()) {
      if (element instanceof TomlComment) {
        return false;
      }
      hasEntry = true;
    }
    return hasEntry;
  }

  private static List<String> keyPath(List<String> relative, String key) {
    List<String> keyPath = new ArrayList<>(relative.size() + 1);
    keyPath.addAll(relative);
    keyPath.add(key);
    return keyPath;
  }

  /**
   * Append the key of an entry: the key as the document wrote it, where the notation is kept and the key has as many
   * parts as the key it is written with here, and otherwise the key in the default style.
   *
   * @param text The text to append to.
   * @param keyPath The key, as the keys of a dotted key.
   * @param entry The entry the key names.
   * @param literals Whether a key that has a span is written with the text of that span.
   */
  private static void appendEntryKey(StringBuilder text, List<String> keyPath, TomlEntry entry, boolean literals) {
    SourceSpan span = literals ? writtenKeySpan(entry, keyPath.size()) : null;
    if (span != null) {
      text.append(span.source.text(span.keyStart, span.keyStop));
    } else {
      appendKeyPath(text, keyPath);
    }
  }

  /**
   * Where an entry's key was written, if the entry has a span and its key has as many parts as the key it is written
   * with here.
   *
   * @param entry The entry.
   * @param parts The number of keys the key is written with here, which the key as written must have as many of.
   * @return The span of the line or element the entry was written as, or {@code null}.
   */
  @Nullable
  private static SourceSpan writtenKeySpan(TomlEntry entry, int parts) {
    if (!(entry instanceof Entry)) {
      return null;
    }
    SourceSpan span = ((Entry) entry).span;
    return (span != null && span.keyStart >= 0 && span.keyParts == parts) ? span : null;
  }

  /**
   * The literal a scalar was written as, where it has a span.
   *
   * @param value The value.
   * @return The literal, or {@code null} if the value is a table or array, was set through the editing API, or was read
   *         from a document parsed with no source kept.
   */
  @Nullable
  private static String literalOf(TomlValue value) {
    if (!(value instanceof Value.Scalar)) {
      return null;
    }
    ValueSpan span = ((Value.Scalar) value).span;
    return (span == null) ? null : span.source.text(span.start, span.stop);
  }

  /**
   * Append a key, as the keys of a dotted key.
   *
   * @param text The text to append to.
   * @param keyPath The key.
   */
  private static void appendKeyPath(StringBuilder text, List<String> keyPath) {
    for (int i = 0; i < keyPath.size(); i++) {
      if (i > 0) {
        text.append('.');
      }
      appendKey(text, keyPath.get(i));
    }
  }

  private static void appendKey(StringBuilder text, String key) {
    if (BARE_KEY.matcher(key).matches()) {
      text.append(key);
    } else {
      appendString(text, key);
    }
  }

  /**
   * Append a single-line basic string. Unlike {@link Toml#tomlEscape(String)}, this escapes only the characters that
   * TOML requires to be escaped, and writes non-ASCII characters as they are.
   */
  private static void appendString(StringBuilder text, String value) {
    text.append('"');
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '"') {
        text.append("\\\"");
      } else {
        appendEscapedChar(text, ch);
      }
    }
    text.append('"');
  }

  /**
   * Append a multi-line basic string: the delimiter, a line separator, the content, then the delimiter again. The
   * leading line separator is trimmed by a parser, so a value that itself starts with a newline still round-trips.
   *
   * <p>
   * A newline in the value is written as {@code lineSeparator} rather than escaped, which is what makes the result
   * multi-line. A quote is written raw unless it would be the third in a row, since three raw quotes in a row would
   * either close the string early or (at the very end of the value) be mistaken for part of the closing delimiter; the
   * third is escaped instead, which also resets the run so later quotes may again be written raw.
   */
  private static void appendMultilineString(StringBuilder text, String value, String lineSeparator) {
    text.append("\"\"\"").append(lineSeparator);
    int rawQuoteRun = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '\n') {
        text.append(lineSeparator);
        rawQuoteRun = 0;
      } else if (ch == '"') {
        if (rawQuoteRun >= 2) {
          text.append("\\\"");
          rawQuoteRun = 0;
        } else {
          text.append('"');
          rawQuoteRun++;
        }
      } else {
        appendEscapedChar(text, ch);
        rawQuoteRun = 0;
      }
    }
    text.append("\"\"\"");
  }

  /**
   * Append a character as it appears inside a basic string, other than a quote (which a caller escapes conditionally,
   * or always for a single-line string) and, inside a multi-line string, a newline (which a caller writes as the line
   * separator instead).
   */
  private static void appendEscapedChar(StringBuilder text, char ch) {
    switch (ch) {
      case '\\':
        text.append("\\\\");
        break;
      case '\b':
        text.append("\\b");
        break;
      case '\t':
        text.append("\\t");
        break;
      case '\n':
        text.append("\\n");
        break;
      case '\f':
        text.append("\\f");
        break;
      case '\r':
        text.append("\\r");
        break;
      default:
        if (ch < 0x20 || ch == 0x7F) {
          text.append("\\u00").append(HEX_DIGITS[ch >> 4]).append(HEX_DIGITS[ch & 0xF]);
        } else {
          text.append(ch);
        }
    }
  }

  private static void appendFloat(StringBuilder text, double value) {
    if (Double.isNaN(value)) {
      text.append("nan");
    } else if (Double.isInfinite(value)) {
      text.append(value > 0 ? "inf" : "-inf");
    } else {
      text.append(value);
    }
  }

  /**
   * Whether an entry is written as a {@code key = value} line, rather than as a sub-table or an array of tables.
   *
   * <p>
   * An array whose elements are all tables is written as one {@code [[header]]} section per element, which carries
   * neither the comments written in the array itself nor those attached to the entry holding it: a re-parse would read
   * both as the comments of the first element. Such an array is written as a {@code key = value} line, where the
   * comments of the entry and of the array keep their placement.
   *
   * <p>
   * Where the notation is kept, a table read as an inline table, and an array read between brackets, are written on the
   * entry's line as they were read, wherever the value was read from: the document being written or another one.
   *
   * @param entry The entry.
   * @param keepNotation Whether the form a value was read in is kept.
   */
  static boolean isLine(TomlEntry entry, boolean keepNotation) {
    Object value = entry.value().get();
    if (value instanceof TomlTable) {
      return keepNotation && value instanceof LinkedTomlTable && ((LinkedTomlTable) value).hasInlineForm();
    }
    if (!(value instanceof TomlArray)) {
      return true;
    }
    TomlArray array = (TomlArray) value;
    if (array.isEmpty()) {
      return true;
    }
    if (keepNotation && array instanceof ListTomlArray && ((ListTomlArray) array).hasInlineForm()) {
      return true;
    }
    for (TomlElement element : array.elements()) {
      if (element instanceof TomlComment || !(((TomlEntry) element).value().get() instanceof TomlTable)) {
        return true;
      }
    }
    return !entry.comments().isEmpty();
  }

  /**
   * Whether a table needs a {@code [header]} of its own, or is implied by the headers of its sub-tables.
   *
   * @param table The table.
   * @param comments The comments attached to the entry holding it, which are written on its header.
   */
  private boolean needsHeader(TomlTable table, List<TomlComment> comments) {
    if (!comments.isEmpty()) {
      return true;
    }
    boolean hasSection = false;
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment || isLine((TomlEntry) element, keepNotation)) {
        return true;
      }
      hasSection = true;
    }
    return !hasSection;
  }

  /**
   * Whether a value is a table or array holding a comment, at any depth, and so cannot be written on one line.
   *
   * @param value The value.
   */
  private static boolean holdsComments(Object value) {
    List<TomlElement> elements;
    if (value instanceof TomlTable) {
      elements = ((TomlTable) value).elements();
    } else if (value instanceof TomlArray) {
      elements = ((TomlArray) value).elements();
    } else {
      return false;
    }
    for (TomlElement element : elements) {
      if (element instanceof TomlComment) {
        return true;
      }
      TomlEntry entry = (TomlEntry) element;
      if (!entry.comments().isEmpty() || holdsComments(entry.value().get())) {
        return true;
      }
    }
    return false;
  }

  private static int firstSectionIndex(List<TomlElement> elements, boolean keepNotation) {
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlEntry && !isLine((TomlEntry) element, keepNotation)) {
        return i;
      }
    }
    return -1;
  }

  private static int lastLineIndex(List<TomlElement> elements, boolean keepNotation) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlEntry && isLine((TomlEntry) element, keepNotation)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * One entry written between the braces of an inline table: the entry, and the key it is written with there, which is
   * a dotted key for an entry of a table one of the inline table's own dotted keys opened.
   */
  private static final class InlineItem {

    private final List<String> keyPath;

    private final TomlEntry entry;

    /** The offset this entry sorts at: where it was written, or where the entry written before it ends. */
    private int anchor;

    /** The order this entry was collected in, which orders the entries anchored at the same offset. */
    private int order;

    InlineItem(List<String> keyPath, TomlEntry entry) {
      this.keyPath = keyPath;
      this.entry = entry;
    }
  }
}
