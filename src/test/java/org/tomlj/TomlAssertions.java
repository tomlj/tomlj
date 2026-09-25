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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assertions comparing the comments of two documents, used to check that writing a document and parsing it again keeps
 * every comment where it was.
 */
final class TomlAssertions {

  private TomlAssertions() {}

  /**
   * Assert that two tables hold the same comments: the same unattached comments, in order, in each table and array, and
   * the same comments attached to the entry under each key.
   *
   * <p>
   * Entries are matched by key rather than by their place in {@link TomlTable#elements()}, since a table writes its
   * values before its sub-tables and so may write them in an order the document did not.
   *
   * @param expected The table the comments are expected to match.
   * @param actual The table to check.
   */
  static void assertSameComments(TomlTable expected, TomlTable actual) {
    assertSameComments(expected, actual, "");
  }

  /**
   * Assert that two arrays hold the same comments, as {@link #assertSameComments(TomlTable, TomlTable)} does for
   * tables.
   *
   * @param expected The array the comments are expected to match.
   * @param actual The array to check.
   */
  static void assertSameComments(TomlArray expected, TomlArray actual) {
    assertSameComments(expected, actual, "");
  }

  private static void assertSameComments(TomlTable expected, TomlTable actual, String path) {
    assertSameUnattached(expected.elements(), actual.elements(), path);
    assertEquals(expected.keySet(), actual.keySet(), () -> "keys of " + describe(path));
    for (TomlElement element : expected.elements()) {
      if (!(element instanceof TomlKeyValue pair)) {
        continue;
      }
      String key = pair.key();
      TomlKeyValue other = actual.entry(Collections.singletonList(key));
      assertNotNull(other, () -> "missing entry " + describe(join(path, key)));
      assertSameComments(pair.comments(), other.comments(), join(path, key));
      assertSameValue(pair.value().get(), other.value().get(), join(path, key));
    }
  }

  private static void assertSameComments(TomlArray expected, TomlArray actual, String path) {
    assertSameUnattached(expected.elements(), actual.elements(), path);
    assertEquals(expected.size(), actual.size(), () -> "size of " + describe(path));
    for (int i = 0; i < expected.size(); i++) {
      String elementPath = path + "[" + i + "]";
      assertSameComments(expected.entry(i).comments(), actual.entry(i).comments(), elementPath);
      assertSameValue(expected.get(i), actual.get(i), elementPath);
    }
  }

  private static void assertSameValue(Object expected, Object actual, String path) {
    if (expected instanceof TomlTable expectedTable && actual instanceof TomlTable actualTable) {
      assertSameComments(expectedTable, actualTable, path);
    } else if (expected instanceof TomlArray expectedArray && actual instanceof TomlArray actualArray) {
      assertSameComments(expectedArray, actualArray, path);
    } else if (expected instanceof TomlTable || expected instanceof TomlArray) {
      assertEquals(expected.getClass().getSimpleName(), actual.getClass().getSimpleName(), () -> describe(path));
    } else {
      assertEquals(expected, actual, () -> describe(path));
    }
  }

  private static void assertSameUnattached(List<TomlElement> expected, List<TomlElement> actual, String path) {
    assertEquals(unattached(expected), unattached(actual), () -> "unattached comments of " + describe(path));
  }

  private static void assertSameComments(List<TomlComment> expected, List<TomlComment> actual, String path) {
    assertEquals(describe(expected), describe(actual), () -> "comments of " + describe(path));
  }

  private static List<String> unattached(List<TomlElement> elements) {
    List<String> comments = new ArrayList<>();
    for (TomlElement element : elements) {
      if (element instanceof TomlComment comment) {
        comments.add(comment.lines().toString());
      }
    }
    return comments;
  }

  private static List<String> describe(List<TomlComment> comments) {
    List<String> described = new ArrayList<>(comments.size());
    for (TomlComment comment : comments) {
      described.add(comment.placement() + " " + comment.lines());
    }
    return described;
  }

  private static String join(String path, String key) {
    return path.isEmpty() ? key : (path + "." + key);
  }

  private static String describe(String path) {
    return path.isEmpty() ? "the root table" : ("\"" + path + "\"");
  }
}
