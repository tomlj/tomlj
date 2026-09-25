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

/**
 * Where a value was written in the text its document was parsed from: the literal of a scalar, or the brackets of an
 * array or inline table.
 *
 * <p>
 * Every offset is an inclusive code-point offset into {@link #source}. A span is immutable, and names the source it
 * reads, so a span copied into another document still resolves.
 */
final class ValueSpan {

  /** The text this span's offsets address. */
  final Source source;

  /** The first offset of the literal, or the opening {@code [} or <code>{</code>. */
  final int start;

  /** The last offset of the literal, or the closing {@code ]} or <code>}</code>. */
  final int stop;

  /**
   * The first offset of the whitespace between the last element and the closing bracket, which equals {@link #stop}
   * when there is none and {@code start + 1} for a container written with no element at all. {@code -1} for a scalar.
   */
  final int trailerStart;

  /**
   * The literal of a scalar.
   *
   * @param source The text the offsets address.
   * @param start The first offset of the literal.
   * @param stop The last offset of the literal.
   * @return The span.
   */
  static ValueSpan scalar(Source source, int start, int stop) {
    return new ValueSpan(source, start, stop, -1);
  }

  /**
   * The brackets of an array or inline table written in the document.
   *
   * @param source The text the offsets address.
   * @param start The offset of the opening bracket.
   * @param stop The offset of the closing bracket.
   * @param trailerStart The first offset of the whitespace before the closing bracket.
   * @return The span.
   */
  static ValueSpan brackets(Source source, int start, int stop, int trailerStart) {
    return new ValueSpan(source, start, stop, trailerStart);
  }

  private ValueSpan(Source source, int start, int stop, int trailerStart) {
    this.source = source;
    this.start = start;
    this.stop = stop;
    this.trailerStart = trailerStart;
  }

  @Override
  public String toString() {
    return "ValueSpan{[" + start + ", " + stop + "] trailer=" + trailerStart + '}';
  }
}
