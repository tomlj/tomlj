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
 * Writes a parsed document keeping its layout ({@link TomlWriteOptions.Keep#LAYOUT}), so that everything the editing
 * API has not touched is written back as it was read, or keeping its notation ({@link TomlWriteOptions.Keep#NOTATION}).
 *
 * <p>
 * Every line, header and unattached comment the parser accepted recorded where it was written, along with the blank
 * lines and indentation above it and the comment after it ({@link SourceSpan}). Writing walks the model, collects a
 * chunk for each of those, and writes them in the order of the offsets they were read at, which keeps the document's
 * layout: its comments, the form each value was written in, and the order of its sections, including sections the
 * document interleaves ({@code [a]}, {@code [b]}, {@code [a.c]}). A line the parser rejected recorded nothing, so it is
 * not written, and neither is the comment run above it, which is not in the model.
 *
 * <p>
 * A span is used only for an element that is still in the container and at the key path it was parsed at. The walk
 * stops using spans as soon as it passes through an entry the editing API added or replaced, since a table stored under
 * a new key keeps the span of the header that named its old path, and a table copied in from another document keeps
 * spans whose source is that document. Everything else is written as {@link TomlWriteOptions.Keep#NOTATION} writes it
 * ({@link Serializer}), where it belongs: a new entry after the nearest line of its table, indented like it and named
 * by a key relative to the section that line is in; a new unattached comment likewise, with a blank line above and
 * below, except that a run after the last line of a table is written directly under the line above it, and joined to a
 * run already there by an empty comment line, since a re-parse reads a run separated from that line by a blank line as
 * an unattached comment of the root; a new table, or one of a copy, as a section after the last line of its parent's
 * subtree. A section written anew copies each of its lines that has a span, so a copied entry is written with the
 * literal it was read as.
 *
 * <p>
 * A line whose value was replaced, or whose comments were set or removed, is written at the offset it was read from:
 * the blank lines above it, its key and the spacing around the {@code =} are written as they were read, and only what
 * changed is written anew. A value the default style writes under a header of its own, a table or an array of tables,
 * cannot stay on a line, so the line is not written and the value is written as a section. An array or inline table
 * edited in place keeps its line and its brackets, and is written from the spans of its elements that were not edited
 * ({@link EditedContainerSerializer}).
 *
 * <p>
 * Keeping the notation ({@link TomlWriteOptions.Keep#NOTATION}) walks the document the same way and writes its lines,
 * headers and comments in the same order, but writes each of them from its parts rather than from the text around them:
 * the indentation of its section, the comments of the model, the key and the literal each value was written with, and
 * an array or inline table laid out anew. A line keeps one blank line above it where the document wrote any, a header
 * gets one unless the options leave it out between nested headers, and the blank lines a document ends with are
 * dropped.
 *
 * <p>
 * A table or array reformatted by {@link MutableTomlTable#reformat(TomlWriteOptions.Keep)} is written at its position
 * in the document, keeping what reformat() asked for: keeping the notation writes its lines from their parts, in place,
 * and keeping nothing writes the whole of it from the model as one block, in the place its header had - or, for a table
 * the document wrote as the dotted keys of the section around it, and for one whose header names a different path,
 * after the last line of that section. No span is used for anything in such a table.
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

  // The options anything written anew is written with, which use the document's line separator unless the caller set
  // one.
  private final TomlWriteOptions options;
  private final String lineSeparator;

  // The options the root table is written with: these options, or the amount to keep that reformat() set on the root
  // if that is less
  private final TomlWriteOptions rootOptions;

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
  // A pending blank line, written only once a line follows it, so that the document never ends with one
  private boolean blankLineOwed;
  // Whether the last thing written was an unattached comment of the document, which the line after it is separated from
  // by a blank line, since a run written directly above a line is attached ABOVE it
  private boolean afterComment;
  // The path of the header the chunk written last ends with, or null if it does not end with a header
  @Nullable
  private List<String> lastHeader;

  static void toToml(ParsedTomlTable table, Appendable appendable, TomlWriteOptions options) throws IOException {
    new SourcePreservingSerializer(table, appendable, options).write();
  }

  private SourcePreservingSerializer(ParsedTomlTable root, Appendable out, TomlWriteOptions options) {
    Source source = root.source();
    assert source != null : "a document with no source is written in the default style";
    this.root = root;
    this.source = source;
    this.out = out;
    this.options = documentOptions(source, options);
    this.lineSeparator = this.options.lineSeparator();
    this.rootOptions = ElementContainer.optionsWithin(root, this.options);
  }

  /**
   * A range of the document's text, for writing.
   *
   * @param start The first offset, inclusive.
   * @param stop The last offset, inclusive.
   * @return The text.
   * @throws IllegalArgumentException If the range holds a construct the version written cannot write.
   */
  private String text(int start, int stop) {
    return source.text(start, stop, options.version());
  }

  /**
   * The options anything written anew is written with: unless the caller set a line separator, the lines added to a
   * document use the document's line separator.
   */
  private static TomlWriteOptions documentOptions(Source source, TomlWriteOptions options) {
    if (options.askedLineSeparator() != null) {
      return options;
    }
    String separator = source.lineSeparator();
    return (separator != null) ? options.withLineSeparator(separator) : options;
  }

  private void write() throws IOException {
    collectTable(root, Collections.emptyList(), Collections.emptyList(), new Group(null), -1, true, rootOptions);
    chunks
        .sort(
            Comparator
                .comparingInt((Chunk chunk) -> chunk.anchor())
                .thenComparingInt(chunk -> chunk.rank)
                .thenComparingInt(chunk -> chunk.order));
    for (Chunk chunk : chunks) {
      chunk.write();
      lastHeader = chunk.headerPath();
    }
    // Keeping only the notation ends the document with the last line written, so the blank lines below it are dropped
    if (rootOptions.keep() != TomlWriteOptions.Keep.NOTATION && root.trailerStart() >= 0) {
      startLine();
      // The document's trailing blank lines are copied, so the pending blank line is not written
      blankLineOwed = false;
      append(text(root.trailerStart(), source.length() - 1));
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
   * @param tableOptions The options this table is written with, which say how much of it is kept.
   * @return The lines this table contributes to {@code section}, which are the lines of the table itself and of any
   *         table the document opened with a dotted key within it.
   */
  private Contribution collectTable(
      LinkedTomlTable table,
      List<String> sectionPath,
      List<String> relative,
      Group section,
      int sectionAnchor,
      boolean rootTable,
      TomlWriteOptions tableOptions) {
    List<TomlElement> elements = table.elements();
    int count = elements.size();
    Contribution[] parts = new Contribution[count];
    boolean[] sections = new boolean[count];
    Group[] subtrees = new Group[count];
    List<Integer> pending = new ArrayList<>();
    Contribution contributed = new Contribution();
    boolean notationOnly = (tableOptions.keep() == TomlWriteOptions.Keep.NOTATION);
    // The spans of the unattached comments the document wrote, whose chunks are collected once the table's last line
    // is known, since a run after that line is written differently
    SourceSpan[] commentSpans = new SourceSpan[count];
    // When only the notation is kept, the lines of this table are written at the indentation the options give the
    // section around it, and the header of a table written in it at the indentation of the lines it is written among
    String lineIndent = notationOnly ? spaces(options.entryIndent(sectionPath.size())) : "";
    String headerIndent = spaces((sectionPath.size() + relative.size()) * options.indent());

    for (int i = 0; i < count; i++) {
      TomlElement element = elements.get(i);
      Contribution part = new Contribution();
      parts[i] = part;

      if (element instanceof TomlComment) {
        TomlComment comment = (TomlComment) element;
        SourceSpan span = comment.span();
        if (span != null && usable(span, SourceSpan.Kind.COMMENT)) {
          commentSpans[i] = span;
          section.addLine(span.stop);
          part.add(span.start, span.stop, indentOf(span));
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
          addLine(span, pair, section, part, path(relative, key), lineIndent, tableOptions);
        } else {
          pending.add(i);
        }
        contributed.merge(part);
        continue;
      }

      sections[i] = true;
      if (pair.value instanceof LinkedTomlTable) {
        LinkedTomlTable subTable = (LinkedTomlTable) pair.value;
        TomlWriteOptions subOptions = ElementContainer.optionsWithin(subTable, tableOptions);
        SourceSpan header = headerOf(pair, subTable);
        boolean headerWritten = (header != null) && usable(header, SourceSpan.Kind.HEADER);
        if (subOptions.keep() == TomlWriteOptions.Keep.NOTHING) {
          // The table is written whole, in the place its header had, so nothing of the subtree is read from the
          // document; only where its text ended is, which is where a section written into the table around it goes.
          subtrees[i] = closedGroup(section, subtreeStop(subTable, headerWritten ? header.stop : -1));
          chunks
              .add(
                  new SectionChunk(
                      pair,
                      path(sectionPath, relative, key),
                      false,
                      section,
                      headerWritten ? header.start : -1,
                      headerWritten ? SOURCE_LINE : NEW_SECTION,
                      subOptions));
        } else if (headerWritten) {
          Group group = new Group(section);
          subtrees[i] = group;
          addHeader(header, pair, path(sectionPath, relative, key), group, headerIndent, subOptions);
          collectTable(
              subTable,
              path(sectionPath, relative, key),
              Collections.emptyList(),
              group,
              header.stop,
              false,
              subOptions);
        } else if (!pair.valueModified && !pair.commentsModified() && writtenAsDottedKeys(subTable)) {
          // A table the document opened with a dotted key, or implicitly as a parent of a header: its lines belong to
          // this section, written with the dotted keys the document wrote them with.
          sections[i] = false;
          part
              .merge(
                  collectTable(subTable, sectionPath, path(relative, key), section, sectionAnchor, false, subOptions));
        } else {
          // A table a dotted key can no longer name gets a header of its own
          addSection(pair, path(sectionPath, relative, key), section, subOptions);
        }
      } else {
        ListTomlArray array = (ListTomlArray) pair.value;
        TomlWriteOptions arrayOptions = ElementContainer.optionsWithin(array, tableOptions);
        List<String> arrayPath = path(sectionPath, relative, key);
        if (arrayOptions.keep() == TomlWriteOptions.Keep.NOTHING) {
          collectTableArrayKeepingNothing(pair, array, arrayPath, section, subtrees, i, arrayOptions);
        } else {
          Group arrayGroup = new Group(section);
          subtrees[i] = arrayGroup;
          List<Group> preceding = new ArrayList<>();
          // Tables the editing API added before the first table the document wrote a header for
          List<Entry.Indexed> leading = new ArrayList<>();
          for (TomlElement tableElement : array.elements()) {
            Entry.Indexed indexed = (Entry.Indexed) tableElement;
            SourceSpan header = null;
            if (!pair.valueModified && indexed.value instanceof LinkedTomlTable) {
              header = headerOf(indexed, (LinkedTomlTable) indexed.value);
            }
            if (header != null && usable(header, SourceSpan.Kind.HEADER)) {
              // They go before that header, and the blank lines and comments above it, since a table written after it
              // would be read back as a later element of the array
              for (Entry.Indexed added : leading) {
                chunks.add(new SectionChunk(added, arrayPath, true, section, header.start, BEFORE_LINE, arrayOptions));
              }
              leading.clear();
              Group group = new Group(arrayGroup);
              addHeader(header, indexed, arrayPath, group, headerIndent, arrayOptions);
              collectTable(
                  (LinkedTomlTable) indexed.value,
                  arrayPath,
                  Collections.emptyList(),
                  group,
                  header.stop,
                  false,
                  arrayOptions);
              preceding.add(group);
            } else if (preceding.isEmpty()) {
              leading.add(indexed);
            } else {
              // A table the editing API added to the array follows the one before it, which the walk has already read
              chunks
                  .add(
                      new SectionChunk(
                          indexed,
                          arrayPath,
                          true,
                          section,
                          stopOf(preceding),
                          NEW_SECTION,
                          arrayOptions));
            }
          }
          // An array the document wrote no header for goes after the last line of its parent's subtree
          for (Entry.Indexed added : leading) {
            chunks.add(new SectionChunk(added, arrayPath, true, section, -1, NEW_SECTION, arrayOptions));
          }
        }
      }
      contributed.merge(part);
    }

    int lastLine = lastLineIndex(elements, parts);
    collectComments(elements, commentSpans, sections, lastLine, rootTable, lineIndent, tableOptions);
    anchorNewElements(
        elements,
        parts,
        sections,
        subtrees,
        pending,
        sectionPath,
        relative,
        sectionAnchor,
        lastLine,
        rootTable,
        tableOptions);
    return contributed;
  }

  /**
   * Collect a chunk for each unattached comment the document wrote. When only the notation is kept, it is written from
   * the model, as one the editing API added is, since where the blank lines around it go is part of the layout.
   *
   * @param elements The elements of the table.
   * @param commentSpans The span of each element that is such a comment, by index, and {@code null} elsewhere.
   * @param sections Which elements are written as sections rather than as lines.
   * @param lastLine The index of the last element written as a line of the section, or {@code -1}.
   * @param rootTable Whether the table is the document root.
   * @param lineIndent The indentation the comment is written at when only the notation is kept.
   * @param tableOptions The options the table is written with.
   */
  private void collectComments(
      List<TomlElement> elements,
      SourceSpan[] commentSpans,
      boolean[] sections,
      int lastLine,
      boolean rootTable,
      String lineIndent,
      TomlWriteOptions tableOptions) {
    boolean notationOnly = (tableOptions.keep() == TomlWriteOptions.Keep.NOTATION);
    boolean sawSection = false;
    for (int i = 0; i < commentSpans.length; i++) {
      SourceSpan span = commentSpans[i];
      if (span == null) {
        sawSection = sawSection || sections[i];
        continue;
      }
      TomlComment comment = (TomlComment) elements.get(i);
      boolean trailing = !rootTable && (i > lastLine);
      if (notationOnly) {
        // A run written after a section of the root belongs to the root only if a blank line separates it from it
        boolean blankAbove = (i < lastLine) || (rootTable && sawSection);
        chunks.add(new CommentChunk(comment, lineIndent, span.start, SOURCE_LINE, blankAbove, trailing, tableOptions));
      } else {
        chunks.add(new LineChunk(span, null, Collections.<String>emptyList(), lineIndent, trailing, tableOptions));
      }
    }
  }

  /**
   * Collect the one chunk an array of tables that keeps nothing is written as: every table of it, as a
   * {@code [[header]]} section, in the place the first of them had.
   *
   * @param pair The entry holding the array.
   * @param array The array.
   * @param arrayPath The keys from the root to the array.
   * @param section The section the array is written in.
   * @param subtrees Where the group for the text the array was read from is recorded.
   * @param index The index of the entry in its table's sequence.
   * @param arrayOptions The options the array is written with.
   */
  private void collectTableArrayKeepingNothing(
      Entry.KeyValue pair,
      ListTomlArray array,
      List<String> arrayPath,
      Group section,
      Group[] subtrees,
      int index,
      TomlWriteOptions arrayOptions) {
    int anchor = -1;
    int stop = -1;
    for (TomlElement tableElement : array.elements()) {
      Entry.Indexed indexed = (Entry.Indexed) tableElement;
      if (!(indexed.value instanceof LinkedTomlTable)) {
        continue;
      }
      SourceSpan header = pair.valueModified ? null : headerOf(indexed, (LinkedTomlTable) indexed.value);
      if (header != null && usable(header, SourceSpan.Kind.HEADER)) {
        if (anchor < 0) {
          anchor = header.start;
        }
        stop = Math.max(stop, header.stop);
      }
      stop = subtreeStop((LinkedTomlTable) indexed.value, stop);
    }
    subtrees[index] = closedGroup(section, stop);
    chunks
        .add(
            new SectionChunk(
                pair,
                arrayPath,
                false,
                section,
                anchor,
                (anchor >= 0) ? SOURCE_LINE : NEW_SECTION,
                arrayOptions));
  }

  /**
   * A group for text of the document that is written from the model instead: it holds no chunk, and records only the
   * end offset of that text, so that a section added to the enclosing table is written after it.
   *
   * @param section The section the text was written in.
   * @param stop The end of the text, or {@code -1} if none of it has a span.
   * @return The group.
   */
  private static Group closedGroup(Group section, int stop) {
    Group group = new Group(section);
    if (stop >= 0) {
      group.addLine(stop);
    }
    return group;
  }

  /**
   * The end of the last span anywhere in a table: its lines, the unattached comments among them, and the headers and
   * lines of every table and array of tables written in it.
   *
   * @param table The table.
   * @param stop The end found so far.
   * @return The greater of that and the end of the text of this table.
   */
  private int subtreeStop(LinkedTomlTable table, int stop) {
    int last = stop;
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment) {
        SourceSpan span = ((TomlComment) element).span();
        if (span != null && usable(span, SourceSpan.Kind.COMMENT)) {
          last = Math.max(last, span.stop);
        }
        continue;
      }
      Entry.KeyValue pair = (Entry.KeyValue) element;
      if (pair.span != null && usable(pair.span, SourceSpan.Kind.LINE)) {
        last = Math.max(last, pair.span.stop);
      }
      if (pair.value instanceof LinkedTomlTable) {
        last = subtreeStop((LinkedTomlTable) pair.value, headerStop(pair, (LinkedTomlTable) pair.value, last));
      } else if (pair.value instanceof ListTomlArray) {
        for (TomlElement tableElement : ((ListTomlArray) pair.value).elements()) {
          if (!(tableElement instanceof Entry.Indexed)) {
            // An unattached comment in an array, which is written within the brackets of the line the array is on
            continue;
          }
          Entry.Indexed indexed = (Entry.Indexed) tableElement;
          if (indexed.value instanceof LinkedTomlTable) {
            LinkedTomlTable elementTable = (LinkedTomlTable) indexed.value;
            last = subtreeStop(elementTable, headerStop(indexed, elementTable, last));
          }
        }
      }
    }
    return last;
  }

  /** The end of a table's header line, where the document wrote one that still names it, or {@code stop}. */
  private int headerStop(Entry entry, LinkedTomlTable table, int stop) {
    SourceSpan header = headerOf(entry, table);
    return (header != null && usable(header, SourceSpan.Kind.HEADER)) ? Math.max(stop, header.stop) : stop;
  }

  /**
   * Collect a chunk for each element of a table that has no usable span: an entry or an unattached comment the editing
   * API added, or one whose span was read from another document or with a key of a different number of parts.
   *
   * <p>
   * Such an element is written beside its neighbours in the table's sequence: after the nearest element before it that
   * is written as a line of this section, or, where there is none, before the nearest one after it, or, where there is
   * neither, at the section's own anchor. It is written with the indentation of that neighbour, and with a key relative
   * to the section the neighbour's line is in, so a new entry of a table written as dotted keys is written as a dotted
   * key.
   *
   * @param lastLine The index of the last element written as a line of the section, or {@code -1}. A comment after it
   *        is written directly under the line above it, so that a re-parse reads it in this table rather than in the
   *        table whose header follows.
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
      int lastLine,
      boolean rootTable,
      TomlWriteOptions tableOptions) {
    if (pending.isEmpty()) {
      return;
    }
    boolean notationOnly = (tableOptions.keep() == TomlWriteOptions.Keep.NOTATION);
    // A new entry must be written before the first section of this table, since a re-parse reads a line written after a
    // header into the table that header opens. A comment is placed by the rules below.
    int firstSection = firstSectionIndex(sections);
    for (int index : pending) {
      TomlElement element = elements.get(index);
      boolean isComment = element instanceof TomlComment;
      int anchor = sectionAnchor;
      int rank = AFTER_LINE;
      String indent = null;
      boolean blankAbove = isComment && (index < lastLine);
      boolean trailing = isComment && !rootTable && (index > lastLine);
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
            // The line of this table nearest after the element. A comment written after a section goes before that line
            // when the line starts before the section's text ends, which happens only for an unattached comment of the
            // root that the document wrote between two headers within the section's text.
            if (!found || parts[j].firstStart <= anchor) {
              anchor = parts[j].firstStart;
              indent = parts[j].firstIndent;
              rank = BEFORE_LINE;
            }
            break;
          }
        }
      }

      // When only the notation is kept, every line of a section is indented alike, regardless of the indentation of the
      // neighbouring line
      String lineIndent = (indent != null && !notationOnly) ? indent : spaces(options.entryIndent(sectionPath.size()));
      if (isComment) {
        chunks
            .add(new CommentChunk((TomlComment) element, lineIndent, anchor, rank, blankAbove, trailing, tableOptions));
      } else {
        Entry.KeyValue pair = (Entry.KeyValue) element;
        chunks
            .add(new EntryChunk(pair, path(relative, pair.key()), lineIndent, anchor, rank, blankAbove, tableOptions));
      }
    }
  }

  private void addLine(
      SourceSpan span,
      Entry entry,
      Group section,
      Contribution part,
      List<String> keyPath,
      String lineIndent,
      TomlWriteOptions lineOptions) {
    chunks.add(new LineChunk(span, entry, keyPath, lineIndent, false, lineOptions));
    section.addLine(span.stop);
    part.add(span.start, span.stop, indentOf(span));
  }

  private void addHeader(
      SourceSpan header,
      Entry entry,
      List<String> headerPath,
      Group group,
      String headerIndent,
      TomlWriteOptions tableOptions) {
    chunks.add(new LineChunk(header, entry, headerPath, headerIndent, false, tableOptions));
    group.addLine(header.stop);
  }

  private void addSection(TomlEntry entry, List<String> path, Group section, TomlWriteOptions tableOptions) {
    chunks.add(new SectionChunk(entry, path, false, section, -1, NEW_SECTION, tableOptions));
  }

  /**
   * Whether a table with no header of its own can be written as the dotted keys of the section around it.
   *
   * <p>
   * A dotted key writes one entry, so a table with no entries cannot be written as dotted keys, and a comment written
   * among those keys would be read in the section rather than in this table, so such a table needs a header.
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
   * The span of the header line a table was opened by, where the table is still at the path that header names.
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
   * Whether a span's source is this document and its kind is the one expected. A span from another document gives no
   * offset in this one.
   */
  @SuppressWarnings("ReferenceEquality") // compares the span's source by identity
  private boolean usable(SourceSpan span, SourceSpan.Kind kind) {
    return span.kind == kind && span.source == source;
  }

  /**
   * Whether an entry is written as a {@code key = value} line, rather than under a header of its own.
   *
   * <p>
   * Unlike every other table, an inline table is written on the line of the entry holding it. An array the document
   * wrote as a literal is written as a literal regardless of its elements, unlike the array of the tables of
   * {@code [[x]]} headers; an array stored through the editing API has no span, so it is written in the default style,
   * and the entry holding it keeps its line only if that style writes the array on one.
   */
  private static boolean writtenOnALine(Entry entry) {
    Object value = entry.value.get();
    if (value instanceof LinkedTomlTable) {
      return ((LinkedTomlTable) value).isInline();
    }
    if (value instanceof ListTomlArray && (entry.valueModified || ((ListTomlArray) value).isTableArray())) {
      return Serializer.isLine(entry, true);
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

  /**
   * The index of the last element of a table written as a line of the section the table is in: a {@code key = value}
   * line, whether the document holds it or the editing API added it, or a table the document opened with a dotted key,
   * whose lines are written among this table's.
   *
   * @param elements The elements of the table.
   * @param parts The lines each element contributed to the section.
   * @return The index, or {@code -1} if no element is written as a line.
   */
  private static int lastLineIndex(List<TomlElement> elements, Contribution[] parts) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      TomlElement element = elements.get(i);
      if (element instanceof Entry && (writtenOnALine((Entry) element) || parts[i].hasLines())) {
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
    return lastLineOf(text(from, token - 1));
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
   * The blank lines of a span's leading whitespace: everything before the indentation of its first line, which are
   * written before the lines that replace the span's first line.
   */
  private static String blankLinesOf(String leading) {
    return leading.substring(0, leading.lastIndexOf('\n') + 1);
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
   * Request a blank line, unless the output is empty or already ends with one, so that blank lines neither open the
   * document nor double up.
   */
  private void requestBlankLine() {
    if (written && !(atLineStart && previousLineBlank)) {
      blankLineOwed = true;
    }
  }

  /**
   * Whether a header written anew is separated from what precedes it by a blank line: always, unless it directly
   * follows the header of a table containing it and the options leave out the blank line there.
   *
   * @param path The path of the header.
   * @param headerOptions The options the header is written with.
   */
  private boolean separateHeader(List<String> path, TomlWriteOptions headerOptions) {
    return headerOptions.blankLineBetweenNestedHeaders() || !Serializer.isNestedHeader(lastHeader, path);
  }

  /**
   * Write the pending blank line, if there is one.
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
   * Write an empty comment line in place of the blank line that would separate the run about to be written from the one
   * just written. Two runs after the last line of a table are written as one run this way, since with a blank line
   * between them a re-parse reads the second as an unattached comment of the root.
   *
   * @param indent The indentation of the line.
   */
  private void joinRun(String indent) throws IOException {
    startLine();
    blankLineOwed = false;
    append(indent);
    append("#");
    append(lineSeparator);
  }

  /**
   * The lines this table contributes to its section, whose offsets anchor the elements the editing API added next to
   * them.
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
   * A section - a table written under a header, or the root - or an array of tables, whose end offset anchors the
   * sections added to it that have no span.
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

    /** The path of the header this chunk ends with, or {@code null} if it does not end with one. */
    @Nullable
    List<String> headerPath() {
      return null;
    }
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

    /**
     * The key the line is written with, relative to the section it is written in, or for a header, the path of its
     * table from the root.
     */
    private final List<String> keyPath;

    /** The indentation this line is written at when only the notation is kept. */
    private final String lineIndent;

    /** Whether this is an unattached comment after the last line of its table. */
    private final boolean trailing;

    /** The options this line is written with, which say how much of the table holding it is kept. */
    private final TomlWriteOptions lineOptions;

    LineChunk(
        SourceSpan span,
        @Nullable Entry entry,
        List<String> keyPath,
        String lineIndent,
        boolean trailing,
        TomlWriteOptions lineOptions) {
      super(SOURCE_LINE);
      this.span = span;
      this.entry = entry;
      this.keyPath = keyPath;
      this.lineIndent = lineIndent;
      this.trailing = trailing;
      this.lineOptions = lineOptions;
    }

    @Override
    int anchor() {
      return span.start;
    }

    @Override
    @Nullable
    List<String> headerPath() {
      return (span.kind == SourceSpan.Kind.HEADER) ? keyPath : null;
    }

    @Override
    void write() throws IOException {
      if (lineOptions.keep() == TomlWriteOptions.Keep.NOTATION) {
        writeFromParts();
        return;
      }
      startLine();
      String leading = text(span.start, firstToken(span) - 1);
      Entry lineEntry = entry;
      if (lineEntry == null) {
        writeComment(leading);
        return;
      }
      if (afterComment && leading.indexOf('\n') < 0) {
        // In the document that unattached comment was followed by a blank line, which was part of the text between them
        // that is not written, so a blank line is requested
        requestBlankLine();
      }
      boolean blankWritten = flushBlankLine();
      boolean modelComments = lineEntry.commentsModified();
      String indent = indentOf(span);
      // The line is assembled whole, so that a value written anew into it is laid out at the column it reaches
      StringBuilder line = new StringBuilder();
      if (modelComments) {
        appendCommentAbove(line, lineEntry, blankWritten ? "" : blankLinesOf(leading), indent);
      } else {
        line.append(blankWritten ? lastLineOf(leading) : leading);
        // The comment run above the line and the indentation of the line itself, as they were written
        line.append(text(firstToken(span), span.keyStart - 1));
      }
      if (span.kind == SourceSpan.Kind.HEADER) {
        line.append(text(span.keyStart, span.keyStop));
      } else {
        line.append(text(span.keyStart, span.valueStart - 1));
        appendValue(line, lineEntry, indent);
      }
      // Input the parser skipped between the value or header and the newline lies before the tail, so it is not written
      line.append(modelComments ? tailWithCommentAfter(lineEntry) : text(span.tailStart, span.stop));
      append(line);
      afterComment = false;
    }

    /**
     * Write an unattached comment from the text it was read from, after the blank lines the document had above it. A
     * comment after the last line of its table is written directly under the line above it instead, regardless of the
     * blank lines in the document, since a re-parse reads a run separated from that line by a blank line as an
     * unattached comment of the root; and where that line is a run as well, an empty comment line joins the two.
     *
     * @param leading The blank lines and indentation the document wrote above the comment.
     */
    private void writeComment(String leading) throws IOException {
      if (trailing) {
        if (afterComment) {
          joinRun(indentOf(span));
        }
        append(lastLineOf(leading));
      } else {
        if (afterComment && leading.indexOf('\n') < 0) {
          // In the document that unattached comment was followed by a blank line, which was part of the text between
          // them that is not written, so a blank line is requested
          requestBlankLine();
        }
        // A blank line written above this one already separates it from what came before
        append(flushBlankLine() ? lastLineOf(leading) : leading);
      }
      append(text(firstToken(span), span.stop));
      afterComment = true;
    }

    /**
     * Write the line from its parts, in the layout the options set: the indentation of its section, the comments the
     * model holds, the key and, for a header, the text the document wrote them as, and the value laid out anew around
     * the literal each scalar was written with. A line keeps one blank line above it where the document wrote any, and
     * a header is separated from what precedes it, unless it directly follows the header of a table containing it and
     * the options leave out the blank line there.
     */
    private void writeFromParts() throws IOException {
      Entry lineEntry = entry;
      assert lineEntry != null : "an unattached comment is written from the model";
      boolean header = (span.kind == SourceSpan.Kind.HEADER);
      if (header) {
        if (separateHeader(keyPath, lineOptions)) {
          requestBlankLine();
        }
      } else if (afterComment || text(span.start, firstToken(span) - 1).indexOf('\n') >= 0) {
        requestBlankLine();
      }
      startLine();
      flushBlankLine();
      StringBuilder line = new StringBuilder();
      Serializer writer = Serializer.defaultStyle(line, lineOptions);
      if (header) {
        writer.writeHeaderText(lineIndent, text(span.keyStart, span.keyStop), lineEntry.comments());
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
      TomlComment above = lineEntry.comment(TomlComment.Placement.ABOVE);
      if (above != null) {
        Serializer.defaultStyle(line, lineOptions).writeCommentLines(above, indent);
      }
      line.append(indent);
    }

    /**
     * Append the value of the line: the text it was read as; an array or inline table edited in place, written within
     * the brackets it was read in; or, where the value has no usable span, the value in the default style.
     *
     * @param line The line so far, which the value is written at the end of.
     * @param lineEntry The entry the line holds.
     * @param indent The indentation of the line, which the elements of an array written over lines are indented from.
     */
    private void appendValue(StringBuilder line, Entry lineEntry, String indent) throws IOException {
      ValueSpan value = span.writtenValue(lineEntry.value);
      if (value != null) {
        line.append(text(value.start, value.stop));
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
                lineOptions);
        return;
      }
      int column = width(indent) + width(text(span.keyStart, span.valueStart - 1));
      Serializer
          .defaultStyle(line, ElementContainer.optionsWithin(lineEntry.value, lineOptions))
          .writeEntryValue(lineEntry.value, indent, column);
    }

    /**
     * What follows the value, or the header, on a line whose comments the model holds: the comment after it, written
     * where the document wrote its own, then the newline the line ended with.
     *
     * @param lineEntry The entry the line holds.
     * @return The text.
     */
    private String tailWithCommentAfter(Entry lineEntry) {
      String newline = text(span.newlineStart, span.stop);
      TomlComment after = lineEntry.comment(TomlComment.Placement.AFTER);
      if (after == null) {
        return newline;
      }
      // The spacing the document wrote before its own comment, or the two spaces of the default style
      String spacing = (span.afterStart >= 0) ? text(span.tailStart, span.afterStart - 1) : "  ";
      return spacing + '#' + after.rawLines().get(0) + newline;
    }
  }

  /**
   * A {@code key = value} line with no span, written in the default style beside the lines around it.
   */
  private final class EntryChunk extends Chunk {

    private final TomlEntry entry;
    private final List<String> keyPath;
    private final String lineIndent;
    private final int anchor;
    private final boolean blankAbove;

    /** The options this line is written with, which say how much of the table holding it is kept. */
    private final TomlWriteOptions lineOptions;

    EntryChunk(
        TomlEntry entry,
        List<String> keyPath,
        String lineIndent,
        int anchor,
        int rank,
        boolean blankAbove,
        TomlWriteOptions lineOptions) {
      super(rank);
      this.entry = entry;
      this.keyPath = keyPath;
      this.lineIndent = lineIndent;
      this.anchor = anchor;
      this.blankAbove = blankAbove;
      this.lineOptions = lineOptions;
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
      Serializer.defaultStyle(text, lineOptions).writeKeyValue(lineIndent, keyPath, entry);
      append(text);
      afterComment = false;
    }
  }

  /**
   * An unattached comment written from the model, with a blank line above it and one below, so that a re-parse reads it
   * in the table it was added to and as an unattached comment. A comment after the last line of its table is written
   * directly under the line above it instead, since a re-parse reads a run separated from that line by a blank line as
   * an unattached comment of the root; where that line is a run as well, an empty comment line joins the two.
   */
  private final class CommentChunk extends Chunk {

    private final TomlComment comment;
    private final String lineIndent;
    private final int anchor;
    private final boolean blankAbove;
    private final boolean trailing;

    /** The options these lines are written with, which say how much of the table holding them is kept. */
    private final TomlWriteOptions lineOptions;

    CommentChunk(
        TomlComment comment,
        String lineIndent,
        int anchor,
        int rank,
        boolean blankAbove,
        boolean trailing,
        TomlWriteOptions lineOptions) {
      super(rank);
      this.comment = comment;
      this.lineIndent = lineIndent;
      this.anchor = anchor;
      this.blankAbove = blankAbove;
      this.trailing = trailing;
      this.lineOptions = lineOptions;
    }

    @Override
    int anchor() {
      return anchor;
    }

    @Override
    void write() throws IOException {
      startLine();
      if (trailing && afterComment) {
        joinRun(lineIndent);
      } else if (blankAbove || afterComment) {
        requestBlankLine();
      }
      flushBlankLine();
      StringBuilder text = new StringBuilder();
      Serializer.defaultStyle(text, lineOptions).writeCommentLines(comment, lineIndent);
      append(text);
      afterComment = true;
    }
  }

  /**
   * A table, or one table of an array of tables, written in the default style as a section of its own: one the document
   * has no header for, which goes after the last line of the subtree it belongs to, or one that keeps nothing, which is
   * written at the offset of its header.
   */
  private final class SectionChunk extends Chunk {

    private final TomlEntry entry;
    private final List<String> path;
    private final boolean arrayTable;
    private final Group group;

    /** Where this section goes, or {@code -1} to take the group's own anchor once the whole document is walked. */
    private final int after;

    /** The options this section is written with, which say how much of the table or array is kept. */
    private final TomlWriteOptions blockOptions;

    SectionChunk(
        TomlEntry entry,
        List<String> path,
        boolean arrayTable,
        Group group,
        int after,
        int rank,
        TomlWriteOptions blockOptions) {
      super(rank);
      this.entry = entry;
      this.path = path;
      this.arrayTable = arrayTable;
      this.group = group;
      this.after = after;
      this.blockOptions = blockOptions;
    }

    @Override
    int anchor() {
      return (after >= 0) ? after : group.sectionAnchor();
    }

    @Override
    void write() throws IOException {
      startLine();
      if (separateHeader(path, blockOptions)) {
        requestBlankLine();
      }
      flushBlankLine();
      StringBuilder text = new StringBuilder();
      Serializer block = Serializer.blockStyle(text, blockOptions);
      if (arrayTable) {
        block.writeArrayTableSection(entry, new ArrayList<>(path));
      } else {
        block.writeSections(entry, new ArrayList<>(path));
      }
      append(text);
      afterComment = false;
      if (rank == BEFORE_LINE) {
        // A header of the document follows, and is separated from this section as from any other
        requestBlankLine();
      }
    }
  }
}
