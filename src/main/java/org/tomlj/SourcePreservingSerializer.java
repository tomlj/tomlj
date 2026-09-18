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
 * Writes a parsed document from the text it was parsed from, so that everything the editing API has not touched is
 * written back as it was read.
 *
 * <p>
 * Every line, header and unattached comment the parser accepted recorded where it was written, along with the blank
 * lines and indentation above it and the comment after it ({@link SourceSpan}). Writing walks the model, collects a
 * chunk for each of those, and writes them in the order of the offsets they were read at, which restores the document's
 * own layout: its comments, the form each value was written in, and the order of its sections, including sections the
 * document interleaves ({@code [a]}, {@code [b]}, {@code [a.c]}). A line the parser rejected recorded nothing, so it is
 * not written; the comments above it were carried to the next accepted line as the document was read, so they survive.
 *
 * <p>
 * A span is used only where the parse itself put the element the walk finds. The walk stops reading spans as soon as it
 * passes through an entry the editing API added or replaced, since a table stored under a new key keeps the span of the
 * header that named its old path, and a table copied in from another document keeps spans that read that document's
 * text. Everything else is written in the default style ({@link TomlSerializer}) where it belongs: a new entry after
 * the nearest line of its table, indented like it and named by a key relative to the section that line is in; a new
 * unattached comment likewise, with a blank line above and below; a new table, or one of a copy, as a section after the
 * last line of its parent's subtree. A section written anew keeps the text of each line it copied, so a copied entry is
 * written with the literal it was read as.
 *
 * <p>
 * A line whose value or comments changed is written anew for now.
 */
// The paragraph above goes when a replaced value, an edited comment and an edited array or inline table are written
// into the line the parse read them on.
final class SourcePreservingSerializer {

  // Chunks are written in the order of the offsets they are anchored at, and these ranks order the ones anchored at
  // the same offset: content anchored before a line of the document, then that line, then content anchored after it,
  // then a new section, so that a new entry never lands under a header written at the same anchor.
  private static final int BEFORE_LINE = -1;
  private static final int SOURCE_LINE = 0;
  private static final int AFTER_LINE = 1;
  private static final int NEW_SECTION = 2;

  private final ParsedTomlTable root;
  private final Source source;
  private final Appendable out;

  // The options anything written anew is written with, which end a new line the way the document ends its own unless
  // the caller asked for a separator.
  private final TomlOptions options;
  private final String lineSeparator;

  private final List<Chunk> chunks = new ArrayList<>();
  private int sequence;

  // Whether anything has been written at all
  private boolean written;
  // Whether the output ends with a newline
  private boolean atLineStart = true;
  // Whether what has been written since the last newline is only whitespace
  private boolean currentLineBlank = true;
  // Whether the line before that one held only whitespace
  private boolean previousLineBlank;
  // A blank line owed to the output, written only once a line follows it, so that the document never ends with one
  private boolean blankLineOwed;
  // Whether the last thing written was an unattached comment of the document, which the line after it is separated
  // from by a blank line, since a run written directly above a line is the comment documenting it
  private boolean afterComment;

  static void toToml(ParsedTomlTable table, Appendable appendable, TomlOptions options) throws IOException {
    new SourcePreservingSerializer(table, appendable, options).write();
  }

  private SourcePreservingSerializer(ParsedTomlTable root, Appendable out, TomlOptions options) {
    Source source = root.source();
    assert source != null : "a document with no source is written in the default style";
    this.root = root;
    this.source = source;
    this.out = out;
    this.options = documentOptions(source, options);
    this.lineSeparator = this.options.lineSeparator();
  }

  /**
   * The options anything written anew is written with: unless the caller asked for a line separator, the lines added to
   * a document end the way the document ends its own.
   */
  private static TomlOptions documentOptions(Source source, TomlOptions options) {
    if (options.askedLineSeparator() != null) {
      return options;
    }
    String separator = source.lineSeparator();
    return (separator != null) ? options.withLineSeparator(separator) : options;
  }

  private void write() throws IOException {
    collectTable(root, Collections.emptyList(), Collections.emptyList(), new Group(null), -1, true);
    chunks
        .sort(
            Comparator
                .comparingInt((Chunk chunk) -> chunk.anchor())
                .thenComparingInt(chunk -> chunk.rank)
                .thenComparingInt(chunk -> chunk.order));
    for (Chunk chunk : chunks) {
      chunk.write();
    }
    if (root.trailerStart() >= 0) {
      startLine();
      // The blank lines the document ends with are its own, so a blank line owed to the output is not written as well
      blankLineOwed = false;
      append(source.text(root.trailerStart(), source.length() - 1));
    }
  }

  /**
   * Walk a table, collecting a chunk for each line, header and comment it contributes to the document.
   *
   * @param table The table to walk.
   * @param sectionPath The keys from the root to the table of the section the walk is in.
   * @param relative The keys from that section's table to this table, empty for the section's own table.
   * @param section The section, which the lines collected here belong to.
   * @param sectionAnchor Where an element of this table with no line of the document beside it goes: after the
   *        section's header, or {@code -1} for the root, which puts it before everything.
   * @param rootTable Whether this table is the document root.
   * @return The lines this table contributes to {@code section}, which are the lines of the table itself and of any
   *         table the document opened with a dotted key within it.
   */
  private Contribution collectTable(
      LinkedTomlTable table,
      List<String> sectionPath,
      List<String> relative,
      Group section,
      int sectionAnchor,
      boolean rootTable) {
    List<TomlElement> elements = table.elements();
    int count = elements.size();
    Contribution[] parts = new Contribution[count];
    boolean[] sections = new boolean[count];
    Group[] subtrees = new Group[count];
    List<Integer> pending = new ArrayList<>();
    Contribution contributed = new Contribution();

    for (int i = 0; i < count; i++) {
      TomlElement element = elements.get(i);
      Contribution part = new Contribution();
      parts[i] = part;

      if (element instanceof TomlComment) {
        SourceSpan span = ((TomlComment) element).span();
        if (span != null && usable(span, SourceSpan.Kind.COMMENT)) {
          addLine(span, section, part);
        } else {
          pending.add(i);
        }
        contributed.merge(part);
        continue;
      }

      Entry.KeyValue pair = (Entry.KeyValue) element;
      String key = pair.key();
      if (writtenOnALine(pair)) {
        SourceSpan span = pair.span;
        if (!pair.isModified()
            && span != null
            && usable(span, SourceSpan.Kind.LINE)
            && span.keyParts == (relative.size() + 1)) {
          addLine(span, section, part);
        } else {
          pending.add(i);
        }
        contributed.merge(part);
        continue;
      }

      sections[i] = true;
      if (pair.value instanceof LinkedTomlTable) {
        LinkedTomlTable subTable = (LinkedTomlTable) pair.value;
        SourceSpan header = headerOf(pair, subTable);
        if (header != null && usable(header, SourceSpan.Kind.HEADER)) {
          Group group = new Group(section);
          subtrees[i] = group;
          addHeader(header, group);
          collectTable(subTable, path(sectionPath, relative, key), Collections.emptyList(), group, header.stop, false);
        } else if (!pair.valueModified && !pair.commentsModified() && writtenAsDottedKeys(subTable)) {
          // A table the document opened with a dotted key, or on the way to a header of its own: its lines belong to
          // this section, written with the dotted keys the document wrote them with.
          sections[i] = false;
          part.merge(collectTable(subTable, sectionPath, path(relative, key), section, sectionAnchor, false));
        } else {
          // A table a dotted key can no longer name gets a header of its own
          addSection(pair, path(sectionPath, relative, key), section);
        }
      } else {
        ListTomlArray array = (ListTomlArray) pair.value;
        List<String> arrayPath = path(sectionPath, relative, key);
        Group arrayGroup = new Group(section);
        subtrees[i] = arrayGroup;
        List<Group> preceding = new ArrayList<>();
        for (TomlElement tableElement : array.elements()) {
          Entry.Indexed indexed = (Entry.Indexed) tableElement;
          SourceSpan header = null;
          if (!pair.valueModified && indexed.value instanceof LinkedTomlTable) {
            header = headerOf(indexed, (LinkedTomlTable) indexed.value);
          }
          if (header != null && usable(header, SourceSpan.Kind.HEADER)) {
            Group group = new Group(arrayGroup);
            addHeader(header, group);
            collectTable(
                (LinkedTomlTable) indexed.value,
                arrayPath,
                Collections.emptyList(),
                group,
                header.stop,
                false);
            preceding.add(group);
          } else {
            // A table the editing API added to the array follows the one before it, which the walk has already read
            chunks.add(new SectionChunk(indexed, arrayPath, true, section, stopOf(preceding)));
          }
        }
      }
      contributed.merge(part);
    }

    anchorNewElements(elements, parts, sections, subtrees, pending, sectionPath, relative, sectionAnchor, rootTable);
    return contributed;
  }

  /**
   * Collect a chunk for each element of a table that the document holds no text for: an entry or an unattached comment
   * the editing API added, or one whose text no longer describes it.
   *
   * <p>
   * Such an element is written beside its neighbours in the table's sequence: after the nearest element before it that
   * is written as a line of this section, or, where there is none, before the nearest one after it, or, where there is
   * neither, at the section's own anchor. It is written with the indentation of that neighbour, and with a key relative
   * to the section the neighbour's line is in, which is how a new entry of a table the document wrote as dotted keys
   * becomes another dotted key beside them.
   */
  private void anchorNewElements(
      List<TomlElement> elements,
      Contribution[] parts,
      boolean[] sections,
      Group[] subtrees,
      List<Integer> pending,
      List<String> sectionPath,
      List<String> relative,
      int sectionAnchor,
      boolean rootTable) {
    if (pending.isEmpty()) {
      return;
    }
    int lastLine = lastLineIndex(elements);
    // A new entry can only go where a re-parse has this very table open, which is before the first of its sections: a
    // line written after a header is read in the table that header opened. A comment is placed by the rules below.
    int firstSection = firstSectionIndex(sections);
    for (int index : pending) {
      TomlElement element = elements.get(index);
      boolean isComment = element instanceof TomlComment;
      int anchor = sectionAnchor;
      int rank = AFTER_LINE;
      String indent = null;
      // A run with no line of its table after it in the sequence is written directly under the line above it, so that
      // a re-parse reads it in this table rather than in whatever follows.
      boolean blankAbove = isComment && (index < lastLine);
      int limit = (isComment || firstSection < 0) ? parts.length : firstSection;

      boolean found = false;
      boolean afterSubtree = false;
      for (int j = Math.min(index, limit) - 1; j >= 0 && !found; j--) {
        Contribution part = parts[j];
        if (part.hasLines()) {
          anchor = part.lastStop;
          indent = part.lastIndent;
          found = true;
        } else if (isComment && rootTable && subtrees[j] != null && subtrees[j].subtreeStop >= 0) {
          // The root's own comments are written after the last section, since a run written under a header belongs to
          // the table that header opened.
          anchor = subtrees[j].subtreeStop;
          blankAbove = true;
          found = true;
          afterSubtree = true;
        }
      }
      if (!found || afterSubtree) {
        for (int j = index + 1; j < limit; j++) {
          if (sections[j]) {
            // A line written under a header belongs to the table that header opened, so an element written before a
            // section of this table cannot be written beside anything past that section: it goes at the section's
            // anchor
            break;
          }
          if (parts[j].hasLines()) {
            // The line of this table nearest after the element. A comment written after a section goes before that
            // line where the section's text runs past it, which is where the root holds a run between two headers.
            if (!found || parts[j].firstStart <= anchor) {
              anchor = parts[j].firstStart;
              indent = parts[j].firstIndent;
              rank = BEFORE_LINE;
            }
            break;
          }
        }
      }

      String lineIndent = (indent != null) ? indent : spaces(sectionPath.size() * options.indent());
      if (isComment) {
        chunks.add(new CommentChunk((TomlComment) element, lineIndent, anchor, rank, blankAbove));
      } else {
        Entry.KeyValue pair = (Entry.KeyValue) element;
        chunks.add(new EntryChunk(pair, path(relative, pair.key()), lineIndent, anchor, rank, blankAbove));
      }
    }
  }

  private void addLine(SourceSpan span, Group section, Contribution part) {
    chunks.add(new LineChunk(span));
    section.addLine(span.stop);
    part.add(span.start, span.stop, indentOf(span));
  }

  private void addHeader(SourceSpan header, Group group) {
    chunks.add(new LineChunk(header));
    group.addLine(header.stop);
  }

  private void addSection(TomlEntry entry, List<String> path, Group section) {
    chunks.add(new SectionChunk(entry, path, false, section, -1));
  }

  /**
   * Whether a table with no header of its own can be written as the dotted keys of the section around it.
   *
   * <p>
   * A dotted key writes one entry, so a table with none has nothing to be written as, and a comment written among those
   * keys would be read in the section rather than in this table, which only a header of its own can hold.
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
   * The header line a table was opened by, where that line still describes it.
   *
   * @param entry The entry holding the table, whose comments are written on the header.
   * @param table The table.
   * @return The header's span, or {@code null} if the table was stored through the editing API, or the comments on its
   *         header were, since the header line is written as it was read, comments included.
   */
  @Nullable
  private static SourceSpan headerOf(Entry entry, LinkedTomlTable table) {
    return (entry.valueModified || entry.commentsModified()) ? null : table.headerSpan;
  }

  /**
   * Whether a span reads this document and covers what the walk expects it to. A span copied from another document
   * describes a line of that one, which says nothing about where the entry holding it belongs here.
   */
  @SuppressWarnings("ReferenceEquality") // the span reads this very text, not one equal to it
  private boolean usable(SourceSpan span, SourceSpan.Kind kind) {
    return span.kind == kind && span.source == source;
  }

  /**
   * Whether an entry is written as a {@code key = value} line, rather than under a header of its own.
   *
   * <p>
   * Unlike every other table, an inline table is written on the line of the entry holding it, and an array written as a
   * literal stays a literal however it is filled, unlike the array of the tables of {@code [[x]]} headers.
   */
  private static boolean writtenOnALine(TomlEntry entry) {
    Object value = entry.value().get();
    if (value instanceof LinkedTomlTable) {
      return ((LinkedTomlTable) value).isInline();
    }
    if (value instanceof ListTomlArray && ((ListTomlArray) value).isTableArray()) {
      return TomlSerializer.isLine(entry);
    }
    return true;
  }

  private static int firstSectionIndex(boolean[] sections) {
    for (int i = 0; i < sections.length; i++) {
      if (sections[i]) {
        return i;
      }
    }
    return -1;
  }

  private static int lastLineIndex(List<TomlElement> elements) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      TomlElement element = elements.get(i);
      if (element instanceof TomlEntry && writtenOnALine((TomlEntry) element)) {
        return i;
      }
    }
    return -1;
  }

  private static int stopOf(List<Group> groups) {
    int stop = -1;
    for (Group group : groups) {
      if (group.subtreeStop > stop) {
        stop = group.subtreeStop;
      }
    }
    return stop;
  }

  /**
   * The indentation of the line a span was read from: the whitespace between the start of that line and the first
   * character of the key, the header or the comment.
   */
  private String indentOf(SourceSpan span) {
    boolean run = (span.kind != SourceSpan.Kind.COMMENT) && (span.aboveStop >= 0);
    int from = run ? (span.aboveStop + 1) : span.start;
    int token = (span.kind == SourceSpan.Kind.COMMENT) ? span.aboveStart : span.keyStart;
    return lastLineOf(source.text(from, token - 1));
  }

  /** The first offset of a span's own text, after the blank lines above it and the indentation of its first line. */
  private static int firstToken(SourceSpan span) {
    return (span.aboveStart >= 0) ? span.aboveStart : span.keyStart;
  }

  /** The text after the last newline, which is the last line of a run of whitespace. */
  private static String lastLineOf(String text) {
    return text.substring(text.lastIndexOf('\n') + 1);
  }

  private static String spaces(int width) {
    StringBuilder text = new StringBuilder(width);
    for (int i = 0; i < width; i++) {
      text.append(' ');
    }
    return text.toString();
  }

  private static List<String> path(List<String> sectionPath, List<String> relative, String key) {
    List<String> path = new ArrayList<>(sectionPath.size() + relative.size() + 1);
    path.addAll(sectionPath);
    path.addAll(relative);
    path.add(key);
    return path;
  }

  private static List<String> path(List<String> relative, String key) {
    List<String> path = new ArrayList<>(relative.size() + 1);
    path.addAll(relative);
    path.add(key);
    return path;
  }

  /**
   * Write text, noting where the output now stands: whether it is at the start of a line, and whether the line before
   * that one was blank.
   */
  private void append(CharSequence text) throws IOException {
    if (text.length() == 0) {
      return;
    }
    out.append(text);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        previousLineBlank = currentLineBlank;
        currentLineBlank = true;
        atLineStart = true;
      } else {
        if (c != ' ' && c != '\t' && c != '\r') {
          currentLineBlank = false;
        }
        atLineStart = false;
      }
    }
    written = true;
  }

  /**
   * End the line the output is on, if it is not already at the start of one. Only the last line of a document can lack
   * its own newline, so this writes one only where something follows such a line.
   */
  private void startLine() throws IOException {
    if (written && !atLineStart) {
      append(lineSeparator);
    }
  }

  /**
   * Owe the output a blank line, unless it is empty or already ends with one, so that blank lines neither open the
   * document nor double up.
   */
  private void requestBlankLine() {
    if (written && !(atLineStart && previousLineBlank)) {
      blankLineOwed = true;
    }
  }

  /**
   * Write the blank line owed to the output, if there is one.
   *
   * @return {@code true} if a blank line was written, in which case the blank lines a chunk of the document opens with
   *         are not written as well.
   */
  private boolean flushBlankLine() throws IOException {
    if (!blankLineOwed) {
      return false;
    }
    blankLineOwed = false;
    append(lineSeparator);
    return true;
  }

  /**
   * The lines this table wrote into the section around it, which anchor whatever the editing API added beside them.
   */
  private static final class Contribution {

    /** Where the first of those lines starts, or {@code -1} if there are none. */
    private int firstStart = -1;

    /** Where the last of them ends, or {@code -1} if there are none. */
    private int lastStop = -1;

    private String firstIndent = "";
    private String lastIndent = "";

    boolean hasLines() {
      return firstStart >= 0;
    }

    void add(int start, int stop, String indent) {
      if (firstStart < 0 || start < firstStart) {
        firstStart = start;
        firstIndent = indent;
      }
      if (stop > lastStop) {
        lastStop = stop;
        lastIndent = indent;
      }
    }

    void merge(Contribution other) {
      if (other.firstStart >= 0 && (firstStart < 0 || other.firstStart < firstStart)) {
        firstStart = other.firstStart;
        firstIndent = other.firstIndent;
      }
      if (other.lastStop > lastStop) {
        lastStop = other.lastStop;
        lastIndent = other.lastIndent;
      }
    }
  }

  /**
   * A section - a table written under a header, or the root - or an array of tables, which anchors the sections written
   * into it that the document has no place for.
   */
  private static final class Group {

    @Nullable
    private final Group parent;

    /** The end of the last line anywhere in this group, the groups nested in it included, or {@code -1}. */
    private int subtreeStop = -1;

    Group(@Nullable Group parent) {
      this.parent = parent;
    }

    void addLine(int stop) {
      for (Group group = this; group != null; group = group.parent) {
        if (stop > group.subtreeStop) {
          group.subtreeStop = stop;
        }
      }
    }

    /**
     * Where a new section of this group goes: after everything in it, or after everything in the nearest enclosing
     * group that holds anything.
     */
    int sectionAnchor() {
      for (Group group = this; group != null; group = group.parent) {
        if (group.subtreeStop >= 0) {
          return group.subtreeStop;
        }
      }
      return -1;
    }
  }

  /**
   * One line, or one run of lines, of the output, together with where it sorts in the document.
   */
  private abstract class Chunk {

    final int rank;

    /**
     * The order this chunk was collected in, which orders the chunks anchored at the same offset with the same rank.
     */
    final int order;

    Chunk(int rank) {
      this.rank = rank;
      this.order = sequence++;
    }

    /** The offset this chunk sorts at: where it was read, or where the line it is anchored to ends. */
    abstract int anchor();

    abstract void write() throws IOException;
  }

  /**
   * A key/value line, a table header or an unattached comment, written from the text it was read from.
   */
  private final class LineChunk extends Chunk {

    private final SourceSpan span;

    LineChunk(SourceSpan span) {
      super(SOURCE_LINE);
      this.span = span;
    }

    @Override
    int anchor() {
      return span.start;
    }

    @Override
    void write() throws IOException {
      startLine();
      String leading = source.text(span.start, firstToken(span) - 1);
      if (afterComment && leading.indexOf('\n') < 0) {
        // The blank line the document had under that comment is gone with whatever was written between them
        requestBlankLine();
      }
      boolean blankWritten = flushBlankLine();
      // A blank line written above this one already separates it from what came before
      append(blankWritten ? lastLineOf(leading) : leading);
      if (span.kind == SourceSpan.Kind.HEADER) {
        // Input the parser skipped between the header and its newline lies between the two texts, and is not written
        append(source.text(firstToken(span), span.keyStop));
        append(source.text(span.tailStart, span.stop));
      } else {
        append(source.text(firstToken(span), span.stop));
      }
      afterComment = (span.kind == SourceSpan.Kind.COMMENT);
    }
  }

  /**
   * A {@code key = value} line the document holds no text for, written in the default style beside the lines around it.
   */
  private final class EntryChunk extends Chunk {

    private final TomlEntry entry;
    private final List<String> keyPath;
    private final String lineIndent;
    private final int anchor;
    private final boolean blankAbove;

    EntryChunk(TomlEntry entry, List<String> keyPath, String lineIndent, int anchor, int rank, boolean blankAbove) {
      super(rank);
      this.entry = entry;
      this.keyPath = keyPath;
      this.lineIndent = lineIndent;
      this.anchor = anchor;
      this.blankAbove = blankAbove;
    }

    @Override
    int anchor() {
      return anchor;
    }

    @Override
    void write() throws IOException {
      startLine();
      if (blankAbove || afterComment) {
        requestBlankLine();
      }
      flushBlankLine();
      StringBuilder text = new StringBuilder();
      TomlSerializer.defaultStyle(text, options).writeKeyValue(lineIndent, keyPath, entry);
      append(text);
      afterComment = false;
    }
  }

  /**
   * An unattached comment the document holds no text for, written with a blank line above it and one below, so that a
   * re-parse reads it in the table it was added to and as the comment above nothing.
   */
  private final class CommentChunk extends Chunk {

    private final TomlComment comment;
    private final String lineIndent;
    private final int anchor;
    private final boolean blankAbove;

    CommentChunk(TomlComment comment, String lineIndent, int anchor, int rank, boolean blankAbove) {
      super(rank);
      this.comment = comment;
      this.lineIndent = lineIndent;
      this.anchor = anchor;
      this.blankAbove = blankAbove;
    }

    @Override
    int anchor() {
      return anchor;
    }

    @Override
    void write() throws IOException {
      startLine();
      if (blankAbove || afterComment) {
        requestBlankLine();
      }
      flushBlankLine();
      StringBuilder text = new StringBuilder();
      TomlSerializer.defaultStyle(text, options).writeCommentLines(comment, lineIndent);
      append(text);
      requestBlankLine();
      afterComment = false;
    }
  }

  /**
   * A table, or one table of an array of tables, that the document has no header for, written in the default style as a
   * section of its own after the last line of the subtree it belongs to.
   */
  private final class SectionChunk extends Chunk {

    private final TomlEntry entry;
    private final List<String> path;
    private final boolean arrayTable;
    private final Group group;

    /** Where this section goes, or {@code -1} to take the group's own anchor once the whole document is walked. */
    private final int after;

    SectionChunk(TomlEntry entry, List<String> path, boolean arrayTable, Group group, int after) {
      super(NEW_SECTION);
      this.entry = entry;
      this.path = path;
      this.arrayTable = arrayTable;
      this.group = group;
      this.after = after;
    }

    @Override
    int anchor() {
      return (after >= 0) ? after : group.sectionAnchor();
    }

    @Override
    void write() throws IOException {
      startLine();
      requestBlankLine();
      flushBlankLine();
      StringBuilder text = new StringBuilder();
      TomlSerializer block = TomlSerializer.blockStyle(text, options);
      if (arrayTable) {
        block.writeArrayTableSection(entry, new ArrayList<>(path));
      } else {
        block.writeSections(entry, new ArrayList<>(path));
      }
      append(text);
      afterComment = false;
    }
  }
}
