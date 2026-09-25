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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The text a document was parsed from, addressed by the offsets its tokens carry.
 *
 * <p>
 * ANTLR indexes a character stream by code point, so every offset a token reports is a code-point offset, while a
 * {@code String} is indexed by {@code char}. The two differ from the first character above U+FFFF onwards, so the
 * offsets of those characters are found once, when the source is built, and each lookup translates through them.
 */
final class Source {

  private static final int[] NO_SURROGATE_PAIRS = new int[0];

  private final String text;

  /**
   * The code-point offset of every character of {@link #text} that is written as a surrogate pair, and so occupies two
   * {@code char}s of it, in ascending order.
   */
  private final int[] surrogatePairOffsets;

  private final int length;

  /**
   * The constructs written in this source that an earlier version of TOML cannot write, in offset order. The parser
   * records them as it reads the source; a writer copying text for a version looks them up.
   */
  private final List<VersionNeed> versionNeeds = new ArrayList<>();

  Source(String text) {
    this.text = text;
    this.surrogatePairOffsets = findSurrogatePairOffsets(text);
    this.length = text.length() - surrogatePairOffsets.length;
  }

  private static int[] findSurrogatePairOffsets(String text) {
    int pairCount = 0;
    for (int i = 0; i < text.length();) {
      int charCount = Character.charCount(text.codePointAt(i));
      if (charCount > 1) {
        pairCount++;
      }
      i += charCount;
    }
    if (pairCount == 0) {
      return NO_SURROGATE_PAIRS;
    }
    int[] offsets = new int[pairCount];
    int pairIndex = 0;
    for (int i = 0, offset = 0; i < text.length(); offset++) {
      int charCount = Character.charCount(text.codePointAt(i));
      if (charCount > 1) {
        offsets[pairIndex++] = offset;
      }
      i += charCount;
    }
    return offsets;
  }

  /**
   * The number of characters in this source.
   *
   * @return The length, in code points.
   */
  int length() {
    return length;
  }

  /**
   * A range of this source.
   *
   * @param start The first offset, inclusive.
   * @param stop The last offset, inclusive.
   * @return The text between the offsets, or {@code ""} if {@code start} is greater than {@code stop}.
   */
  String text(int start, int stop) {
    return (start > stop) ? "" : text.substring(charIndex(start), charIndex(stop + 1));
  }

  /**
   * A range of this source, to be written for a version of TOML.
   *
   * @param start The first offset, inclusive.
   * @param stop The last offset, inclusive.
   * @param version The version of TOML the text is written for.
   * @return The text between the offsets, or {@code ""} if {@code start} is greater than {@code stop}.
   * @throws IllegalArgumentException If the range holds a construct that version of TOML cannot write, such as a
   *         {@code \e} escape for TOML 1.0.0.
   */
  String text(int start, int stop, TomlVersion version) {
    for (int i = firstNeedFrom(start); i < versionNeeds.size() && versionNeeds.get(i).offset <= stop; i++) {
      VersionNeed need = versionNeeds.get(i);
      if (need.version.after(version)) {
        throw new IllegalArgumentException(
            need.what
                + " originally at line "
                + need.position.line()
                + ", column "
                + need.position.column()
                + " needs TOML "
                + need.version.number()
                + " and cannot be written for TOML "
                + version.number());
      }
    }
    return text(start, stop);
  }

  /**
   * Record that a construct written in this source needs a version of TOML, so that a writer copying the text holding
   * it for an earlier version refuses to. The parser calls this where it accepts the construct; the constructs are
   * whole tokens, which no copied range splits.
   *
   * @param offset The offset the construct starts at.
   * @param version The earliest version of TOML that allows the construct.
   * @param what The construct, as a sentence names it: {@code "The escape sequence '\e'"}.
   * @param position Where the construct was written, for the message.
   */
  void requireVersion(int offset, TomlVersion version, String what, TomlPosition position) {
    // Nested constructs are recorded out of offset order: an inline table's line breaks before the values between them
    versionNeeds.add(firstNeedFrom(offset), new VersionNeed(offset, version, what, position));
  }

  /** The index of the first recorded construct at or after an offset, which is the size of the list if none is. */
  private int firstNeedFrom(int offset) {
    int low = 0;
    int high = versionNeeds.size();
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (versionNeeds.get(mid).offset < offset) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  /** A construct written in this source that an earlier version of TOML cannot write. */
  private static final class VersionNeed {

    final int offset;
    final TomlVersion version;
    final String what;
    final TomlPosition position;

    VersionNeed(int offset, TomlVersion version, String what, TomlPosition position) {
      this.offset = offset;
      this.version = version;
      this.what = what;
      this.position = position;
    }
  }

  /**
   * The line separator this document is written with, read from its first newline.
   *
   * @return {@code "\r\n"} if the first newline is preceded by a carriage return, {@code "\n"} if it is not, or
   *         {@code null} if the document holds no newline.
   */
  @Nullable
  String lineSeparator() {
    int newline = text.indexOf('\n');
    if (newline < 0) {
      return null;
    }
    return ((newline > 0) && (text.charAt(newline - 1) == '\r')) ? "\r\n" : "\n";
  }

  /**
   * The first offset of the whitespace written directly above and before an offset.
   *
   * <p>
   * The scan walks back over the spaces and tabs before {@code offset} on its own line, and then over the blank lines
   * above that line, a blank line being one that holds only spaces and tabs. A line that holds anything else, including
   * a line the parser rejected, stops the scan, so the newline ending it is not included. A newline is {@code \n} or
   * {@code \r\n}; a carriage return on its own is content.
   *
   * @param offset An offset, from {@code 0} up to and including {@link #length()}.
   * @return The first offset of that whitespace, which is {@code offset} itself when there is none.
   */
  int leadingStart(int offset) {
    int index = charIndex(offset);
    while (index > 0 && isSpaceOrTab(text.charAt(index - 1))) {
      index--;
    }
    // Each iteration moves to the start of the blank line ending at the newline before index, which is the start of a
    // line once the spaces and tabs have been walked back over.
    while (index > 0 && text.charAt(index - 1) == '\n') {
      int lineStop = index - 2;
      if (lineStop >= 0 && text.charAt(lineStop) == '\r') {
        lineStop--;
      }
      int i = lineStop;
      while (i >= 0 && isSpaceOrTab(text.charAt(i))) {
        i--;
      }
      if (i >= 0 && text.charAt(i) != '\n') {
        break;
      }
      index = i + 1;
    }
    return codePointOffset(index);
  }

  private static boolean isSpaceOrTab(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * Translate a code-point offset into an index into {@link #text}.
   *
   * @param offset A code-point offset, from {@code 0} up to and including {@link #length()}.
   * @return The index of the same character.
   */
  private int charIndex(int offset) {
    if (surrogatePairOffsets.length == 0) {
      return offset;
    }
    int found = Arrays.binarySearch(surrogatePairOffsets, offset);
    int precedingPairs = (found >= 0) ? found : -(found + 1);
    return offset + precedingPairs;
  }

  /**
   * Translate an index into {@link #text} into a code-point offset, the inverse of {@link #charIndex(int)}.
   *
   * @param index An index into {@link #text}, never within a surrogate pair.
   * @return The code-point offset of the same character.
   */
  private int codePointOffset(int index) {
    if (surrogatePairOffsets.length == 0) {
      return index;
    }
    // The pair recorded at i sits at index surrogatePairOffsets[i] + i, since each pair before it adds one char. The
    // search counts the pairs written before index, which is how many chars index is ahead of its code-point offset.
    int low = 0;
    int high = surrogatePairOffsets.length;
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (surrogatePairOffsets[mid] + mid < index) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return index - low;
  }
}
