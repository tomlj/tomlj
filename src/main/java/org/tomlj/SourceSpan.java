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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Where a line, a table header, an unattached comment or an element of an array or inline table was written in the text
 * its document was parsed from.
 *
 * <p>
 * Every offset is an inclusive code-point offset into {@link #source}, and is {@code -1} where this span has no such
 * part. What a span covers depends on its {@link #kind}:
 *
 * <ul>
 * <li>{@link Kind#LINE}: a {@code key = value} line of a section, from the blank lines above it to its newline.</li>
 * <li>{@link Kind#HEADER}: a {@code [a.b]} or {@code [[a.b]]} line, which has a key and no value.</li>
 * <li>{@link Kind#COMMENT}: an unattached comment, whose own lines are the {@code above} range.</li>
 * <li>{@link Kind#ELEMENT}: an element of an array, or an entry of an inline table, from the whitespace after the
 * element before it to the end of its own tail.</li>
 * </ul>
 *
 * <p>
 * A span is immutable, and names the source it reads, so a span copied into another document still resolves. The text
 * of the comments a span records is never read from the source: only the whitespace around them is, since a comment is
 * written from the model.
 */
final class SourceSpan {

  /**
   * What a span covers.
   */
  enum Kind {
    /** A {@code key = value} line of a section. */
    LINE,
    /** A {@code [a.b]} or {@code [[a.b]]} line. */
    HEADER,
    /** An unattached comment. */
    COMMENT,
    /** An element of an array, or an entry of an inline table. */
    ELEMENT
  }

  /** The text this span's offsets address. */
  final Source source;

  /** What this span covers. */
  final Kind kind;

  /**
   * The first offset of this span's own text: the blank lines above it and the indentation of its first line for a
   * LINE, HEADER or COMMENT of a section; the first offset after the element before it, or after the opening bracket,
   * for an ELEMENT or a COMMENT written between brackets.
   */
  final int start;

  /**
   * The first offset of the comment run above what this span covers, its first {@code #}, or {@code -1} if there is
   * none. For a COMMENT this is the comment's own first {@code #}.
   */
  final int aboveStart;

  /**
   * The last offset of that run: the newline ending its last line, or that line's last character when the document ends
   * without a newline. {@code -1} if there is no run.
   */
  final int aboveStop;

  /**
   * The first offset of the key as written, dotted form included, or of the whole header, brackets included. {@code -1}
   * for an array element and for a COMMENT.
   */
  final int keyStart;

  /** The last offset of that key or header, or {@code -1} where there is none. */
  final int keyStop;

  /**
   * The number of keys the written key has, {@code 2} for {@code a.b}, so that a writer can tell whether the written
   * key still names the entry from the section it is written in. {@code 0} where there is no key.
   */
  final int keyParts;

  /** The first offset of the value, or {@code -1} for a HEADER or a COMMENT. The value's own extent is on the value. */
  final int valueStart;

  /** ELEMENT only: the offset of the comma written in this element's tail, or {@code -1} if there is none. */
  final int commaOffset;

  /** The first offset of the comment after this element, its {@code #}, or {@code -1} if there is none. */
  final int afterStart;

  /** The last offset of that comment, or {@code -1} if there is none. */
  final int afterStop;

  /**
   * The first offset of the text copied after the value or header, through {@link #stop}: the whitespace before the
   * comment after it, that comment, the comma of an element, and the newline ending the line. Normally the offset after
   * the value, or after the header where there is no value; when the parser reported and skipped stray input between a
   * header and its newline, {@link #newlineStart}, so that the input it skipped is not copied. {@code -1} for a
   * COMMENT.
   */
  final int tailStart;

  /**
   * LINE and HEADER: the first offset of the newline ending the line, which equals {@code stop + 1} for the empty
   * newline the lexer supplies at the end of a document that ends without one. {@code -1} for a COMMENT or an ELEMENT,
   * whose newline, if any, is part of the text through {@link #stop}.
   */
  final int newlineStart;

  /**
   * The last offset of this span's own text: the newline of a LINE or HEADER, {@link #aboveStop} for a COMMENT, or the
   * end of the tail for an ELEMENT. {@code newlineStart - 1} where the document ends without a newline.
   */
  final int stop;

  /**
   * A {@code key = value} line of a section.
   *
   * @param source The text the offsets address.
   * @param start The first offset of the line's leading whitespace.
   * @param aboveStart The first {@code #} of the comment run above the line, or {@code -1}.
   * @param aboveStop The last offset of that run, or {@code -1}.
   * @param keyStart The first offset of the key as written.
   * @param keyStop The last offset of the key as written.
   * @param keyParts The number of keys the written key has.
   * @param valueStart The first offset of the value.
   * @param afterStart The {@code #} of the comment after the value, or {@code -1}.
   * @param afterStop The last offset of that comment, or {@code -1}.
   * @param tailStart The first offset copied after the value.
   * @param newlineStart The first offset of the newline ending the line.
   * @param stop The last offset of that newline.
   * @return The span.
   */
  static SourceSpan line(
      Source source,
      int start,
      int aboveStart,
      int aboveStop,
      int keyStart,
      int keyStop,
      int keyParts,
      int valueStart,
      int afterStart,
      int afterStop,
      int tailStart,
      int newlineStart,
      int stop) {
    return new SourceSpan(
        source,
        Kind.LINE,
        start,
        aboveStart,
        aboveStop,
        keyStart,
        keyStop,
        keyParts,
        valueStart,
        -1,
        afterStart,
        afterStop,
        tailStart,
        newlineStart,
        stop);
  }

  /**
   * A {@code [a.b]} or {@code [[a.b]]} line, which has a key and no value.
   *
   * @param source The text the offsets address.
   * @param start The first offset of the line's leading whitespace.
   * @param aboveStart The first {@code #} of the comment run above the header, or {@code -1}.
   * @param aboveStop The last offset of that run, or {@code -1}.
   * @param keyStart The first offset of the header, its opening bracket.
   * @param keyStop The last offset of the header.
   * @param keyParts The number of keys the header's key has.
   * @param afterStart The {@code #} of the comment after the header, or {@code -1}.
   * @param afterStop The last offset of that comment, or {@code -1}.
   * @param tailStart The first offset copied after the header.
   * @param newlineStart The first offset of the newline ending the line.
   * @param stop The last offset of that newline.
   * @return The span.
   */
  static SourceSpan header(
      Source source,
      int start,
      int aboveStart,
      int aboveStop,
      int keyStart,
      int keyStop,
      int keyParts,
      int afterStart,
      int afterStop,
      int tailStart,
      int newlineStart,
      int stop) {
    return new SourceSpan(
        source,
        Kind.HEADER,
        start,
        aboveStart,
        aboveStop,
        keyStart,
        keyStop,
        keyParts,
        -1,
        -1,
        afterStart,
        afterStop,
        tailStart,
        newlineStart,
        stop);
  }

  /**
   * An unattached comment, whose own lines are its {@code above} range.
   *
   * @param source The text the offsets address.
   * @param start The first offset of the comment's leading whitespace.
   * @param aboveStart The first {@code #} of the comment.
   * @param aboveStop The last offset of the comment.
   * @return The span.
   */
  static SourceSpan comment(Source source, int start, int aboveStart, int aboveStop) {
    return new SourceSpan(
        source,
        Kind.COMMENT,
        start,
        aboveStart,
        aboveStop,
        -1,
        -1,
        0,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        aboveStop);
  }

  /**
   * An element of an array, or an entry of an inline table.
   *
   * @param source The text the offsets address.
   * @param start The first offset after the element before this one, or after the opening bracket.
   * @param aboveStart The first {@code #} of the comment run above the element, or {@code -1}.
   * @param aboveStop The last offset of that run, or {@code -1}.
   * @param keyStart The first offset of the key as written, or {@code -1} for an array element.
   * @param keyStop The last offset of the key as written, or {@code -1} for an array element.
   * @param keyParts The number of keys the written key has, or {@code 0} for an array element.
   * @param valueStart The first offset of the value.
   * @param commaOffset The offset of the comma in this element's tail, or {@code -1}.
   * @param afterStart The {@code #} of the comment after the value, or {@code -1}.
   * @param afterStop The last offset of that comment, or {@code -1}.
   * @param tailStart The first offset copied after the value.
   * @param stop The last offset of the tail.
   * @return The span.
   */
  static SourceSpan element(
      Source source,
      int start,
      int aboveStart,
      int aboveStop,
      int keyStart,
      int keyStop,
      int keyParts,
      int valueStart,
      int commaOffset,
      int afterStart,
      int afterStop,
      int tailStart,
      int stop) {
    return new SourceSpan(
        source,
        Kind.ELEMENT,
        start,
        aboveStart,
        aboveStop,
        keyStart,
        keyStop,
        keyParts,
        valueStart,
        commaOffset,
        afterStart,
        afterStop,
        tailStart,
        -1,
        stop);
  }

  private SourceSpan(
      Source source,
      Kind kind,
      int start,
      int aboveStart,
      int aboveStop,
      int keyStart,
      int keyStop,
      int keyParts,
      int valueStart,
      int commaOffset,
      int afterStart,
      int afterStop,
      int tailStart,
      int newlineStart,
      int stop) {
    this.source = source;
    this.kind = kind;
    this.start = start;
    this.aboveStart = aboveStart;
    this.aboveStop = aboveStop;
    this.keyStart = keyStart;
    this.keyStop = keyStop;
    this.keyParts = keyParts;
    this.valueStart = valueStart;
    this.commaOffset = commaOffset;
    this.afterStart = afterStart;
    this.afterStop = afterStop;
    this.tailStart = tailStart;
    this.newlineStart = newlineStart;
    this.stop = stop;
  }

  /**
   * The text of the value written between this span's key and its tail, where the document still holds text that
   * describes the value.
   *
   * @param value The value the line or element holds.
   * @return The span of the value's text, or {@code null} where it was replaced or edited through the editing API, or
   *         came from another line or another document, so that the text between the key and the tail is no longer its
   *         own.
   */
  @Nullable
  @SuppressWarnings("ReferenceEquality") // the value's text is part of this span's own text, not of a text equal to it
  ValueSpan writtenValue(Value value) {
    ValueSpan written = value.writtenSpan();
    return (written != null && written.source == source && written.start == valueStart) ? written : null;
  }

  @Override
  public String toString() {
    return "SourceSpan{"
        + kind
        + " ["
        + start
        + ", "
        + stop
        + "] above=["
        + aboveStart
        + ", "
        + aboveStop
        + "] key=["
        + keyStart
        + ", "
        + keyStop
        + "]/"
        + keyParts
        + " value="
        + valueStart
        + " comma="
        + commaOffset
        + " after=["
        + afterStart
        + ", "
        + afterStop
        + "] tail="
        + tailStart
        + " newline="
        + newlineStart
        + '}';
  }
}
