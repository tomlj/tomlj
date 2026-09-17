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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The storage shared by a parsed table and a parsed array: the sequence of elements written in it, in document order.
 *
 * <p>
 * A table and an array both hold entries interleaved with unattached comments, and either can be nested in the other,
 * so both are values.
 *
 * <p>
 * Each subclass also keeps its own index over its entries, a map by key for a table and a list by index for an array;
 * this class keeps only the order they were written in.
 *
 * @param <E> The kind of entry this container holds: a key/value pair for a table, an indexed value for an array.
 */
abstract class ElementContainer<E extends Entry> extends Value {

  private final List<TomlElement> elements = new ArrayList<>();

  /**
   * The elements written in this table or array, in document order.
   *
   * @return The elements, in document order. Unmodifiable.
   */
  public List<TomlElement> elements() {
    return Collections.unmodifiableList(elements);
  }

  /**
   * Append an entry to this table or array's sequence. The caller also indexes it, by key or position.
   *
   * @param entry The entry.
   */
  void add(E entry) {
    elements.add(entry);
  }

  /**
   * Add an unattached comment, after the elements already written.
   *
   * @param comment The comment.
   */
  void addComment(TomlComment comment) {
    elements.add(comment);
  }

  /**
   * A container is its own value: {@link LinkedTomlTable} and {@link ListTomlArray} implement the public
   * {@link TomlTable} / {@link TomlArray} interfaces directly, so the object the public API exposes is the container
   * itself.
   *
   * @return This container.
   */
  @Override
  public Object get() {
    return this;
  }
}
