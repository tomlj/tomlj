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

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * Options controlling how a TOML document is parsed.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public final class TomlParseOptions {

  /**
   * The default maximum nesting depth: {@value}.
   *
   * @see #withMaxNestingDepth(int)
   */
  public static final int DEFAULT_MAX_NESTING_DEPTH = 128;

  private final TomlVersion version;
  private final int maxNestingDepth;

  private TomlParseOptions(TomlVersion version, int maxNestingDepth) {
    this.version = version;
    this.maxNestingDepth = maxNestingDepth;
  }

  /**
   * The default parse options: {@link TomlVersion#LATEST}, with a maximum nesting depth of
   * {@value #DEFAULT_MAX_NESTING_DEPTH}.
   *
   * @return The default parse options.
   */
  public static TomlParseOptions defaults() {
    return new TomlParseOptions(TomlVersion.LATEST, DEFAULT_MAX_NESTING_DEPTH);
  }

  /**
   * Create a copy of these options that parses at a different specification version.
   *
   * @param version The version level to parse at.
   * @return A new set of options with the given version.
   */
  public TomlParseOptions withVersion(TomlVersion version) {
    requireNonNull(version);
    return new TomlParseOptions(version, maxNestingDepth);
  }

  /**
   * Create a copy of these options that allows a different maximum nesting depth.
   *
   * <p>
   * The depth of a value, table or array is the number of tables and arrays that enclose it, not counting the root
   * table. For example:
   * <ul>
   * <li>In {@code a = [[1]]} the integer {@code 1} is nested 2 deep (the outer and inner arrays).</li>
   * <li>In {@code a.b.c = 1} the integer {@code 1} is nested 2 deep (tables {@code a} and {@code b}).</li>
   * <li>The table named by {@code [a.b]} is nested 1 deep.</li>
   * <li>Each table of {@code [[a.b]]} is nested 2 deep (table {@code a} and array {@code b}).</li>
   * </ul>
   * A document containing a value, table or array nested deeper than this limit is reported as a parse error.
   *
   * <p>
   * The limit keeps bounded the stack depth needed to parse a document and to serialize the result with
   * {@link TomlTable#toJson(JsonOptions...) toJson()} and {@link TomlTable#toToml()}. Raising it lets deeper documents
   * parse, but such documents then need a correspondingly larger thread stack to parse and serialize; without one, they
   * fail with a {@link StackOverflowError}.
   *
   * @param maxNestingDepth The maximum number of tables and arrays, not counting the root table, that may enclose any
   *        value, table or array in the document. Must not be negative; with {@code 0}, every table and array must be
   *        empty and directly in the root table.
   * @return A new set of options with the given maximum nesting depth.
   * @throws IllegalArgumentException If {@code maxNestingDepth} is negative.
   */
  public TomlParseOptions withMaxNestingDepth(int maxNestingDepth) {
    if (maxNestingDepth < 0) {
      throw new IllegalArgumentException("maxNestingDepth must not be negative: " + maxNestingDepth);
    }
    return new TomlParseOptions(version, maxNestingDepth);
  }

  /**
   * The version level to parse at.
   *
   * @return The version level to parse at.
   */
  public TomlVersion version() {
    return version;
  }

  /**
   * The maximum nesting depth allowed in a parsed document.
   *
   * @return The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or
   *         array in the document.
   * @see #withMaxNestingDepth(int)
   */
  public int maxNestingDepth() {
    return maxNestingDepth;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlParseOptions)) {
      return false;
    }
    TomlParseOptions other = (TomlParseOptions) obj;
    return this.version == other.version && this.maxNestingDepth == other.maxNestingDepth;
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, maxNestingDepth);
  }

  @Override
  public String toString() {
    return "TomlParseOptions{version=" + version + ", maxNestingDepth=" + maxNestingDepth + '}';
  }
}
