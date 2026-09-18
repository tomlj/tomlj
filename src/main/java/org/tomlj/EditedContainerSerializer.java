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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Writes an array or inline table that the editing API changed, within the brackets it was read in.
 *
 * <p>
 * The elements of a container partition the text between its brackets: each covers the whitespace before it, the
 * comment run above it, its key and value, and the tail after the value - the comma, the comment after it and the
 * newline ending its line. An element that was not replaced is written from that text, so it keeps the literal it was
 * read as, the comments around it and the layout it was written in; one that was added or replaced is written in the
 * default style, laid out like the elements around it. A removed element takes its text with it, which leaves the text
 * around it to be made good: a comma is written where two elements no longer have one between them, and the comma after
 * the last element is dropped unless the document wrote one there.
 *
 * <p>
 * An inline table is written from its own entries and from the entries of the tables its dotted keys opened, each in
 * the place the document wrote it: {@code { a.b = 1, c = 2, a.d = 3 }} is read as two tables but written in the order
 * it was read.
 *
 * <p>
 * A container the document wrote on one line is written over lines when a comment has to be written in it, since a
 * comment ends at a line break. The text between elements then no longer describes where they go, so only the elements
 * themselves are written from it.
 */
final class EditedContainerSerializer {

  /**
   * Where the container being written sits, which decides how a value written anew inside it is laid out.
   */
  enum Context {
    /** The value of a {@code key = value} line, the only place a multi-line string is written. */
    LINE,
    /** An element of an array. */
    ELEMENT,
    /** The value of an entry of an inline table, which is written on the one line. */
    ENTRY
  }

  // The indentation of the elements of a container written over lines, relative to the line the container starts on
  private static final String ELEMENT_INDENT = "  ";

  private final StringBuilder out;
  private final ValueSpan brackets;
  private final Source source;
  private final TomlOptions options;
  private final String lineSeparator;

  // The indentation of the line the container starts on, which its closing bracket is written at
  private final String lineIndent;

  // Whether the container is written over several lines
  private boolean multiLine;

  // Whether the container keeps the layout the document gave it, so that the text between its elements still says
  // where they go and can be copied along with them
  private boolean copyLayout;

  // The indentation of the elements of a container written over lines
  private String elementIndent = ELEMENT_INDENT;

  // Whether a comma follows the last element of the container, which is what the document wrote after its own last one
  private boolean trailingComma;

  // Where the comma separating the entry just written from the next one goes, if one is needed
  private int commaSlot = -1;

  // Whether the entry just written still needs a comma after it
  private boolean commaOwed;

  /**
   * Write an array or inline table that has been edited, within the brackets it was read in.
   *
   * @param text The output, which holds the line the container starts on.
   * @param container The array or inline table.
   * @param brackets The brackets the document wrote it in.
   * @param context Where the container itself is written, which lays it out if nothing of it can be copied.
   * @param options The options anything written anew is written with.
   * @throws IOException If the output throws.
   */
  static void append(
      StringBuilder text,
      ElementContainer<?> container,
      ValueSpan brackets,
      Context context,
      TomlOptions options) throws IOException {
    EditedContainerSerializer writer = new EditedContainerSerializer(text, brackets, options);
    TomlOptions containerOptions = ElementContainer.optionsWithin(container, options);
    if (containerOptions.style() != options.style()) {
      // The container is written in a style of its own, which keeps none of the text it was read in
      writer.appendValue(container, context, containerOptions);
      return;
    }
    writer.write(container, context);
  }

  private EditedContainerSerializer(StringBuilder out, ValueSpan brackets, TomlOptions options) {
    this.out = out;
    this.brackets = brackets;
    this.source = brackets.source;
    this.options = options;
    this.lineSeparator = options.lineSeparator();
    this.lineIndent = indentOfLastLine(out);
  }

  private void write(ElementContainer<?> container, Context context) throws IOException {
    boolean table = container instanceof LinkedTomlTable;
    List<Item> items = new ArrayList<>();
    if (table) {
      collectEntries((LinkedTomlTable) container, Collections.<String>emptyList(), items);
    } else {
      for (TomlElement element : container.elements()) {
        items.add(new Item(element, Collections.<String>emptyList(), source));
      }
    }
    order(items);
    if (!anySpanned(items)) {
      // Nothing the document wrote between the brackets is still here, so there is nothing to write it from
      appendValue(container, context, options);
      return;
    }
    layout(items);

    Context itemContext = table ? Context.ENTRY : Context.ELEMENT;
    out.append(source.text(brackets.start, brackets.start));
    Item previous = null;
    boolean afterComment = false;
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      String leading = adjacentLeading(item, previous);
      if (item.isEntry() && commaOwed && (leading == null || !holdsComma(leading))) {
        // The comma goes where the document would have written it, right after the value it follows
        out.insert(commaSlot, ',');
        commaOwed = false;
      }
      if (leading != null) {
        out.append(leading);
        if (holdsComma(leading)) {
          commaOwed = false;
        }
      } else {
        appendBoundary(previous == null, afterComment);
      }
      if (item.isEntry()) {
        appendEntry(item, itemContext);
        commaSlot = out.length();
        commaOwed = true;
        appendTail(item, entryFollows(items, i));
      } else {
        appendComment(item);
      }
      previous = item;
      afterComment = !item.isEntry();
    }
    appendTrailer(previous);
  }

  /**
   * Collect the items of an inline table: its entries, and the entries of the tables its dotted keys opened, which are
   * written between these braces with the dotted keys the document wrote them with.
   *
   * @param table The inline table, or a table one of its dotted keys opened.
   * @param relative The keys from the inline table to {@code table}.
   * @param items The items collected so far.
   */
  private void collectEntries(LinkedTomlTable table, List<String> relative, List<Item> items) {
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment) {
        items.add(new Item(element, Collections.<String>emptyList(), source));
        continue;
      }
      Entry.KeyValue pair = (Entry.KeyValue) element;
      List<String> keyPath = path(relative, pair.key());
      if (!pair.valueModified && pair.comments().isEmpty() && pair.value instanceof LinkedTomlTable) {
        LinkedTomlTable dotted = (LinkedTomlTable) pair.value;
        if (!dotted.isInline() && writtenAsDottedKeys(dotted)) {
          collectEntries(dotted, keyPath, items);
          continue;
        }
      }
      items.add(new Item(pair, keyPath, source));
    }
  }

  /**
   * Whether a table a dotted key opened in an inline table can still be written as dotted keys of it.
   *
   * <p>
   * A dotted key writes one entry, so a table with none has nothing to be written as and is written as an empty inline
   * table instead; and a comment written among those keys would be read in the inline table rather than in this one,
   * which is also why a table whose own entry carries a comment is written as a value of its own.
   *
   * @param table The table.
   * @return {@code true} if the table holds an entry and no unattached comment.
   */
  private static boolean writtenAsDottedKeys(LinkedTomlTable table) {
    boolean hasEntry = false;
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment) {
        return false;
      }
      hasEntry = true;
    }
    return hasEntry;
  }

  /**
   * Put the items in the order they are written: an item the document wrote sorts where it was written, and one it did
   * not sorts after the last item written before it in the container's own sequence.
   *
   * <p>
   * An inline table's dotted keys are collected a table at a time, so the items are not collected in the order the
   * document holds them.
   *
   * @param items The items.
   */
  private void order(List<Item> items) {
    int stop = brackets.start;
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      item.order = i;
      if (item.span != null) {
        item.anchor = item.span.start;
        stop = Math.max(stop, item.span.stop);
      } else {
        item.anchor = stop;
      }
    }
    items.sort(Comparator.comparingInt((Item item) -> item.anchor).thenComparingInt(item -> item.order));
  }

  /**
   * Work out how the container is laid out: over lines or on one, how far its elements are indented, and whether a
   * comma follows the last of them.
   *
   * @param items The items, in the order they are written.
   */
  private void layout(List<Item> items) {
    boolean sourceLines = holdsNewline(source.text(brackets.trailerStart, brackets.stop - 1));
    boolean ownLines = false;
    String indent = null;
    SourceSpan lastEntry = null;
    for (Item item : items) {
      SourceSpan span = item.span;
      if (span != null) {
        sourceLines = sourceLines || holdsNewline(source.text(span.start, span.stop));
        if (indent == null) {
          indent = startingIndent(span);
        }
        if (item.isEntry()) {
          lastEntry = span;
        }
      }
      ownLines = ownLines || needsOwnLine(item);
    }
    multiLine = sourceLines || ownLines;
    copyLayout = (multiLine == sourceLines);
    elementIndent = (indent != null) ? indent : (lineIndent + ELEMENT_INDENT);
    trailingComma = (lastEntry != null) && endsWithComma(source.text(lastEntry.tailStart, brackets.stop - 1));
  }

  /**
   * Whether an item has to be written on a line of its own, which is what a comment written in a container needs, since
   * a comment ends at a line break.
   *
   * @param item The item.
   * @return {@code true} if the item carries a comment.
   */
  private static boolean needsOwnLine(Item item) {
    if (!item.isEntry()) {
      return true;
    }
    Entry entry = item.entry();
    return !entry.comments().isEmpty() || TomlSerializer.holdsComments(entry.value.get());
  }

  /**
   * The whitespace before an item that is written as the document wrote it: the item's own, if the document wrote it
   * directly after the item written before it, and {@code null} if it did not, in which case the layout of the
   * container is written instead.
   *
   * @param item The item about to be written.
   * @param previous The item written before it, or {@code null} if it is the first.
   * @return The whitespace, or {@code null}.
   */
  @Nullable
  private String adjacentLeading(Item item, @Nullable Item previous) {
    if (!copyLayout || item.span == null) {
      return null;
    }
    boolean adjacent = (previous == null) ? (item.span.start == (brackets.start + 1))
        : (previous.span != null && (previous.span.stop + 1) == item.span.start);
    return adjacent ? source.text(item.span.start, firstToken(item.span) - 1) : null;
  }

  /**
   * Write what separates an item from the one before it, where the document has no text for it: the layout the rest of
   * the container uses.
   *
   * @param first Whether the item is the first written, and so follows the opening bracket.
   * @param afterComment Whether the item before it was a comment, which a blank line keeps unattached to this one.
   */
  private void appendBoundary(boolean first, boolean afterComment) {
    if (!multiLine) {
      out.append(first ? openingSpacing() : " ");
      return;
    }
    if (!endsWithNewline()) {
      out.append(lineSeparator);
    }
    if (afterComment) {
      out.append(lineSeparator);
    }
    out.append(elementIndent);
  }

  /**
   * Write one entry of the container: the comment run above it, its key, and its value.
   *
   * @param item The entry.
   * @param context Where a value written anew inside the container sits.
   */
  private void appendEntry(Item item, Context context) throws IOException {
    Entry entry = item.entry();
    SourceSpan span = item.span;
    TomlOptions valueOptions = ElementContainer.optionsWithin(entry.value, options);
    if (span == null) {
      appendCommentAbove(entry);
      appendKey(item.keyPath);
      appendValue(entry.value.get(), context, valueOptions);
      return;
    }
    int from;
    if (entry.commentsModified()) {
      appendCommentAbove(entry);
      from = (span.keyStart >= 0) ? span.keyStart : span.valueStart;
    } else {
      from = firstToken(span);
    }
    if (span.writtenValue(entry.value) != null) {
      out.append(source.text(from, span.tailStart - 1));
      return;
    }
    // The spacing around the '=' is the entry's own; the value it held is written anew
    out.append(source.text(from, span.valueStart - 1));
    ValueSpan nested = span.writtenBrackets(entry.value);
    if (nested != null) {
      append(out, (ElementContainer<?>) entry.value, nested, context, options);
    } else {
      appendValue(entry.value.get(), context, valueOptions);
    }
  }

  /**
   * Write what follows an entry's value: the comma separating it from the next one, the comment after it, and the
   * newline ending its line.
   *
   * @param item The entry.
   * @param entryFollows Whether another entry is written after it.
   */
  private void appendTail(Item item, boolean entryFollows) {
    Entry entry = item.entry();
    SourceSpan span = item.span;
    if (span == null) {
      if (appendCommentAfter(entry, "  ")) {
        // A comment runs to the end of its line, so what follows the entry starts on the next one
        out.append(lineSeparator);
      }
      return;
    }
    if (entry.commentsModified()) {
      // The comma the document wrote goes with the comment it was written around, so it is written again where it is
      // needed: before the comment, or by whatever follows the entry, which may hold it already
      boolean written = appendCommentAfter(entry, afterSpacing(span));
      String newline = newlineOf(span);
      // A comment runs to the end of its line, so whatever the document wrote after it on that line is moved off it
      out.append((written && newline.isEmpty()) ? lineSeparator : newline);
      return;
    }
    // The comma the document wrote after the last element of the container is what follows whatever ends up last
    boolean comma = entryFollows || trailingComma;
    String tail = (!comma && span.commaOffset >= 0)
        ? (source.text(span.tailStart, span.commaOffset - 1) + source.text(span.commaOffset + 1, span.stop))
        : source.text(span.tailStart, span.stop);
    out.append(tail);
    if (holdsComma(tail)) {
      commaOwed = false;
    }
  }

  /** Write the comment run above an entry from the model, leaving the output at the entry's own indentation. */
  private void appendCommentAbove(Entry entry) {
    for (TomlComment comment : entry.comments()) {
      if (comment.placement() != TomlComment.Placement.ABOVE) {
        continue;
      }
      for (String rawLine : comment.rawLines()) {
        // A run above an entry documents it only where each of its lines is a line of its own
        startElementLine();
        out.append('#').append(rawLine).append(lineSeparator).append(elementIndent);
      }
    }
  }

  /**
   * Write the comment after an entry from the model, if it has one.
   *
   * @param entry The entry.
   * @param spacing What separates the comment from what precedes it.
   * @return {@code true} if a comment was written.
   */
  private boolean appendCommentAfter(Entry entry, String spacing) {
    for (TomlComment comment : entry.comments()) {
      if (comment.placement() == TomlComment.Placement.AFTER) {
        out.append(spacing).append('#').append(comment.rawLines().get(0));
        return true;
      }
    }
    return false;
  }

  /**
   * Move the output onto the indentation of a line of its own, where it is not already on one, dropping the spacing the
   * line it leaves ended with.
   */
  private void startElementLine() {
    int lineStart = lastLineStart(out);
    int end = out.length();
    while (end > lineStart && (out.charAt(end - 1) == ' ' || out.charAt(end - 1) == '\t')) {
      end--;
    }
    if (end == lineStart) {
      return;
    }
    out.setLength(end);
    out.append(lineSeparator).append(elementIndent);
  }

  /** Write an unattached comment, from the document where it wrote one and from the model where it did not. */
  private void appendComment(Item item) {
    if (item.span != null) {
      out.append(source.text(item.span.aboveStart, item.span.stop));
      return;
    }
    List<String> rawLines = ((TomlComment) item.element).rawLines();
    for (int i = 0; i < rawLines.size(); i++) {
      if (i > 0) {
        out.append(elementIndent);
      }
      out.append('#').append(rawLines.get(i)).append(lineSeparator);
    }
  }

  /**
   * Write what closes the container: the whitespace before the closing bracket, and the bracket itself.
   *
   * @param previous The last item written, or {@code null} if the container has none.
   */
  private void appendTrailer(@Nullable Item previous) {
    if (commaOwed && trailingComma) {
      out.insert(commaSlot, ',');
    }
    boolean adjacent =
        copyLayout && previous != null && previous.span != null && (previous.span.stop + 1) == brackets.trailerStart;
    String trailer = source.text(brackets.trailerStart, brackets.stop - 1);
    if (adjacent) {
      out.append(trailer);
    } else if (multiLine) {
      if (!endsWithNewline()) {
        out.append(lineSeparator);
      }
      out.append(lineIndent);
    } else if (!holdsNewline(trailer)) {
      // The whitespace the document wrote before its closing bracket, which the element it followed took no part of
      out.append(trailer);
    }
    out.append(source.text(brackets.stop, brackets.stop));
  }

  /** Write the key of an entry of an inline table, as the keys of a dotted key. */
  private void appendKey(List<String> keyPath) {
    if (keyPath.isEmpty()) {
      return;
    }
    TomlSerializer.appendKeyPath(out, keyPath);
    out.append(" = ");
  }

  /**
   * Write a value in the default style, at the end of the line the output has reached.
   *
   * @param value The value.
   * @param context Where the value sits.
   * @param valueOptions The options the value is written with, which are these options unless it is written in a style
   *        of its own.
   */
  private void appendValue(Object value, Context context, TomlOptions valueOptions) throws IOException {
    if (context == Context.ENTRY && !TomlSerializer.holdsComments(value)) {
      // Everything inside an inline table stays on the one line, whatever the maximum line width is
      TomlSerializer.appendInlineValue(out, value, valueOptions.style() == TomlOptions.Style.PRETTIFY);
      return;
    }
    int lineStart = lastLineStart(out);
    String indent = indentOfLastLine(out);
    int column = out.codePointCount(lineStart, out.length());
    TomlSerializer serializer = TomlSerializer.defaultStyle(out, valueOptions);
    if (context == Context.LINE) {
      serializer.writeLineValue(value, indent, column);
    } else {
      serializer.writeValue(value, indent, column, 1);
    }
  }

  /** The spaces and tabs the document wrote directly after the opening bracket, which laid out its first element. */
  private String openingSpacing() {
    StringBuilder spacing = new StringBuilder();
    for (int offset = brackets.start + 1; offset < brackets.stop; offset++) {
      String character = source.text(offset, offset);
      if (!" ".equals(character) && !"\t".equals(character)) {
        break;
      }
      spacing.append(character);
    }
    return spacing.toString();
  }

  /**
   * The spacing the comment after an entry is written at: the document's own where it wrote one there, and the two
   * spaces of the default style where it did not.
   */
  private String afterSpacing(SourceSpan span) {
    if (span.afterStart < 0) {
      return "  ";
    }
    // The comment is written after the comma, wherever the document had it, so only a comma before it is skipped
    int from = (span.commaOffset >= 0 && span.commaOffset < span.afterStart) ? (span.commaOffset + 1) : span.tailStart;
    return source.text(from, span.afterStart - 1);
  }

  /** The newline ending an entry's line, or {@code ""} if its tail does not reach one. */
  private String newlineOf(SourceSpan span) {
    String tail = source.text(span.tailStart, span.stop);
    if (tail.endsWith("\r\n")) {
      return "\r\n";
    }
    return tail.endsWith("\n") ? "\n" : "";
  }

  /**
   * The indentation the document wrote an item at, where it wrote it on a line of its own: an item whose own line holds
   * nothing before it but spaces and tabs.
   *
   * @param span Where the item was written.
   * @return The indentation, or {@code null} if the item does not start a line.
   */
  @Nullable
  private String startingIndent(SourceSpan span) {
    String leading = source.text(span.start, firstToken(span) - 1);
    int lineStart = lastLineStart(leading);
    if (lineStart == 0 && !(span.start > 0 && "\n".equals(source.text(span.start - 1, span.start - 1)))) {
      return null;
    }
    String indent = leading.substring(lineStart);
    for (int i = 0; i < indent.length(); i++) {
      if (indent.charAt(i) != ' ' && indent.charAt(i) != '\t') {
        return null;
      }
    }
    return indent;
  }

  private boolean endsWithNewline() {
    return out.length() > 0 && out.charAt(out.length() - 1) == '\n';
  }

  private static boolean anySpanned(List<Item> items) {
    for (Item item : items) {
      if (item.span != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean entryFollows(List<Item> items, int index) {
    for (int i = index + 1; i < items.size(); i++) {
      if (items.get(i).isEntry()) {
        return true;
      }
    }
    return false;
  }

  /** The first offset of an item's own text, after the whitespace the document wrote before it. */
  private static int firstToken(SourceSpan span) {
    if (span.aboveStart >= 0) {
      return span.aboveStart;
    }
    return (span.keyStart >= 0) ? span.keyStart : span.valueStart;
  }

  /**
   * Whether a text written between two values holds the comma that separates them. A comma inside a comment does not
   * separate anything.
   */
  private static boolean holdsComma(String text) {
    return lastSeparator(text) >= 0;
  }

  /**
   * Whether a text written after a value ends with a comma, comments and whitespace aside, which is how a document that
   * writes a comma after its last element is recognized.
   */
  private static boolean endsWithComma(String text) {
    return lastSeparator(text) == ',';
  }

  /**
   * The last character of a text that is written outside a comment and is not whitespace, or {@code -1} if it has none.
   */
  private static int lastSeparator(String text) {
    int last = -1;
    boolean comment = false;
    for (int i = 0; i < text.length(); i++) {
      char character = text.charAt(i);
      if (comment) {
        comment = (character != '\n');
      } else if (character == '#') {
        comment = true;
      } else if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
        last = character;
      }
    }
    return last;
  }

  private static boolean holdsNewline(String text) {
    return text.indexOf('\n') >= 0;
  }

  /** The index after the last newline of a text, which is where its last line starts. */
  private static int lastLineStart(CharSequence text) {
    for (int i = text.length() - 1; i >= 0; i--) {
      if (text.charAt(i) == '\n') {
        return i + 1;
      }
    }
    return 0;
  }

  /** The spaces and tabs the last line of a text starts with, which is the indentation of the line being written. */
  private static String indentOfLastLine(CharSequence text) {
    int start = lastLineStart(text);
    int end = start;
    while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
      end++;
    }
    return text.subSequence(start, end).toString();
  }

  private static List<String> path(List<String> relative, String key) {
    List<String> keyPath = new ArrayList<>(relative.size() + 1);
    keyPath.addAll(relative);
    keyPath.add(key);
    return keyPath;
  }

  /**
   * One element of an array, one entry of an inline table, or one comment written between the brackets, together with
   * where it sorts among them.
   */
  private static final class Item {

    /** The element: an {@link Entry}, or a {@link TomlComment} attached to nothing. */
    private final TomlElement element;

    /** The key of an entry of an inline table, relative to that table; empty for anything else. */
    private final List<String> keyPath;

    /** Where the document wrote this item, or {@code null} if it holds no text that still describes it. */
    @Nullable
    private final SourceSpan span;

    /** The offset this item sorts at: where it was written, or where the item written before it ends. */
    private int anchor;

    /** The order this item was collected in, which orders the items anchored at the same offset. */
    private int order;

    Item(TomlElement element, List<String> keyPath, Source source) {
      this.element = element;
      this.keyPath = keyPath;
      this.span = writtenSpan(element, keyPath, source);
    }

    boolean isEntry() {
      return element instanceof Entry;
    }

    Entry entry() {
      return (Entry) element;
    }

    /**
     * The span an item is written from: the one the document recorded for it, where that span reads this container's
     * text and, for an entry, still names it from the container it is written in.
     */
    @Nullable
    @SuppressWarnings("ReferenceEquality") // the span reads this very text, not one equal to it
    private static SourceSpan writtenSpan(TomlElement element, List<String> keyPath, Source source) {
      if (element instanceof TomlComment) {
        SourceSpan span = ((TomlComment) element).span();
        return (span != null && span.kind == SourceSpan.Kind.COMMENT && span.source == source) ? span : null;
      }
      SourceSpan span = ((Entry) element).span;
      boolean written = span != null
          && span.kind == SourceSpan.Kind.ELEMENT
          && span.source == source
          && span.keyParts == keyPath.size();
      return written ? span : null;
    }
  }
}
