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
import java.util.NoSuchElementException;

/**
 * Helpers for the default methods of {@link MutableTomlTable}, which cannot have private methods of their own in Java
 * 8.
 */
final class MutableTomlTables {

  private MutableTomlTables() {}

  /**
   * Get the entry for a key, or throw if the key is not set.
   *
   * @param table The table to look the key up in.
   * @param path The key path.
   * @return The entry.
   * @throws IllegalArgumentException If {@code path} is empty.
   * @throws NoSuchElementException If the key is not set.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key is not a table.
   */
  static MutableTomlKeyValue entryOrThrow(MutableTomlTable table, List<String> path) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    MutableTomlKeyValue entry = table.entry(path);
    if (entry == null) {
      throw new NoSuchElementException(Toml.joinKeyPath(path) + " is not set");
    }
    return entry;
  }
}
