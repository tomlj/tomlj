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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

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
 * <p>
 * The editing API tracks {@link #sequenceModified} here: whether an entry or an unattached comment was removed from
 * this container's sequence, or an unattached comment was added to it. An entry added or replaced records that on
 * itself, in {@link Entry#valueModified}, since it still exists afterwards to carry the flag.
 *
 * @param <E> The kind of entry this container holds: a key/value pair for a table, an indexed value for an array.
 */
abstract class ElementContainer<E extends Entry> extends Value {

  private final List<TomlElement> elements = new ArrayList<>();

  private boolean sequenceModified;

  // How much of the existing structure and format of this table or array is kept, regardless of what the options ask
  // for the enclosing document. LAYOUT until reformat() sets less, and never set back to more. A copy keeps it, since
  // it applies wherever the table or array is stored.
  TomlWriteOptions.Keep keep = TomlWriteOptions.Keep.LAYOUT;

  // Where the brackets of this table or array were written in the document, and where the whitespace before the closing
  // one starts. Null for a table or array built through the editing API, for one read from a document parsed with no
  // source kept, and for the tables of a [[x]] header and the array holding them, which are written as sections rather
  // than in brackets. A copy shares it, since the span is offsets into the source the brackets were read from, and a
  // copy is still written from that text.
  @Nullable
  ValueSpan bracketSpan;

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
   * Add an unattached comment read from the document, after the elements already written.
   *
   * @param comment The comment.
   */
  void addParsedComment(TomlComment comment) {
    elements.add(comment);
  }

  /**
   * Add an unattached comment through the editing API, after the elements already written, and record the addition.
   *
   * @param comment The comment.
   */
  void addEditedComment(TomlComment comment) {
    elements.add(comment);
    sequenceModified = true;
  }

  /**
   * Insert an entry into this table or array's sequence at a position. The caller also indexes the entry, by key or
   * position.
   *
   * @param index The index to insert at.
   * @param entry The entry.
   */
  void insert(int index, E entry) {
    elements.add(index, entry);
  }

  /**
   * Insert an unattached comment through the editing API at a position in this container's sequence, and record the
   * addition.
   *
   * @param index The index to insert at.
   * @param comment The comment.
   */
  void insertEditedComment(int index, TomlComment comment) {
    elements.add(index, comment);
    sequenceModified = true;
  }

  /**
   * The index of an element in this container's sequence, by identity.
   *
   * @param element The element to find.
   * @return The index of {@code element} in {@link #elements()}, or {@code -1} if it is not among them.
   */
  @SuppressWarnings("ReferenceEquality") // a sequence search is about identity, never equals
  int indexOfElement(TomlElement element) {
    for (int i = 0; i < elements.size(); i++) {
      if (elements.get(i) == element) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Remove an element from this container's sequence, by identity, and record the removal if it was found. Used for an
   * entry, once the caller has removed it from its own index, and for an unattached comment.
   *
   * @param element The element to remove.
   * @return {@code true} if the element was found, and removed.
   */
  boolean removeElement(TomlElement element) {
    int index = indexOfElement(element);
    if (index < 0) {
      return false;
    }
    elements.remove(index);
    sequenceModified = true;
    return true;
  }

  /**
   * Remove every entry from this container's sequence, leaving the unattached comments where they are. Used by
   * {@code clear()}; the caller clears its own index separately.
   *
   * @return {@code true} if an entry was removed.
   */
  boolean removeAllEntries() {
    boolean removedAny = false;
    Iterator<TomlElement> iterator = elements.iterator();
    while (iterator.hasNext()) {
      TomlElement element = iterator.next();
      if (element instanceof Entry) {
        iterator.remove();
        removedAny = true;
      }
    }
    if (removedAny) {
      sequenceModified = true;
    }
    return removedAny;
  }

  /**
   * Whether this table or array was changed through the editing API, either directly or by a change nested within one
   * of its entries.
   *
   * @return {@code true} if this table or array was changed through the editing API.
   */
  public boolean isModified() {
    if (sequenceModified) {
      return true;
    }
    for (TomlElement element : elements) {
      if (element instanceof Entry && ((Entry) element).isModified()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Set how much of the existing structure and format of this table or array is kept; see
   * {@link MutableTomlTable#reformat(TomlWriteOptions.Keep)}.
   *
   * @param keep How much to keep.
   */
  void reformatAs(TomlWriteOptions.Keep keep) {
    requireNonNull(keep);
    // A table reformatted twice keeps the lesser of the two amounts: the later of them in the order LAYOUT, NOTATION,
    // NOTHING. So LAYOUT, the amount every table starts with, changes nothing
    if (keep.compareTo(this.keep) > 0) {
      this.keep = keep;
    }
  }

  /**
   * How much of the existing structure and format of this table or array is kept within a document that keeps
   * {@code inherited}: the later of that and the amount set here, in the order LAYOUT, NOTATION, NOTHING, each of which
   * keeps less than the one before it.
   *
   * @param inherited How much the document, or the table or array holding this one, keeps.
   * @return How much this table or array keeps.
   */
  TomlWriteOptions.Keep keepWithin(TomlWriteOptions.Keep inherited) {
    return (keep.compareTo(inherited) > 0) ? keep : inherited;
  }

  /**
   * The options a value nested in a document is written with: the options of the table or array holding it, keeping no
   * more than reformat() set on the value itself.
   *
   * @param value The value.
   * @param enclosing The options the table or array holding it is written with.
   * @return Those options, or a copy of them that keeps less.
   */
  static TomlWriteOptions optionsWithin(Object value, TomlWriteOptions enclosing) {
    if (!(value instanceof ElementContainer)) {
      return enclosing;
    }
    TomlWriteOptions.Keep keep = ((ElementContainer<?>) value).keepWithin(enclosing.keep());
    return (keep == enclosing.keep()) ? enclosing : enclosing.keep(keep);
  }

  /**
   * Whether reformat() was called on this table or array, or on one written inside it, in which case the span of its
   * brackets is not used.
   *
   * @return {@code true} if reformat() was called here or anywhere within.
   */
  private boolean reformatted() {
    if (keep != TomlWriteOptions.Keep.LAYOUT) {
      return true;
    }
    for (TomlElement element : elements) {
      if (!(element instanceof Entry)) {
        continue;
      }
      Value value = ((Entry) element).value;
      if (value instanceof ElementContainer && ((ElementContainer<?>) value).reformatted()) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * The span of the brackets this table or array was written in covers what it held when it was read, so it is not used
   * once anything in it has been edited, or once reformat() has been called on it or on a table or array inside it.
   */
  @Override
  @Nullable
  ValueSpan writtenSpan() {
    return (isModified() || reformatted()) ? null : bracketSpan;
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
