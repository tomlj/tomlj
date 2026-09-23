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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

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
 * A span is immutable, and names the source it reads, so a span copied into another document still resolves. The
 * offsets of a comment run also serve as the boundaries of the whitespace around it.
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
   * Collects the parts of a span as they are read. A part never set is {@code -1}, and {@link SourceSpan#keyParts} is
   * {@code 0} where no key is set.
   */
  static final class Builder {
    private final Source source;
    private final Kind kind;
    private final int start;
    private int aboveStart = -1;
    private int aboveStop = -1;
    private int keyStart = -1;
    private int keyStop = -1;
    private int keyParts;
    private int valueStart = -1;
    private int commaOffset = -1;
    private int afterStart = -1;
    private int afterStop = -1;
    private int tailStart = -1;
    private int newlineStart = -1;

    /**
     * Start a span.
     *
     * @param source The text the offsets address.
     * @param kind What the span covers.
     * @param start The first offset of the span's own text.
     */
    Builder(Source source, Kind kind, int start) {
      this.source = source;
      this.kind = kind;
      this.start = start;
    }

    /**
     * Set the comment run above what the span covers, or the comment a COMMENT covers.
     *
     * @param run The comment run.
     * @return This builder.
     */
    Builder above(ParserRuleContext run) {
      return above(run.getStart().getStartIndex(), run.getStop().getStopIndex());
    }

    /**
     * Set the comment a COMMENT covers, where it is not one node: a comment ending a line of an array or inline table
     * includes the newline after it.
     *
     * @param start The first {@code #} of the comment.
     * @param stop The last offset of the comment.
     * @return This builder.
     */
    Builder above(int start, int stop) {
      this.aboveStart = start;
      this.aboveStop = stop;
      return this;
    }

    /**
     * Set the key or header.
     *
     * @param key The key as written, or the whole header, brackets included.
     * @param parts The number of keys the written key has.
     * @return This builder.
     */
    Builder key(ParserRuleContext key, int parts) {
      this.keyStart = key.getStart().getStartIndex();
      this.keyStop = key.getStop().getStopIndex();
      this.keyParts = parts;
      return this;
    }

    /**
     * Set where the value starts.
     *
     * @param start The first offset of the value.
     * @return This builder.
     */
    Builder value(int start) {
      this.valueStart = start;
      return this;
    }

    /**
     * Set the comma of an element.
     *
     * @param offset The offset of the comma in an element's tail.
     * @return This builder.
     */
    Builder comma(int offset) {
      this.commaOffset = offset;
      return this;
    }

    /**
     * Set the comment after the value or header.
     *
     * @param comment The comment token.
     * @return This builder.
     */
    Builder after(Token comment) {
      this.afterStart = comment.getStartIndex();
      this.afterStop = comment.getStopIndex();
      return this;
    }

    /**
     * Set where the tail starts.
     *
     * @param start The first offset copied after the value or header.
     * @return This builder.
     */
    Builder tail(int start) {
      this.tailStart = start;
      return this;
    }

    /**
     * Set the newline ending a LINE or HEADER.
     *
     * @param newline The newline token.
     * @return This builder.
     */
    Builder newline(Token newline) {
      this.newlineStart = newline.getStartIndex();
      return this;
    }

    /**
     * Build the span.
     *
     * @param stop The last offset of the span's own text.
     * @return The span.
     */
    SourceSpan build(int stop) {
      return new SourceSpan(this, stop);
    }
  }

  private SourceSpan(Builder builder, int stop) {
    this.source = builder.source;
    this.kind = builder.kind;
    this.start = builder.start;
    this.aboveStart = builder.aboveStart;
    this.aboveStop = builder.aboveStop;
    this.keyStart = builder.keyStart;
    this.keyStop = builder.keyStop;
    this.keyParts = builder.keyParts;
    this.valueStart = builder.valueStart;
    this.commaOffset = builder.commaOffset;
    this.afterStart = builder.afterStart;
    this.afterStop = builder.afterStop;
    this.tailStart = builder.tailStart;
    this.newlineStart = builder.newlineStart;
    this.stop = stop;
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
