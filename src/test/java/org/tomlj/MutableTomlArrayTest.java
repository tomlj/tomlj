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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MutableTomlArray}, through the public API only.
 *
 * <p>
 * A parsed document is obtained by parsing a table directly, with {@link #parse(String)}: a {@link LinkedTomlTable} is
 * a {@link MutableTomlTable}, and the {@link ListTomlArray} values inside it are {@link MutableTomlArray}s.
 */
class MutableTomlArrayTest {

  private static LinkedTomlTable parse(String document) {
    return Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
  }

  // Unattached comments held directly in an array's elements(), filtered out from the entries.
  private static List<TomlComment> unattachedComments(TomlArray array) {
    List<TomlComment> comments = new ArrayList<>();
    for (TomlElement element : array.elements()) {
      if (element instanceof TomlComment comment) {
        comments.add(comment);
      }
    }
    return comments;
  }

  // A minimal, genuinely foreign TomlArray: not one of our own, so a value stored through it must be deep-copied
  // rather than shared.
  private static final class ForeignArray implements TomlArray {
    private final List<Object> values;

    ForeignArray(List<Object> values) {
      this.values = values;
    }

    @Override
    public int size() {
      return values.size();
    }

    @Override
    public boolean isEmpty() {
      return values.isEmpty();
    }

    @Override
    public TomlEntry entry(int index) {
      return new ForeignEntry(values.get(index));
    }

    @Override
    public List<TomlElement> elements() {
      List<TomlElement> elements = new ArrayList<>();
      for (Object value : values) {
        elements.add(new ForeignEntry(value));
      }
      return elements;
    }

    @Override
    public List<Object> toList() {
      return values;
    }
  }

  // A minimal TomlEntry for ForeignArray, with no position and no comments.
  private static final class ForeignEntry implements TomlEntry {
    private final Object value;

    ForeignEntry(Object value) {
      this.value = value;
    }

    @Override
    public TomlValue value() {
      return new ForeignValue(value);
    }

    @Override
    public List<TomlComment> comments() {
      return List.of();
    }

    @Override
    public TomlPosition position() {
      return null;
    }
  }

  // A minimal TomlValue for ForeignEntry#value(), with no position.
  private static final class ForeignValue implements TomlValue {
    private final Object value;

    ForeignValue(Object value) {
      this.value = value;
    }

    @Override
    public Object get() {
      return value;
    }

    @Override
    public TomlPosition position() {
      return null;
    }
  }

  @Test
  void shouldCreateAnEmptyUnmodifiedArray() {
    MutableTomlArray array = MutableTomlArray.create();
    assertTrue(array.isEmpty());
    assertFalse(array.isModified());
  }

  @Test
  void shouldCreateFromValues() {
    MutableTomlArray array = MutableTomlArray.of(1, "two", 3.0);
    assertEquals(List.of(1L, "two", 3.0), array.toList());
    assertTrue(array.isModified());
  }

  @Test
  void shouldCreateFromAnIterable() {
    Set<Integer> values = new LinkedHashSet<>(List.of(1, 2, 3));
    MutableTomlArray array = MutableTomlArray.copyOf(values);
    assertEquals(List.of(1L, 2L, 3L), array.toList());
  }

  @Test
  void shouldAppendAValue() {
    MutableTomlArray array = MutableTomlArray.create();
    array.add(1L);
    array.add(2L);
    assertEquals(List.of(1L, 2L), array.toList());
  }

  @Test
  void shouldReplaceInPlace() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L);
    array.set(0, 99L);
    assertEquals(List.of(99L, 2L), array.toList());
  }

  @Test
  void shouldRemoveAnElement() {
    MutableTomlArray array = MutableTomlArray.of(1L, 2L, 3L);
    Object removed = array.remove(1);
    assertEquals(2L, removed);
    assertEquals(List.of(1L, 3L), array.toList());
    assertTrue(array.isModified());
  }

  @Test
  void shouldRejectOutOfBoundsIndexOperations() {
    MutableTomlArray array = MutableTomlArray.create();
    assertThrows(IndexOutOfBoundsException.class, () -> array.get(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.set(0, 1L));
    assertThrows(IndexOutOfBoundsException.class, () -> array.remove(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.isModified(0));
  }

  @Test
  void shouldRejectNullAndUnsupportedValues() {
    MutableTomlArray array = MutableTomlArray.create();
    assertThrows(NullPointerException.class, () -> array.add(null));
    assertThrows(IllegalArgumentException.class, () -> array.add(new Object()));
  }

  @Test
  void shouldClearAnArrayLeavingUnattachedComments() {
    LinkedTomlTable table = parse("a = [\n1,\n\n# footer\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.clear();

    assertTrue(array.isEmpty());
    assertEquals(1, unattachedComments(array).size());
    assertTrue(array.isModified());
  }

  @Test
  void shouldNotReportClearingAnEmptyArrayAsAModification() {
    MutableTomlArray array = MutableTomlArray.create();
    array.clear();
    assertFalse(array.isModified());
  }

  @Test
  void shouldStoreAnArrayAsADeepCopy() {
    LinkedTomlTable table = parse("a = [1]\n");
    ListTomlArray a = (ListTomlArray) table.get("a");
    MutableTomlArray root = MutableTomlArray.create();

    root.add(a);

    MutableTomlArray stored = root.getArray(0);
    assertNotSame(a, stored);
    assertNull(stored.inputPositionOf(0));

    // A later change to a is not seen through root.
    a.set(0, 2L);
    assertEquals(1L, root.getArray(0).get(0));

    // Editing through the stored copy is seen through root.
    stored.set(0, 3L);
    assertEquals(3L, root.getArray(0).get(0));
  }

  @Test
  void shouldStoreAnArrayIntoItself() {
    MutableTomlArray root = MutableTomlArray.create();
    root.add(1L);
    MutableTomlArray before = MutableTomlArray.copyOf(root);

    root.add(root);

    MutableTomlArray stored = root.getArray(1);
    assertNotSame(root, stored);
    assertTrue(Toml.equals(before, stored));
  }

  @Test
  void shouldDeepCopyAForeignArray() {
    TomlArray foreign = new ForeignArray(List.of(1L, 2L));
    MutableTomlArray array = MutableTomlArray.create();

    array.add(foreign);

    TomlArray stored = (TomlArray) array.get(0);
    assertNotSame(foreign, stored);
    assertEquals(List.of(1L, 2L), stored.toList());
  }

  @Test
  void shouldTrackModificationOnAParsedArray() {
    LinkedTomlTable table = parse("a = [1, 2]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    assertFalse(array.isModified());
    assertFalse(array.isModified(0));

    array.set(0, 99L);

    assertTrue(array.isModified());
    assertTrue(array.isModified(0));
    assertFalse(array.isModified(1));
  }

  @Test
  void shouldKeepPlaceAndCommentsWhenReplacingButHaveNoPosition() {
    LinkedTomlTable table = parse("a = [\n1 # note\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");
    List<TomlComment> comments = array.comments(0);
    TomlElement beforeReplace = array.elements().get(0);

    array.set(0, 42L);

    // An array entry's position is its value's, so the replacement, a value with no position of its own, leaves
    // inputPositionOf(0) null, even though the entry itself, with its place and its comments, is unchanged.
    assertNull(array.inputPositionOf(0));
    assertEquals(comments, array.comments(0));
    List<TomlElement> elements = array.elements();
    assertEquals(1, elements.size());
    assertSame(beforeReplace, elements.get(0));
    assertEquals(42L, ((TomlEntry) elements.get(0)).value().get());
  }

  @Test
  void shouldCopyAParsedArrayDroppingPositionsAndMarkingEveryEntryModified() {
    LinkedTomlTable table = parse("a = [\n1, # note\n2\n]\n");
    ListTomlArray original = (ListTomlArray) table.get("a");

    MutableTomlArray copy = MutableTomlArray.copyOf(original);

    assertTrue(Toml.equals(original, copy));
    assertEquals(original.comments(0), copy.comments(0));
    assertNull(copy.inputPositionOf(0));
    assertNull(copy.inputPositionOf(1));
    assertTrue(copy.isModified(0));
    assertTrue(copy.isModified(1));
    assertTrue(copy.isModified());
    assertFalse(original.isModified());

    copy.set(0, 99L);
    assertEquals(1L, original.get(0));
  }

  @Test
  void shouldReportFalseIsModifiedForAnEmptyCopy() {
    MutableTomlArray copy = MutableTomlArray.copyOf(MutableTomlArray.create());
    assertFalse(copy.isModified());
  }

  @Test
  void shouldNavigateThroughMutableViews() {
    LinkedTomlTable table = parse("a = [ {x = 1} ]\n");
    MutableTomlArray array = (MutableTomlArray) table.get("a");

    array.getTable(0).set("x", 99L);

    assertEquals(99L, table.getArray("a").getTable(0).get("x"));
  }

  @Test
  void shouldShowTheNewEntryAtTheEndOfElementsAfterAdd() {
    MutableTomlArray array = MutableTomlArray.create();
    array.add(1L);
    array.add(2L);

    List<TomlElement> elements = array.elements();
    assertEquals(2, elements.size());
    assertEquals(1L, ((TomlEntry) elements.get(0)).value().get());
    assertEquals(2L, ((TomlEntry) elements.get(1)).value().get());
  }

  @Test
  void shouldRemoveTheEntryFromElementsButKeepUnattachedCommentsInPlaceAfterRemove() {
    LinkedTomlTable table = parse("a = [\n# first\n\n1,\n\n# second\n]\n");
    ListTomlArray array = (ListTomlArray) table.get("a");

    array.remove(0);

    List<TomlElement> elements = array.elements();
    assertEquals(2, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals("second", ((TomlComment) elements.get(1)).text());
  }
}
