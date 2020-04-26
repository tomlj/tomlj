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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomlj.TomlPosition.positionAt;

import org.junit.jupiter.api.Test;

class MutableTomlArrayTest {

  @Test
  void emptyArrayContainsAllTypes() {
    TomlArray array = new MutableTomlArray();
    assertTrue(array.isEmpty());
    assertEquals(0, array.size());
    assertTrue(array.containsStrings());
    assertTrue(array.containsLongs());
    assertTrue(array.containsDoubles());
    assertTrue(array.containsBooleans());
    assertTrue(array.containsOffsetDateTimes());
    assertTrue(array.containsLocalDateTimes());
    assertTrue(array.containsLocalDates());
    assertTrue(array.containsLocalTimes());
    assertTrue(array.containsArrays());
    assertTrue(array.containsTables());
  }

  @Test
  void arrayContainsTypeAfterAddingItem() {
    MutableTomlArray array = new MutableTomlArray().append("foo", positionAt(2, 3));
    assertFalse(array.isEmpty());
    assertEquals(1, array.size());
    assertTrue(array.containsStrings());
    assertFalse(array.containsLongs());
    assertFalse(array.containsDoubles());
    assertFalse(array.containsBooleans());
    assertFalse(array.containsOffsetDateTimes());
    assertFalse(array.containsLocalDateTimes());
    assertFalse(array.containsLocalDates());
    assertFalse(array.containsLocalTimes());
    assertFalse(array.containsArrays());
    assertFalse(array.containsTables());
  }

  @Test
  void cannotAppendUnsupportedType() {
    MutableTomlArray array = new MutableTomlArray();
    assertThrows(IllegalArgumentException.class, () -> array.append(this, positionAt(1, 1)));
    assertThrows(NullPointerException.class, () -> array.append(null, positionAt(1, 1)));
  }

  @Test
  void cannotAppendDifferentTypes() {
    MutableTomlArray array = new MutableTomlArray();
    array.append("Foo", positionAt(1, 1));
    assertThrows(TomlInvalidTypeException.class, () -> array.append(1L, positionAt(1, 1)));
    array.append("Bar", positionAt(1, 1));
    assertEquals(2, array.size());
  }

  @Test
  void shouldReturnNullForUnknownIndex() {
    MutableTomlArray array = new MutableTomlArray();
    assertThrows(IndexOutOfBoundsException.class, () -> array.get(0));
  }

  @Test
  void shouldReturnInputPosition() {
    MutableTomlArray array = new MutableTomlArray();
    array.append("Foo", positionAt(4, 3));
    array.append("Bar", positionAt(9, 5));
    assertEquals(positionAt(4, 3), array.inputPositionOf(0));
    assertEquals(positionAt(9, 5), array.inputPositionOf(1));
    assertThrows(IndexOutOfBoundsException.class, () -> array.get(2));
  }
}
