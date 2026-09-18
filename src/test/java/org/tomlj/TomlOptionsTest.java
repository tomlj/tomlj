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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TomlOptionsTest {

  @Test
  void defaultsAreNoIndentDefaultMaxLineWidthAndThePlatformLineSeparator() {
    TomlOptions options = TomlOptions.defaults();
    assertEquals(TomlOptions.Style.PRESERVE, options.style());
    assertEquals(0, options.indent());
    assertEquals(80, options.maxLineWidth());
    assertEquals(TomlOptions.DEFAULT_MAX_LINE_WIDTH, options.maxLineWidth());
    assertEquals(System.lineSeparator(), options.lineSeparator());
  }

  @Test
  void withStyleReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlOptions original = TomlOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\r\n");
    TomlOptions updated = original.withStyle(TomlOptions.Style.CANONICAL);

    assertEquals(TomlOptions.Style.CANONICAL, updated.style());
    assertEquals(2, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlOptions.Style.PRESERVE, original.style());
  }

  @Test
  void canonicalWritesInTheDefaultStyle() {
    assertEquals(TomlOptions.Style.CANONICAL, TomlOptions.defaults().canonical().style());
  }

  @Test
  void withStyleRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlOptions.defaults().withStyle(null));
  }

  @Test
  void withIndentReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlOptions original = TomlOptions.defaults().withMaxLineWidth(100).withLineSeparator("\r\n");
    TomlOptions updated = original.withIndent(4);

    assertEquals(4, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(0, original.indent());
  }

  @Test
  void withIndentAllowsZero() {
    assertEquals(0, TomlOptions.defaults().withIndent(2).withIndent(0).indent());
  }

  @Test
  void withIndentRejectsANegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> TomlOptions.defaults().withIndent(-1));
  }

  @Test
  void withLineSeparatorReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlOptions original = TomlOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\n");
    TomlOptions updated = original.withLineSeparator("\r\n");

    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(2, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\n", original.lineSeparator());
  }

  @Test
  void lineSeparatorIsThePlatformsUntilOneIsAskedFor() {
    assertNull(TomlOptions.defaults().askedLineSeparator());
    assertEquals("\r\n", TomlOptions.defaults().withLineSeparator("\r\n").askedLineSeparator());
  }

  @Test
  void withLineSeparatorRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlOptions.defaults().withLineSeparator(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\r", "\n\n", " ", "\u2028", "\u0085"})
  void withLineSeparatorRejectsAnythingButLfAndCrlf(String separator) {
    assertThrows(IllegalArgumentException.class, () -> TomlOptions.defaults().withLineSeparator(separator));
  }

  @Test
  void withMaxLineWidthReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlOptions original = TomlOptions.defaults().withIndent(2).withLineSeparator("\r\n");
    TomlOptions updated = original.withMaxLineWidth(100);

    assertEquals(100, updated.maxLineWidth());
    assertEquals(2, updated.indent());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlOptions.DEFAULT_MAX_LINE_WIDTH, original.maxLineWidth());
  }

  @Test
  void withMaxLineWidthAllowsZeroAndIntegerMaxValue() {
    assertEquals(0, TomlOptions.defaults().withMaxLineWidth(0).maxLineWidth());
    assertEquals(Integer.MAX_VALUE, TomlOptions.defaults().withMaxLineWidth(Integer.MAX_VALUE).maxLineWidth());
  }

  @Test
  void withMaxLineWidthRejectsANegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> TomlOptions.defaults().withMaxLineWidth(-1));
  }

  @Test
  void equalOptionsAreEqualAndHaveTheSameHashCode() {
    TomlOptions a = TomlOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\r\n");
    TomlOptions b = TomlOptions.defaults().withLineSeparator("\r\n").withMaxLineWidth(100).withIndent(2);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void optionsDifferingOnlyInIndentAreNotEqual() {
    assertDifferent(TomlOptions.defaults().withIndent(2), TomlOptions.defaults().withIndent(3));
  }

  @Test
  void optionsDifferingOnlyInMaxLineWidthAreNotEqual() {
    assertDifferent(TomlOptions.defaults().withMaxLineWidth(80), TomlOptions.defaults().withMaxLineWidth(81));
  }

  @Test
  void optionsDifferingOnlyInLineSeparatorAreNotEqual() {
    assertDifferent(TomlOptions.defaults().withLineSeparator("\n"), TomlOptions.defaults().withLineSeparator("\r\n"));
  }

  @Test
  void optionsDifferingOnlyInStyleAreNotEqual() {
    assertDifferent(TomlOptions.defaults(), TomlOptions.defaults().canonical());
  }

  @Test
  void toStringShowsEveryOption() {
    TomlOptions options = TomlOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\r\n");
    assertEquals(
        "TomlOptions{style=PRESERVE, indent=2, maxLineWidth=100, lineSeparator=\"\\r\\n\"}",
        options.toString());
    assertEquals(
        "TomlOptions{style=CANONICAL, indent=0, maxLineWidth=80, lineSeparator=\"\\n\"}",
        TomlOptions.defaults().canonical().withLineSeparator("\n").toString());
  }

  private static void assertDifferent(TomlOptions a, TomlOptions b) {
    assertNotEquals(a, b);
    assertNotEquals(a.hashCode(), b.hashCode());
  }
}
