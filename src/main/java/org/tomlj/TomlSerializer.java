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
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Writes tables and arrays as TOML.
 *
 * <p>
 * The table being written is the document root. Each table writes, in the order they were written in it, the entries
 * that are written as {@code key = value} lines and the comments unattached to any entry; then its sub-tables and
 * arrays of tables, in the same order. A sub-table is written under a {@code [header]} naming its path from the root,
 * unless it holds only sub-tables and arrays of tables, whose headers imply it. Each table in an array of tables is
 * written under a {@code [[header]]}. Tables and arrays inside arrays are written inline.
 *
 * <p>
 * An array is written on one line if that line fits within the maximum line width, and otherwise with each element on
 * its own line.
 *
 * <p>
 * A string is written as a multi-line basic string when it is the value of a {@code key = value} line and contains a
 * newline. Every other string, whether a key or a value in an array or inline table, is written as a single-line basic
 * string.
 *
 * <p>
 * Comments are written from the model, never copied from source text: the run above an entry or header, the comment
 * after it, and the comments unattached to any entry, in the container they were written in. An array or inline table
 * holding a comment is written over several lines, since a comment ends at a line break. Where a comment is written
 * decides which container a re-parse reads it in, which is what the blank lines around an unattached comment are for: a
 * run separated by a blank line from what follows it belongs to the container of the next expression, and a run written
 * directly under a line belongs to the container open where it was written.
 */
final class TomlSerializer {
  private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  // The indentation of the elements of a multi-line array, relative to the line the array starts on
  private static final String ARRAY_ELEMENT_INDENT = "  ";

  private final Appendable out;
  // The spaces to indent per level of table nesting
  private final int indent;
  // The widest a single-line array's line may be, in code points
  private final int maxLineWidth;
  private final String lineSeparator;
  // Whether a line whose text the document it was read from still holds is copied rather than written anew. Set only
  // for a block of a document being written from its source, where a copied entry keeps the literal it was read as.
  private final boolean copySourceLines;
  private boolean written = false;
  // A blank line owed to the output, written only once something follows it, so that the document never ends with one
  private boolean blankLineOwed = false;

  private TomlSerializer(Appendable out, TomlOptions options, boolean copySourceLines) {
    this.out = out;
    this.indent = options.indent();
    this.maxLineWidth = options.maxLineWidth();
    this.lineSeparator = options.lineSeparator();
    this.copySourceLines = copySourceLines;
  }

  /**
   * A writer of the default style.
   *
   * @param out The output.
   * @param options The options to write with.
   * @return A writer.
   */
  static TomlSerializer defaultStyle(Appendable out, TomlOptions options) {
    return new TomlSerializer(out, options, false);
  }

  /**
   * A writer of a block of a document being written from the text it was parsed from: the default style, except that a
   * line the document still holds the text of is copied rather than written anew, so that a copied entry keeps the
   * literal and the spacing it was read with.
   *
   * @param out The output.
   * @param options The options to write with.
   * @return A writer.
   */
  static TomlSerializer blockStyle(Appendable out, TomlOptions options) {
    return new TomlSerializer(out, options, true);
  }

  static void toToml(TomlTable table, Appendable appendable, TomlOptions options) throws IOException {
    requireNonNull(table);
    requireNonNull(appendable);
    requireNonNull(options);
    if (options.style() != TomlOptions.Style.CANONICAL && table instanceof ParsedTomlTable) {
      ParsedTomlTable parsed = (ParsedTomlTable) table;
      if (parsed.source() != null) {
        SourcePreservingSerializer.toToml(parsed, appendable, options);
        return;
      }
    }
    defaultStyle(appendable, options).writeEntries(table, new ArrayList<>());
  }

  static void toToml(TomlArray array, Appendable appendable, TomlOptions options) throws IOException {
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
   * section, so writing it among the values is what keeps it in this table.
   *
   * @param table The table.
   * @param path The keys from the root to the table, or to the array holding it. Restored before returning.
   */
  private void writeEntries(TomlTable table, List<String> path) throws IOException {
    List<TomlElement> elements = table.elements();
    String lineIndent = indentFor(path.size());
    int firstSection = path.isEmpty() ? firstSectionIndex(elements) : -1;
    int lastLine = lastLineIndex(elements);
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlComment) {
        if (firstSection < 0 || i < firstSection) {
          writeUnattachedComment((TomlComment) element, lineIndent, i < lastLine);
        }
      } else if (isLine((TomlEntry) element)) {
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
      if (isLine(entry)) {
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
    appendKeyPath(prefix, keyPath);
    prefix.append(" = ");
    out.append(prefix);
    writeLineValue(entry.value().get(), lineIndent, width(lineIndent) + prefix.codePointCount(0, prefix.length()));
    writeCommentAfter(comments);
    endLine();
  }

  /**
   * Write a line from the text the document it was read from still holds: its key, the spacing around the {@code =},
   * its value and the comment after it, as they were written. The key is written anew when the line was written with a
   * dotted key that no longer names the entry from here, and the line ends with this writer's own line separator, since
   * it is being written somewhere the document did not have it.
   *
   * @param lineIndent The indentation of the line.
   * @param keyPath The key, as the keys of a dotted key.
   * @param entry The entry the line holds.
   * @return {@code false} if the document holds no text for this line, or holds text that no longer describes it.
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
   *         since, or {@code -1} if the document holds no text that still describes the value.
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
   * Write the value of a {@code key = value} line, the only place a multi-line string is written.
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
   * always written on one line, as it must be everywhere but as the value of a {@code key = value} line.
   *
   * @param value The value.
   * @param lineIndent The indentation of the line the value starts on.
   * @param column The width of that line before the value, in code points.
   * @param trailing The width of what follows the value on its last line, in code points.
   */
  private void writeValue(Object value, String lineIndent, int column, int trailing) throws IOException {
    if (holdsComments(value)) {
      writeOverLines(value, lineIndent);
      return;
    }

    StringBuilder text = new StringBuilder();
    if (!(value instanceof TomlArray) || ((TomlArray) value).isEmpty()) {
      appendInline(value, text, Integer.MAX_VALUE);
      out.append(text);
      return;
    }

    int width = maxLineWidth - column - trailing;
    // A code point takes at most two chars, so text longer than twice the width cannot fit and need not be completed
    int limit = width > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : 2 * width;
    if (width >= 0 && appendInline(value, text, limit) && text.codePointCount(0, text.length()) <= width) {
      out.append(text);
      return;
    }

    TomlArray array = (TomlArray) value;
    String elementIndent = lineIndent + ARRAY_ELEMENT_INDENT;
    int elementColumn = width(elementIndent);
    out.append('[');
    endLine();
    for (int i = 0; i < array.size(); i++) {
      beginLine(elementIndent);
      writeValue(array.get(i), elementIndent, elementColumn, 1);
      out.append(',');
      endLine();
    }
    beginLine(lineIndent);
    out.append(']');
  }

  /**
   * Write an array or inline table holding a comment, with each element on its own line, since a comment ends at a line
   * break.
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
        // Without a blank line the run would run into the next one, or be read as the comment above the next element
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
        appendKey(prefix, ((TomlKeyValue) entry).key());
        prefix.append(" = ");
        out.append(prefix);
        column += prefix.codePointCount(0, prefix.length());
      }
      writeValue(entry.value().get(), elementIndent, column, 1);
      out.append(',');
      writeCommentAfter(entry.comments());
      endLine();
    }
    beginLine(lineIndent);
    out.append(isTable ? '}' : ']');
  }

  /**
   * Write a comment that documents nothing, with a blank line below it so that a re-parse does not read it as the
   * comment above whatever follows.
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
   * Owe the output a blank line, which is written once a line follows it. Blank lines never double up, and the output
   * neither starts nor ends with one.
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
   * Append the single-line form of a value, giving up once the text is longer than a limit.
   *
   * @param value The value.
   * @param text The text to append to.
   * @param limit The length, in chars, that the text may reach.
   * @return {@code false} if the text grew longer than {@code limit}, in which case the value may be incomplete.
   */
  private static boolean appendInline(Object value, StringBuilder text, int limit) {
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
        return appendInlineArray((TomlArray) value, text, limit);
      case TABLE:
        return appendInlineTable((TomlTable) value, text, limit);
    }
    return text.length() <= limit;
  }

  private static boolean appendInlineArray(TomlArray array, StringBuilder text, int limit) {
    text.append('[');
    for (int i = 0; i < array.size(); i++) {
      if (i > 0) {
        text.append(", ");
      }
      if (!appendInline(array.get(i), text, limit)) {
        return false;
      }
    }
    text.append(']');
    return text.length() <= limit;
  }

  private static boolean appendInlineTable(TomlTable table, StringBuilder text, int limit) {
    List<TomlElement> elements = table.elements();
    if (elements.isEmpty()) {
      text.append("{}");
      return text.length() <= limit;
    }
    text.append("{ ");
    boolean first = true;
    for (TomlElement element : elements) {
      if (!first) {
        text.append(", ");
      }
      first = false;
      TomlKeyValue pair = (TomlKeyValue) element;
      appendKey(text, pair.key());
      text.append(" = ");
      if (!appendInline(pair.value().get(), text, limit)) {
        return false;
      }
    }
    text.append(" }");
    return text.length() <= limit;
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
   * both as the comments of the first element. Such an array stays a {@code key = value} line, where every comment
   * around it keeps the place it was written in.
   */
  static boolean isLine(TomlEntry entry) {
    Object value = entry.value().get();
    if (value instanceof TomlTable) {
      return false;
    }
    if (!(value instanceof TomlArray)) {
      return true;
    }
    TomlArray array = (TomlArray) value;
    if (array.isEmpty()) {
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
  private static boolean needsHeader(TomlTable table, List<TomlComment> comments) {
    if (!comments.isEmpty()) {
      return true;
    }
    boolean hasSection = false;
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment || isLine((TomlEntry) element)) {
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

  private static int firstSectionIndex(List<TomlElement> elements) {
    for (int i = 0; i < elements.size(); i++) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlEntry && !isLine((TomlEntry) element)) {
        return i;
      }
    }
    return -1;
  }

  private static int lastLineIndex(List<TomlElement> elements) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlEntry && isLine((TomlEntry) element)) {
        return i;
      }
    }
    return -1;
  }
}
