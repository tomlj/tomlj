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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

class ListTomlArrayTest {

  private static LinkedTomlTable parse(String document) {
    return Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
  }

  @Test
  void shouldKeepUnattachedCommentsInPlaceOnADeepCopy() {
    LinkedTomlTable table = parse("a = [\n# first\n\n1,\n\n# second\n]\n");
    ListTomlArray original = (ListTomlArray) table.get("a");

    ListTomlArray copy = original.copy();

    List<TomlElement> elements = copy.elements();
    assertEquals(3, elements.size());
    assertEquals("first", ((TomlComment) elements.get(0)).text());
    assertEquals(1L, ((TomlEntry) elements.get(1)).value().get());
    assertEquals("second", ((TomlComment) elements.get(2)).text());
  }

  @Test
  void shouldKeepTheIsTableArrayFlagOnACopy() {
    LinkedTomlTable table = parse("[[x]]\na = 1\n");
    ListTomlArray original = (ListTomlArray) table.get("x");
    assertTrue(original.isTableArray());

    ListTomlArray copy = original.copy();

    assertTrue(copy.isTableArray());
  }
}
