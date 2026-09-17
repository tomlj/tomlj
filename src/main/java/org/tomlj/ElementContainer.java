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
 * A table and an array are both values (either can be nested inside the other), and both hold the same kinds of thing:
 * entries interleaved with unattached comments. Keeping the sequence in one place keeps the two kinds of container in
 * step as the model grows.
 *
 * <p>
 * Each subclass also keeps its own index over its entries, a map by key for a table and a list by index for an array,
 * for lookup; this class keeps only the order they were written in.
 *
 * @param <E> The kind of entry this container holds: a key/value pair for a table, a value for an array. The type
 *        parameter fixes the rule that a table holds only pairs and an array only values, on top of the unattached
 *        comments every container holds regardless of what it holds.
 */
abstract class ElementContainer<E extends Element.Entry> extends Element.Value {

  private final List<Element> elements = new ArrayList<>();

  /**
   * The elements written in this table or array, in document order.
   *
   * @return The elements, in document order. Unmodifiable.
   */
  List<Element> elements() {
    return Collections.unmodifiableList(elements);
  }

  /**
   * Append an entry to this table or array's sequence. The caller also indexes it, by key or position.
   *
   * @param element The entry.
   */
  void add(E element) {
    elements.add(element);
  }

  /**
   * Add an unattached comment, after the elements already written.
   *
   * @param comment The comment.
   */
  void addComment(TomlComment comment) {
    elements.add(new Element.Comment(comment));
  }

  /**
   * Get the unattached comments in this table or array.
   *
   * @return The unattached comments, in document order. Unmodifiable.
   */
  public List<TomlComment> comments() {
    List<TomlComment> comments = new ArrayList<>();
    for (Element element : elements) {
      if (element instanceof Element.Comment) {
        comments.add(((Element.Comment) element).comment);
      }
    }
    return Collections.unmodifiableList(comments);
  }

  /**
   * A container is its own value: {@link MutableTomlTable} and {@link MutableTomlArray} implement the public
   * {@link TomlTable} / {@link TomlArray} interfaces directly, so the object the public API exposes is the container
   * itself.
   *
   * @return This container.
   */
  @Override
  Object get() {
    return this;
  }
}
