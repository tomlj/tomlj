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
 * A line whose value was replaced, or whose comments were set or removed, keeps the place the document gave it: the
 * blank lines above it, its key and the spacing around the {@code =} are written as they were read, and only what
 * changed is written anew. A value the default style writes under a header of its own, a table or an array of tables,
 * cannot stay on a line, so the line goes and the value is written as a section. An array or inline table edited in
 * place keeps its line and its brackets, and is written from the parts of it the document still holds the text of
 * ({@link EditedContainerSerializer}).
 *
 * <p>
 * A normalized layout ({@link TomlOptions.Style#PRETTIFY}) walks the document the same way and writes its lines,
 * headers and comments in the same order, but writes each of them from its parts rather than from the text around them:
 * the indentation of its section, the comments of the model, the key and the literal each value was written with, and
 * an array or inline table laid out anew. A line keeps one blank line above it where the document wrote any, a header
 * always gets one, and the blank lines a document ends with are dropped.
 */
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

  // Whether every line is written from its parts in the layout the options ask for, rather than from the text around it
  private final boolean prettify;

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
    this.prettify = (options.style() == TomlOptions.Style.PRETTIFY);
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
    // A normalized layout ends the document with the last line it writes, so the blank lines below it are dropped
    if (!prettify && root.trailerStart() >= 0) {
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
    // The last line this table writes, which an unattached run after it is written directly under
    int lastLine = prettify ? lastLineIndex(elements) : -1;
    // A normalized layout writes the lines of this table at the indentation the options give the section around it,
    // and the header of a table written in it at the indentation of the lines it is written among
    String lineIndent = prettify ? spaces(sectionPath.size() * options.indent()) : "";
    String headerIndent = prettify ? spaces((sectionPath.size() + relative.size()) * options.indent()) : "";
    boolean sawSection = false;

    for (int i = 0; i < count; i++) {
      TomlElement element = elements.get(i);
      Contribution part = new Contribution();
      parts[i] = part;

      if (element instanceof TomlComment) {
        TomlComment comment = (TomlComment) element;
        SourceSpan span = comment.span();
        if (span != null && usable(span, SourceSpan.Kind.COMMENT)) {
          // A run written after a section of the root belongs to the root only if a blank line separates it from it
          addComment(span, comment, section, part, lineIndent, (i < lastLine) || (rootTable && sawSection));
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
        if (span != null && usable(span, SourceSpan.Kind.LINE) && span.keyParts == (relative.size() + 1)) {
          addLine(span, pair, section, part, path(relative, key), lineIndent);
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
          addHeader(header, pair, group, headerIndent);
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
            addHeader(header, indexed, group, headerIndent);
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
      sawSection = sawSection || sections[i];
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

      // A normalized layout indents every line of a section alike, whatever the line beside this one was indented by
      String lineIndent = (indent != null && !prettify) ? indent : spaces(sectionPath.size() * options.indent());
      if (isComment) {
        chunks.add(new CommentChunk((TomlComment) element, lineIndent, anchor, rank, blankAbove));
      } else {
        Entry.KeyValue pair = (Entry.KeyValue) element;
        chunks.add(new EntryChunk(pair, path(relative, pair.key()), lineIndent, anchor, rank, blankAbove));
      }
    }
  }

  private void addLine(
      SourceSpan span,
      Entry entry,
      Group section,
      Contribution part,
      List<String> keyPath,
      String lineIndent) {
    chunks.add(new LineChunk(span, entry, keyPath, lineIndent));
    section.addLine(span.stop);
    part.add(span.start, span.stop, indentOf(span));
  }

  /**
   * Collect an unattached comment the document wrote. A normalized layout writes it from the model, as it writes one
   * the editing API added, since where the blank lines around it go is part of the layout.
   */
  private void addComment(
      SourceSpan span,
      TomlComment comment,
      Group section,
      Contribution part,
      String lineIndent,
      boolean blankAbove) {
    chunks
        .add(
            prettify ? new CommentChunk(comment, lineIndent, span.start, SOURCE_LINE, blankAbove)
                : new LineChunk(span, null, Collections.<String>emptyList(), lineIndent));
    section.addLine(span.stop);
    part.add(span.start, span.stop, indentOf(span));
  }

  private void addHeader(SourceSpan header, Entry entry, Group group, String headerIndent) {
    chunks.add(new LineChunk(header, entry, Collections.emptyList(), headerIndent));
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
   * @return The header's span, or {@code null} if the table was stored through the editing API, since the header then
   *         names the path the table was read at rather than the one it is written at.
   */
  @Nullable
  private static SourceSpan headerOf(Entry entry, LinkedTomlTable table) {
    return entry.valueModified ? null : table.headerSpan;
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
   * Unlike every other table, an inline table is written on the line of the entry holding it. An array the document
   * wrote as a literal stays a literal however it is filled, unlike the array of the tables of {@code [[x]]} headers;
   * an array stored through the editing API was never written at all, so the default style decides how it is written,
   * and the entry holding it keeps its line only if that style writes the array on one.
   */
  private static boolean writtenOnALine(Entry entry) {
    Object value = entry.value.get();
    if (value instanceof LinkedTomlTable) {
      return ((LinkedTomlTable) value).isInline();
    }
    if (value instanceof ListTomlArray && (entry.valueModified || ((ListTomlArray) value).isTableArray())) {
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
      if (element instanceof Entry && writtenOnALine((Entry) element)) {
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

  /**
   * The blank lines of a span's leading whitespace: everything before the indentation of its first line, which the
   * lines written in place of that first line carry instead.
   */
  private static String blankLinesOf(String leading) {
    return leading.substring(0, leading.lastIndexOf('\n') + 1);
  }

  /** The comment attached to an entry at a placement, or {@code null} if it has none there. */
  @Nullable
  private static TomlComment attachedComment(Entry entry, TomlComment.Placement placement) {
    for (TomlComment comment : entry.comments()) {
      if (comment.placement() == placement) {
        return comment;
      }
    }
    return null;
  }

  /** The width of a text with no newline in it, in code points, every character of which is one column wide. */
  private static int width(String text) {
    return text.codePointCount(0, text.length());
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
   * A key/value line, a table header or an unattached comment, written from the text it was read from, with the value
   * and the comments of a line the editing API touched written from the model instead.
   */
  private final class LineChunk extends Chunk {

    private final SourceSpan span;

    /** The entry the line or header holds, or {@code null} for an unattached comment. */
    @Nullable
    private final Entry entry;

    /** The key the line is written with, relative to the section it is written in. Empty for a header. */
    private final List<String> keyPath;

    /** The indentation a normalized layout writes this line at. */
    private final String lineIndent;

    LineChunk(SourceSpan span, @Nullable Entry entry, List<String> keyPath, String lineIndent) {
      super(SOURCE_LINE);
      this.span = span;
      this.entry = entry;
      this.keyPath = keyPath;
      this.lineIndent = lineIndent;
    }

    @Override
    int anchor() {
      return span.start;
    }

    @Override
    void write() throws IOException {
      if (prettify) {
        writeNormalized();
        return;
      }
      startLine();
      String leading = source.text(span.start, firstToken(span) - 1);
      if (afterComment && leading.indexOf('\n') < 0) {
        // The blank line the document had under that comment is gone with whatever was written between them
        requestBlankLine();
      }
      boolean blankWritten = flushBlankLine();
      Entry lineEntry = entry;
      if (lineEntry == null) {
        // A blank line written above this one already separates it from what came before
        append(blankWritten ? lastLineOf(leading) : leading);
        append(source.text(firstToken(span), span.stop));
        afterComment = true;
        return;
      }
      boolean modelComments = lineEntry.commentsModified();
      String indent = indentOf(span);
      // The line is assembled whole, so that a value written anew into it is laid out at the column it reaches
      StringBuilder line = new StringBuilder();
      if (modelComments) {
        appendCommentAbove(line, lineEntry, blankWritten ? "" : blankLinesOf(leading), indent);
      } else {
        line.append(blankWritten ? lastLineOf(leading) : leading);
        // The comment run above the line and the indentation of the line itself, as they were written
        line.append(source.text(firstToken(span), span.keyStart - 1));
      }
      if (span.kind == SourceSpan.Kind.HEADER) {
        line.append(source.text(span.keyStart, span.keyStop));
      } else {
        line.append(source.text(span.keyStart, span.valueStart - 1));
        appendValue(line, lineEntry, indent);
      }
      // Input the parser skipped between the value or header and the newline lies before the tail, so it is not written
      line.append(modelComments ? tailWithCommentAfter(lineEntry) : source.text(span.tailStart, span.stop));
      append(line);
      afterComment = false;
    }

    /**
     * Write the line from its parts, in the layout the options ask for: the indentation of its section, the comments
     * the model holds, the key and, for a header, the text the document wrote them as, and the value laid out anew
     * around the literal each scalar was written with. A line keeps one blank line above it where the document wrote
     * any, and a header is always separated from what precedes it.
     */
    private void writeNormalized() throws IOException {
      Entry lineEntry = entry;
      assert lineEntry != null : "an unattached comment is written from the model";
      boolean header = (span.kind == SourceSpan.Kind.HEADER);
      if (header || source.text(span.start, firstToken(span) - 1).indexOf('\n') >= 0) {
        requestBlankLine();
      }
      startLine();
      flushBlankLine();
      StringBuilder line = new StringBuilder();
      TomlSerializer writer = TomlSerializer.defaultStyle(line, options);
      if (header) {
        writer.writeHeaderText(lineIndent, source.text(span.keyStart, span.keyStop), lineEntry.comments());
      } else {
        writer.writeKeyValue(lineIndent, keyPath, lineEntry);
      }
      append(line);
      afterComment = false;
    }

    /** Append the blank lines above the line, then the run of comment lines the model holds, indented like the line. */
    private void appendCommentAbove(StringBuilder line, Entry lineEntry, String blankLines, String indent)
        throws IOException {
      line.append(blankLines);
      TomlComment above = attachedComment(lineEntry, TomlComment.Placement.ABOVE);
      if (above != null) {
        TomlSerializer.defaultStyle(line, options).writeCommentLines(above, indent);
      }
      line.append(indent);
    }

    /**
     * Append the value of the line: the text it was read as; an array or inline table edited in place, written within
     * the brackets it was read in; or, where the document holds no text that still describes the value, the value in
     * the default style.
     *
     * @param line The line so far, which the value is written at the end of.
     * @param lineEntry The entry the line holds.
     * @param indent The indentation of the line, which the elements of an array written over lines are indented from.
     */
    private void appendValue(StringBuilder line, Entry lineEntry, String indent) throws IOException {
      ValueSpan value = span.writtenValue(lineEntry.value);
      if (value != null) {
        line.append(source.text(value.start, value.stop));
        return;
      }
      ValueSpan brackets = span.writtenBrackets(lineEntry.value);
      if (brackets != null) {
        EditedContainerSerializer
            .append(
                line,
                (ElementContainer<?>) lineEntry.value,
                brackets,
                EditedContainerSerializer.Context.LINE,
                options);
        return;
      }
      int column = width(indent) + width(source.text(span.keyStart, span.valueStart - 1));
      TomlSerializer.defaultStyle(line, options).writeLineValue(lineEntry.value.get(), indent, column);
    }

    /**
     * What follows the value, or the header, on a line whose comments the model holds: the comment after it, written
     * where the document wrote its own, then the newline the line ended with.
     *
     * @param lineEntry The entry the line holds.
     * @return The text.
     */
    private String tailWithCommentAfter(Entry lineEntry) {
      String newline = source.text(span.newlineStart, span.stop);
      TomlComment after = attachedComment(lineEntry, TomlComment.Placement.AFTER);
      if (after == null) {
        return newline;
      }
      // The spacing the document wrote before its own comment, or the two spaces of the default style
      String spacing = (span.afterStart >= 0) ? source.text(span.tailStart, span.afterStart - 1) : "  ";
      return spacing + '#' + after.rawLines().get(0) + newline;
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
