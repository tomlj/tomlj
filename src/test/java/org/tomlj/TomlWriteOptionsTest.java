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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TomlWriteOptionsTest {

  @Test
  void defaultsAreNoIndentDefaultMaxLineWidthThePlatformLineSeparatorAndTheLatestVersion() {
    TomlWriteOptions options = TomlWriteOptions.defaults();
    assertEquals(TomlWriteOptions.Keep.LAYOUT, options.keep());
    assertEquals(0, options.indent());
    assertEquals(80, options.maxLineWidth());
    assertEquals(TomlWriteOptions.DEFAULT_MAX_LINE_WIDTH, options.maxLineWidth());
    assertEquals(System.lineSeparator(), options.lineSeparator());
    assertEquals(TomlVersion.LATEST, options.version());
    assertFalse(options.entriesAlignedWithHeaders());
    assertFalse(options.spaceInsideArrays());
    assertTrue(options.blankLineBetweenNestedHeaders());
  }

  @Test
  void keepReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\r\n");
    TomlWriteOptions updated = original.keep(TomlWriteOptions.Keep.NOTHING);

    assertEquals(TomlWriteOptions.Keep.NOTHING, updated.keep());
    assertEquals(2, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlWriteOptions.Keep.LAYOUT, original.keep());
  }

  @Test
  void keepGivesOptionsThatKeepTheAmountAskedFor() {
    for (TomlWriteOptions.Keep keep : TomlWriteOptions.Keep.values()) {
      assertEquals(keep, TomlWriteOptions.defaults().keep(keep).keep());
    }
  }

  @Test
  void keepReplacesTheAmountAskedForEarlier() {
    assertEquals(
        TomlWriteOptions.Keep.NOTATION,
        TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTHING).keep(TomlWriteOptions.Keep.NOTATION).keep());
  }

  @Test
  void keepRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlWriteOptions.defaults().keep(null));
  }

  @Test
  void withIndentReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withMaxLineWidth(100).withLineSeparator("\r\n").withVersion(TomlVersion.V1_0_0);
    TomlWriteOptions updated = original.withIndent(4);

    assertEquals(4, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlVersion.V1_0_0, updated.version());
    assertEquals(0, original.indent());
  }

  @Test
  void withIndentAllowsZero() {
    assertEquals(0, TomlWriteOptions.defaults().withIndent(2).withIndent(0).indent());
  }

  @Test
  void withIndentRejectsANegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> TomlWriteOptions.defaults().withIndent(-1));
  }

  @Test
  void withEntriesAlignedWithHeadersReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withIndent(2).withSpaceInsideArrays(true).withBlankLineBetweenNestedHeaders(false);
    TomlWriteOptions updated = original.withEntriesAlignedWithHeaders(true);

    assertTrue(updated.entriesAlignedWithHeaders());
    assertEquals(2, updated.indent());
    assertTrue(updated.spaceInsideArrays());
    assertFalse(updated.blankLineBetweenNestedHeaders());
    assertFalse(original.entriesAlignedWithHeaders());
    assertFalse(updated.withEntriesAlignedWithHeaders(false).entriesAlignedWithHeaders());
  }

  @Test
  void entryIndentIsOneLevelBeyondTheHeaderUnlessEntriesAreAligned() {
    TomlWriteOptions options = TomlWriteOptions.defaults().withIndent(2);
    assertEquals(0, options.entryIndent(0));
    assertEquals(2, options.entryIndent(1));
    assertEquals(6, options.entryIndent(3));

    TomlWriteOptions aligned = options.withEntriesAlignedWithHeaders(true);
    assertEquals(0, aligned.entryIndent(0));
    assertEquals(0, aligned.entryIndent(1));
    assertEquals(4, aligned.entryIndent(3));
  }

  @Test
  void withSpaceInsideArraysReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original = TomlWriteOptions
        .defaults()
        .withIndent(2)
        .withEntriesAlignedWithHeaders(true)
        .withBlankLineBetweenNestedHeaders(false);
    TomlWriteOptions updated = original.withSpaceInsideArrays(true);

    assertTrue(updated.spaceInsideArrays());
    assertEquals(2, updated.indent());
    assertTrue(updated.entriesAlignedWithHeaders());
    assertFalse(updated.blankLineBetweenNestedHeaders());
    assertFalse(original.spaceInsideArrays());
    assertFalse(updated.withSpaceInsideArrays(false).spaceInsideArrays());
  }

  @Test
  void withBlankLineBetweenNestedHeadersReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withIndent(2).withEntriesAlignedWithHeaders(true).withSpaceInsideArrays(true);
    TomlWriteOptions updated = original.withBlankLineBetweenNestedHeaders(false);

    assertFalse(updated.blankLineBetweenNestedHeaders());
    assertEquals(2, updated.indent());
    assertTrue(updated.entriesAlignedWithHeaders());
    assertTrue(updated.spaceInsideArrays());
    assertTrue(original.blankLineBetweenNestedHeaders());
    assertTrue(updated.withBlankLineBetweenNestedHeaders(true).blankLineBetweenNestedHeaders());
  }

  @Test
  void withLineSeparatorReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original = TomlWriteOptions
        .defaults()
        .withIndent(2)
        .withMaxLineWidth(100)
        .withLineSeparator("\n")
        .withVersion(TomlVersion.V1_0_0);
    TomlWriteOptions updated = original.withLineSeparator("\r\n");

    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(2, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals(TomlVersion.V1_0_0, updated.version());
    assertEquals("\n", original.lineSeparator());
  }

  @Test
  void lineSeparatorIsThePlatformsUntilOneIsAskedFor() {
    assertNull(TomlWriteOptions.defaults().askedLineSeparator());
    assertEquals("\r\n", TomlWriteOptions.defaults().withLineSeparator("\r\n").askedLineSeparator());
  }

  @Test
  void withLineSeparatorRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlWriteOptions.defaults().withLineSeparator(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\r", "\n\n", " ", "\u2028", "\u0085"})
  void withLineSeparatorRejectsAnythingButLfAndCrlf(String separator) {
    assertThrows(IllegalArgumentException.class, () -> TomlWriteOptions.defaults().withLineSeparator(separator));
  }

  @Test
  void withMaxLineWidthReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withIndent(2).withLineSeparator("\r\n").withVersion(TomlVersion.V1_0_0);
    TomlWriteOptions updated = original.withMaxLineWidth(100);

    assertEquals(100, updated.maxLineWidth());
    assertEquals(2, updated.indent());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlVersion.V1_0_0, updated.version());
    assertEquals(TomlWriteOptions.DEFAULT_MAX_LINE_WIDTH, original.maxLineWidth());
  }

  @Test
  void withMaxLineWidthAllowsZeroAndIntegerMaxValue() {
    assertEquals(0, TomlWriteOptions.defaults().withMaxLineWidth(0).maxLineWidth());
    assertEquals(Integer.MAX_VALUE, TomlWriteOptions.defaults().withMaxLineWidth(Integer.MAX_VALUE).maxLineWidth());
  }

  @Test
  void withMaxLineWidthRejectsANegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> TomlWriteOptions.defaults().withMaxLineWidth(-1));
  }

  @Test
  void withVersionReturnsANewInstanceLeavingTheOriginalUnchangedAndKeepsTheOtherOptions() {
    TomlWriteOptions original =
        TomlWriteOptions.defaults().withIndent(2).withMaxLineWidth(100).withLineSeparator("\r\n");
    TomlWriteOptions updated = original.withVersion(TomlVersion.V1_0_0);

    assertEquals(TomlVersion.V1_0_0, updated.version());
    assertEquals(2, updated.indent());
    assertEquals(100, updated.maxLineWidth());
    assertEquals("\r\n", updated.lineSeparator());
    assertEquals(TomlVersion.LATEST, original.version());
  }

  @Test
  void withVersionRejectsNull() {
    assertThrows(NullPointerException.class, () -> TomlWriteOptions.defaults().withVersion(null));
  }

  @Test
  void equalOptionsAreEqualAndHaveTheSameHashCode() {
    TomlWriteOptions a = TomlWriteOptions
        .defaults()
        .withIndent(2)
        .withMaxLineWidth(100)
        .withLineSeparator("\r\n")
        .withVersion(TomlVersion.V1_0_0)
        .withEntriesAlignedWithHeaders(true)
        .withSpaceInsideArrays(true)
        .withBlankLineBetweenNestedHeaders(false);
    TomlWriteOptions b = TomlWriteOptions
        .defaults()
        .withBlankLineBetweenNestedHeaders(false)
        .withSpaceInsideArrays(true)
        .withEntriesAlignedWithHeaders(true)
        .withVersion(TomlVersion.V1_0_0)
        .withLineSeparator("\r\n")
        .withMaxLineWidth(100)
        .withIndent(2);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void optionsDifferingOnlyInIndentAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults().withIndent(2), TomlWriteOptions.defaults().withIndent(3));
  }

  @Test
  void optionsDifferingOnlyInMaxLineWidthAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults().withMaxLineWidth(80), TomlWriteOptions.defaults().withMaxLineWidth(81));
  }

  @Test
  void optionsDifferingOnlyInLineSeparatorAreNotEqual() {
    assertDifferent(
        TomlWriteOptions.defaults().withLineSeparator("\n"),
        TomlWriteOptions.defaults().withLineSeparator("\r\n"));
  }

  @Test
  void optionsDifferingOnlyInVersionAreNotEqual() {
    assertDifferent(
        TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0),
        TomlWriteOptions.defaults().withVersion(TomlVersion.V1_1_0));
  }

  @Test
  void optionsDifferingOnlyInEntryAlignmentAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults(), TomlWriteOptions.defaults().withEntriesAlignedWithHeaders(true));
  }

  @Test
  void optionsDifferingOnlyInArraySpacingAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults(), TomlWriteOptions.defaults().withSpaceInsideArrays(true));
  }

  @Test
  void optionsDifferingOnlyInTheBlankLineBetweenNestedHeadersAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults(), TomlWriteOptions.defaults().withBlankLineBetweenNestedHeaders(false));
  }

  @Test
  void optionsDifferingOnlyInWhatTheyKeepAreNotEqual() {
    assertDifferent(TomlWriteOptions.defaults(), TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTHING));
    assertDifferent(
        TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTATION),
        TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTHING));
  }

  @Test
  void toStringShowsEveryOption() {
    TomlWriteOptions options = TomlWriteOptions
        .defaults()
        .withIndent(2)
        .withMaxLineWidth(100)
        .withLineSeparator("\r\n")
        .withVersion(TomlVersion.V1_0_0)
        .withEntriesAlignedWithHeaders(true)
        .withSpaceInsideArrays(true)
        .withBlankLineBetweenNestedHeaders(false);
    assertEquals(
        "TomlWriteOptions{keep=LAYOUT, indent=2, maxLineWidth=100, lineSeparator=\"\\r\\n\", version=V1_0_0, "
            + "entriesAlignedWithHeaders=true, spaceInsideArrays=true, blankLineBetweenNestedHeaders=false}",
        options.toString());
    assertEquals(
        "TomlWriteOptions{keep=NOTHING, indent=0, maxLineWidth=80, lineSeparator=\"\\n\", version=LATEST, "
            + "entriesAlignedWithHeaders=false, spaceInsideArrays=false, blankLineBetweenNestedHeaders=true}",
        TomlWriteOptions.defaults().keep(TomlWriteOptions.Keep.NOTHING).withLineSeparator("\n").toString());
  }

  private static void assertDifferent(TomlWriteOptions a, TomlWriteOptions b) {
    assertNotEquals(a, b);
    assertNotEquals(a.hashCode(), b.hashCode());
  }
}
