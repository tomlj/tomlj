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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Something written in a table or array: an entry, or an unattached comment.
 *
 * <p>
 * {@link TomlTable#elements()} and {@link TomlArray#elements()} list the elements of a table or array in document
 * order. Each is a {@link TomlKeyValue} in a table, a {@link TomlValue} in an array, or an unattached
 * {@link TomlComment}.
 */
public interface TomlElement {

  /**
   * Where this element was written in the document.
   *
   * @return The position, or {@code null} if this element was not read from a document, or is a table that a dotted key
   *         created before any header defined it.
   */
  @Nullable
  TomlPosition position();
}
