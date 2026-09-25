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

import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The root table of a parsed document, paired with the errors reported while reading it.
 *
 * <p>
 * A subclass of {@link LinkedTomlTable} rather than a delegator, so that every method added to {@link TomlTable} or
 * {@link MutableTomlTable} is inherited, with no delegating method to add by hand.
 */
final class ParsedTomlTable extends LinkedTomlTable implements TomlParseResult {

  private final AccumulatingErrorListener errorListener;

  // The text the document was parsed from, or null if the parse options kept none. Every span recorded in this document
  // has it as its source.
  private final @Nullable Source source;

  // The first offset of the blank lines the document ends with, or -1 when there are none: when nothing follows the
  // newline ending its last line that is not blank (or that line itself, if it has no newline), when the document is
  // empty, or when no source was kept.
  private int trailerStart = -1;

  ParsedTomlTable(AccumulatingErrorListener errorListener, @Nullable Source source) {
    super(TomlPosition.positionAt(1, 1));
    this.errorListener = errorListener;
    this.source = source;
  }

  @Override
  public List<TomlParseError> errors() {
    return errorListener.errors();
  }

  /**
   * The text this document was parsed from.
   *
   * @return The source, or {@code null} if the parse options kept none.
   */
  @Nullable
  Source source() {
    return source;
  }

  /**
   * Where the blank lines the document ends with start.
   *
   * @return The first offset of the whitespace after the last accepted line, or {@code -1} if there is none.
   */
  int trailerStart() {
    return trailerStart;
  }

  /**
   * Record where the blank lines the document ends with start, once every line has been read. Does nothing when no
   * source was kept.
   */
  void recordTrailer() {
    if (source == null) {
      return;
    }
    int start = source.leadingStart(source.length());
    trailerStart = (start < source.length()) ? start : -1;
  }
}
