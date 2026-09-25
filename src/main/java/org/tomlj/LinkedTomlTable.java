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
import static org.tomlj.Parser.parseDottedKey;
import static org.tomlj.TomlType.typeFor;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

class LinkedTomlTable extends ElementContainer<Entry.KeyValue> implements MutableTomlTable {

  private final Map<String, Entry.KeyValue> properties = new LinkedHashMap<>();

  // Not final: a table created implicitly by a dotted key or by a leading key of a header such as [a.b] has no
  // position until a header defines this table itself, at which point it takes the header's position; see #define. The
  // root table is defined from the start of the document, and an inline table from the moment it is created, at the
  // position of its opening '{'.
  private @Nullable TomlPosition position;

  private final boolean inline;

  // Whether the inline form was asked for through createInline(), which a writer keeping the notation honours as it
  // honours the braces a document wrote; see hasInlineForm().
  private boolean inlineRequested;

  // Where the [a.b] header that defined this table, or the [[a.b]] header that opened this element table, was written
  // in the document. Null for a table no header names, for one built through the editing API, and for a copy: a copy is
  // not in a document, so it has no header.
  @Nullable
  SourceSpan headerSpan;

  LinkedTomlTable(@Nullable TomlPosition position) {
    this(position, false);
  }

  LinkedTomlTable() {
    this(null, false);
  }

  private LinkedTomlTable(@Nullable TomlPosition position, boolean inline) {
    this.position = position;
    this.inline = inline;
  }

  /**
   * Create a table for an inline table in a document.
   *
   * <p>
   * Inline tables are self-contained: once closed, no table header or dotted key may add to them.
   *
   * @param position The position of the inline table.
   * @return A new, defined table.
   */
  static LinkedTomlTable inline(TomlPosition position) {
    return new LinkedTomlTable(position, true);
  }

  /**
   * Create an empty inline table through the editing API; see {@link MutableTomlTable#createInline()}.
   *
   * @return A new, empty inline table.
   */
  static LinkedTomlTable createInline() {
    LinkedTomlTable table = new LinkedTomlTable(null, true);
    table.inlineRequested = true;
    return table;
  }

  boolean isDefined() {
    return position != null;
  }

  /**
   * Whether this table is written with braces, on the line of the entry holding it, rather than under a header of its
   * own.
   *
   * @return {@code true} if this table is an inline table.
   */
  boolean isInline() {
    return inline;
  }

  /**
   * Whether the inline form of this table is written where the notation is kept: it was read between braces from a
   * document whose source was kept, or asked for through {@link MutableTomlTable#createInline()}. A table read from a
   * document parsed with no source is inline, but records nothing of how it was written.
   *
   * @return {@code true} if a writer keeping the notation writes this table with braces.
   */
  boolean hasInlineForm() {
    return bracketSpan != null || inlineRequested;
  }

  /**
   * Define this table at a header's position, once {@code [a]} or {@code [[a]]} is reached for it.
   *
   * @param position The header's position.
   */
  void define(TomlPosition position) {
    this.position = position;
  }

  @Override
  @Nullable
  public TomlPosition position() {
    return position;
  }

  @Override
  public int size() {
    return properties.size();
  }

  @Override
  public boolean isEmpty() {
    return properties.isEmpty();
  }

  @Override
  public Set<String> keySet() {
    // Unmodifiable: removing a key through this set would desync it from the sequence elements() also holds.
    return Collections.unmodifiableSet(properties.keySet());
  }

  @Override
  public Set<List<String>> keyPathSet(boolean includeTables) {
    return properties.entrySet().stream().flatMap(property -> {
      String key = property.getKey();
      List<String> basePath = Collections.singletonList(key);

      Object value = property.getValue().value().get();
      if (!(value instanceof TomlTable)) {
        return Stream.of(basePath);
      }

      Stream<List<String>> subKeys = ((TomlTable) value).keyPathSet(includeTables).stream().map(subPath -> {
        List<String> path = new ArrayList<>(subPath.size() + 1);
        path.add(key);
        path.addAll(subPath);
        return path;
      });

      if (includeTables) {
        return Stream.concat(Stream.of(basePath), subKeys);
      } else {
        return subKeys;
      }
    }).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Set<Map.Entry<String, Object>> entrySet() {
    return properties
        .entrySet()
        .stream()
        .map(property -> new AbstractMap.SimpleEntry<>(property.getKey(), property.getValue().value().get()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
    return properties.entrySet().stream().flatMap(property -> {
      String key = property.getKey();
      List<String> entryPath = Collections.singletonList(key);
      Object value = property.getValue().value().get();

      if (!(value instanceof TomlTable)) {
        return Stream.of(new AbstractMap.SimpleEntry<>(entryPath, value));
      }

      Stream<Map.Entry<List<String>, Object>> subEntries =
          ((TomlTable) value).entryPathSet(includeTables).stream().map(subEntry -> {
            List<String> subPath = subEntry.getKey();
            List<String> path = new ArrayList<>(subPath.size() + 1);
            path.add(key);
            path.addAll(subPath);
            return new AbstractMap.SimpleEntry<>(path, subEntry.getValue());
          });

      if (includeTables) {
        return Stream.concat(Stream.of(new AbstractMap.SimpleEntry<>(entryPath, value)), subEntries);
      } else {
        return subEntries;
      }
    }).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  @Nullable
  public TomlPosition inputPositionOf(List<String> path) {
    if (path.isEmpty()) {
      return position;
    }
    Entry.KeyValue entry = entry(path);
    return (entry != null) ? entry.position() : null;
  }

  @Override
  public Entry.@Nullable KeyValue entry(List<String> path) {
    if (path.isEmpty()) {
      return null;
    }
    LinkedTomlTable table = parentTable(path);
    return (table != null) ? table.properties.get(path.get(path.size() - 1)) : null;
  }

  /**
   * Walk to the table that holds the last key of a path.
   *
   * @param path A non-empty key path.
   * @return The table the last key is looked up in, or {@code null} if a table before it is missing.
   * @throws TomlInvalidTypeException If an element of the path preceding the final key exists and is not a table.
   */
  @Nullable
  private LinkedTomlTable parentTable(List<String> path) {
    LinkedTomlTable table = this;
    for (int i = 0; i < (path.size() - 1); ++i) {
      Entry.KeyValue entry = table.properties.get(path.get(i));
      if (entry == null) {
        return null;
      }
      if (!(entry.value instanceof LinkedTomlTable)) {
        String badPath = Toml.joinKeyPath(path.subList(0, i + 1));
        throw new TomlInvalidTypeException(
            "Value of '" + badPath + "' is a " + TomlType.typeNameFor(entry.value.get()));
      }
      table = (LinkedTomlTable) entry.value;
    }
    return table;
  }

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    properties.forEach((key, entry) -> map.put(key, entry.value().get()));
    return map;
  }

  /**
   * Append a key/value pair to this table's sequence, and index it by key.
   *
   * @param key The key.
   * @param value The value.
   * @param position The input position, or {@code null} for an entry added through the editing API.
   * @param comments The comments attached to the entry.
   * @return The entry created.
   */
  private Entry.KeyValue put(String key, Value value, @Nullable TomlPosition position, List<TomlComment> comments) {
    Entry.KeyValue entry = new Entry.KeyValue(key, value, position, comments);
    add(entry);
    properties.put(key, entry);
    return entry;
  }

  /**
   * Build a key/value pair added through the editing API: no position, and marked as added.
   *
   * @param key The key.
   * @param value The value, already wrapped; see {@link Value#of}.
   * @param comments The comments attached to the entry.
   * @return The entry, not yet part of any table.
   */
  private static Entry.KeyValue newEditedEntry(String key, Value value, List<TomlComment> comments) {
    Entry.KeyValue entry = new Entry.KeyValue(key, value, null, comments);
    entry.valueModified = true;
    return entry;
  }

  /**
   * Append a key/value pair added through the editing API: no position, and marked as added.
   *
   * @param key The key.
   * @param value The value, already wrapped; see {@link Value#of}.
   * @param comments The comments attached to the entry.
   * @return The entry created.
   */
  private Entry.KeyValue putEdited(String key, Value value, List<TomlComment> comments) {
    Entry.KeyValue entry = newEditedEntry(key, value, comments);
    add(entry);
    properties.put(key, entry);
    return entry;
  }

  LinkedTomlTable createParsedTable(List<String> path, TomlPosition position) {
    return createParsedTable(path, position, Collections.emptyList());
  }

  LinkedTomlTable createParsedTable(List<String> path, TomlPosition position, List<TomlComment> comments) {
    if (path.isEmpty()) {
      return this;
    }

    int depth = path.size();
    final LinkedTomlTable table = ensureTable(path.subList(0, depth - 1), position, true, true).table;

    String key = path.get(depth - 1);
    Entry.KeyValue entry = table.properties.get(key);
    if (entry == null) {
      final LinkedTomlTable newTable = new LinkedTomlTable(position);
      table.put(key, newTable, position, comments);
      return newTable;
    }
    if (entry.value instanceof LinkedTomlTable) {
      final LinkedTomlTable subTable = (LinkedTomlTable) entry.value;
      if (!subTable.isDefined()) {
        subTable.define(position);
        // The entry was created implicitly by an earlier dotted key; it now takes over the header's position and
        // comments, in place, so it keeps its spot in the table's sequence.
        entry.define(position, comments);
        return subTable;
      }
    }
    String message = Toml.joinKeyPath(path) + " previously defined at " + entry.position();
    throw new TomlParseError(message, position);
  }

  LinkedTomlTable createParsedTableArray(List<String> path, TomlPosition position) {
    return createParsedTableArray(path, position, Collections.emptyList());
  }

  LinkedTomlTable createParsedTableArray(List<String> path, TomlPosition position, List<TomlComment> comments) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("empty path");
    }

    int depth = path.size();
    final LinkedTomlTable table = ensureTable(path.subList(0, depth - 1), position, true, true).table;

    String key = path.get(depth - 1);
    Entry.KeyValue entry = table.properties.get(key);
    if (entry == null) {
      entry = table.put(key, new ListTomlArray(true, position), position, Collections.emptyList());
    }
    if (!(entry.value instanceof TomlArray)) {
      String message = Toml.joinKeyPath(path) + " is not an array (previously defined at " + entry.position() + ")";
      throw new TomlParseError(message, position);
    }
    if (!(entry.value instanceof ListTomlArray) || !((ListTomlArray) entry.value).isTableArray()) {
      String message = Toml.joinKeyPath(path) + " previously defined as a literal array at " + entry.position();
      throw new TomlParseError(message, position);
    }
    ListTomlArray array = (ListTomlArray) entry.value;
    // The new table's own position is the header's, since [[x]] gives each element table it opens a position of its
    // own rather than sharing the array's.
    LinkedTomlTable newTable = new LinkedTomlTable(position);
    // Each header of an array of tables is an expression of its own, so its comments belong to the element it opens
    // rather than to the array as a whole.
    array.appendParsed(newTable, position, comments);
    return newTable;
  }

  List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> setParsed(
      String keyPath,
      Object value,
      TomlPosition position) {
    return setParsed(parseDottedKey(keyPath), value, position);
  }

  List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> setParsed(
      List<String> path,
      Object value,
      TomlPosition position) {
    return setParsed(path, value, position, Collections.emptyList());
  }

  List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> setParsed(
      List<String> path,
      Object value,
      TomlPosition position,
      List<TomlComment> comments) {
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }
    assert (typeFor(value).isPresent()) : "Unexpected value of type " + value.getClass();
    return setParsed(path, Value.of(value, position), position, comments);
  }

  /**
   * Set the value at a key path, creating any intermediate tables the path needs.
   *
   * @param path The key path.
   * @param value The value, already wrapped; see {@link Value#of}.
   * @param position The input position.
   * @param comments The comments attached to the entry.
   * @return The intermediate tables created along the path, each paired with the position it should be defined at if a
   *         later header claims it.
   */
  List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> setParsed(
      List<String> path,
      Value value,
      TomlPosition position,
      List<TomlComment> comments) {
    int depth = path.size();
    assert (depth > 0);

    final EnsureTableResult result = ensureTable(path.subList(0, depth - 1), position, false, false);
    final LinkedTomlTable table = result.table;

    String key = path.get(depth - 1);
    Entry.KeyValue previous = table.properties.get(key);
    if (previous != null) {
      String pathString = Toml.joinKeyPath(path);
      String message = pathString + " previously defined at " + previous.position();
      throw new TomlParseError(message, position);
    }
    table.put(key, value, position, comments);
    return result.intermediates;
  }

  private static class EnsureTableResult {
    final LinkedTomlTable table;
    final List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> intermediates;

    private EnsureTableResult(
        LinkedTomlTable table,
        List<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> intermediates) {
      this.table = table;
      this.intermediates = intermediates;
    }
  }

  /**
   * Ensure a table exists at a given path.
   *
   * @param path The path to ensure exists (as a table)
   * @param position The input position.
   * @param followTableArrays If `true`, path walking is permitted via the last element of array tables.
   * @param followDefinedTables Allow path walking through defined tables.
   * @return The
   * @throws TomlParseError If the table cannot be created.
   */
  private EnsureTableResult ensureTable(
      List<String> path,
      TomlPosition position,
      boolean followTableArrays,
      boolean followDefinedTables) {
    LinkedTomlTable table = this;
    int depth = path.size();

    if (depth == 0) {
      return new EnsureTableResult(table, Collections.emptyList());
    }

    ArrayList<AbstractMap.SimpleEntry<LinkedTomlTable, TomlPosition>> elements = new ArrayList<>();
    for (int i = 0; i < depth; ++i) {
      String key = path.get(i);
      Entry.KeyValue entry = table.properties.get(key);
      if (entry == null) {
        entry = table.put(key, new LinkedTomlTable(), position, Collections.emptyList());
      }
      if (entry.value instanceof LinkedTomlTable) {
        table = (LinkedTomlTable) entry.value;
        if (table.inline) {
          String message = Toml.joinKeyPath(path.subList(0, i + 1))
              + " is an inline table (defined at "
              + table.position()
              + ") and cannot be extended";
          throw new TomlParseError(message, position);
        }
        if (!followDefinedTables && table.position() != null) {
          String message = Toml.joinKeyPath(path.subList(0, i + 1)) + " already defined at " + table.position();
          throw new TomlParseError(message, position);
        }
        elements.add(new AbstractMap.SimpleEntry<>(table, entry.position()));
        continue;
      }
      if (entry.value instanceof TomlTable) {
        String message = Toml.joinKeyPath(path.subList(0, i + 1))
            + " is not a table (previously defined at "
            + entry.position()
            + ")";
        throw new TomlParseError(message, position);
      }
      if (followTableArrays && entry.value instanceof ListTomlArray) {
        ListTomlArray array = (ListTomlArray) entry.value;
        if (array.isTableArray()) {
          assert !array.isEmpty();
          table = (LinkedTomlTable) array.get(array.size() - 1);
          elements.add(new AbstractMap.SimpleEntry<>(table, entry.position()));
          continue;
        }
      }
      String message =
          Toml.joinKeyPath(path.subList(0, i + 1)) + " is not a table (previously defined at " + entry.position() + ")";
      throw new TomlParseError(message, position);
    }
    return new EnsureTableResult(table, elements);
  }

  @Override
  public LinkedTomlTable set(List<String> path, Object value) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    requireNoNullElement(path);
    checkKeyPath(path);
    Object normalized = TomlValues.normalize(value);
    int depth = path.size();
    LinkedTomlTable table = ensureEditedTable(path.subList(0, depth - 1));
    String key = path.get(depth - 1);
    Entry.KeyValue existing = table.properties.get(key);
    if (existing != null) {
      existing.replace(Value.of(normalized, null));
    } else {
      table.putEdited(key, Value.of(normalized, null), Collections.emptyList());
    }
    return this;
  }

  @Override
  public LinkedTomlTable insertBefore(List<String> anchorPath, String key, Object value) {
    return insert(anchorPath, key, value, false);
  }

  @Override
  public LinkedTomlTable insertAfter(List<String> anchorPath, String key, Object value) {
    return insert(anchorPath, key, value, true);
  }

  // Shared by insertBefore(List, String, Object) and insertAfter(List, String, Object); after selects which side of
  // the anchor the new entry goes on.
  private LinkedTomlTable insert(List<String> anchorPath, String key, Object value, boolean after) {
    if (anchorPath.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    requireNoNullElement(anchorPath);
    requireNonNull(key);
    TomlValues.checkKey(key);
    Object normalized = TomlValues.normalize(value);

    LinkedTomlTable table = parentTable(anchorPath);
    String anchorKey = anchorPath.get(anchorPath.size() - 1);
    Entry.KeyValue anchor = (table != null) ? table.properties.get(anchorKey) : null;
    if (anchor == null) {
      throw new NoSuchElementException(Toml.joinKeyPath(anchorPath) + " is not set");
    }
    table.insertEntry(anchor, key, normalized, after);
    return this;
  }

  @Override
  public LinkedTomlTable insertBefore(TomlElement anchor, String key, Object value) {
    requireNonNull(anchor);
    requireNonNull(key);
    TomlValues.checkKey(key);
    Object normalized = TomlValues.normalize(value);
    return insertEntry(anchor, key, normalized, false);
  }

  @Override
  public LinkedTomlTable insertAfter(TomlElement anchor, String key, Object value) {
    requireNonNull(anchor);
    requireNonNull(key);
    TomlValues.checkKey(key);
    Object normalized = TomlValues.normalize(value);
    return insertEntry(anchor, key, normalized, true);
  }

  // Shared by insertBefore(TomlElement, String, Object) and insertAfter(TomlElement, String, Object), and by
  // insert(List, String, Object, boolean) once it has walked to the anchor's own table; anchor and key are already
  // validated non-null, and value already converted. after selects which side of the anchor the new entry goes on.
  private LinkedTomlTable insertEntry(TomlElement anchor, String key, Object normalized, boolean after) {
    int index = indexOfElement(anchor);
    if (index < 0) {
      throw new NoSuchElementException("anchor is not an element of this table");
    }
    if (properties.containsKey(key)) {
      throw new TomlKeyAlreadySetException(Toml.joinKeyPath(Collections.singletonList(key)) + " is already set");
    }
    Entry.KeyValue entry = newEditedEntry(key, Value.of(normalized, null), Collections.emptyList());
    insert(after ? index + 1 : index, entry);
    reindex();
    return this;
  }

  @Override
  public LinkedTomlTable insertCommentBefore(List<String> anchorPath, TomlComment comment) {
    return insertComment(anchorPath, comment, false);
  }

  @Override
  public LinkedTomlTable insertCommentAfter(List<String> anchorPath, TomlComment comment) {
    return insertComment(anchorPath, comment, true);
  }

  // Shared by insertCommentBefore(List, TomlComment) and insertCommentAfter(List, TomlComment); after selects which
  // side of the anchor the comment goes on.
  private LinkedTomlTable insertComment(List<String> anchorPath, TomlComment comment, boolean after) {
    if (anchorPath.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    requireNoNullElement(anchorPath);

    LinkedTomlTable table = parentTable(anchorPath);
    String anchorKey = anchorPath.get(anchorPath.size() - 1);
    Entry.KeyValue anchor = (table != null) ? table.properties.get(anchorKey) : null;
    if (anchor == null) {
      throw new NoSuchElementException(Toml.joinKeyPath(anchorPath) + " is not set");
    }
    table.insertComment(anchor, comment, after);
    return this;
  }

  @Override
  public LinkedTomlTable insertCommentBefore(TomlElement anchor, TomlComment comment) {
    requireNonNull(anchor);
    requireNonNull(comment);
    return insertComment(anchor, comment, false);
  }

  @Override
  public LinkedTomlTable insertCommentAfter(TomlElement anchor, TomlComment comment) {
    requireNonNull(anchor);
    requireNonNull(comment);
    return insertComment(anchor, comment, true);
  }

  // Shared by insertCommentBefore(TomlElement, TomlComment) and insertCommentAfter(TomlElement, TomlComment), and by
  // insertComment(List, TomlComment, boolean) once it has walked to the anchor's own table; anchor and comment are
  // already validated non-null. after selects which side of the anchor the comment goes on.
  private LinkedTomlTable insertComment(TomlElement anchor, TomlComment comment, boolean after) {
    int index = indexOfElement(anchor);
    if (index < 0) {
      throw new NoSuchElementException("anchor is not an element of this table");
    }
    insertEditedComment(after ? index + 1 : index, comment.requireUnattached().withoutPosition());
    return this;
  }

  // Rebuild properties from elements(), in sequence order. A LinkedHashMap otherwise iterates in insertion order, so
  // an entry inserted ahead of others already indexed would be listed last; run after such an insertion.
  private void reindex() {
    properties.clear();
    for (TomlElement element : elements()) {
      if (element instanceof Entry.KeyValue) {
        Entry.KeyValue entry = (Entry.KeyValue) element;
        properties.put(entry.key, entry);
      }
    }
  }

  // Throws before anything else changes if any element of path is null. A loop, not path.contains(null): List.of()
  // rejects null in contains() too, with no argument identifying which path element was null.
  private static void requireNoNullElement(List<String> path) {
    for (String element : path) {
      if (element == null) {
        throw new NullPointerException("path element");
      }
    }
  }

  // Checked before any table along path is created, so a rejected call leaves the model unchanged.
  private static void checkKeyPath(List<String> path) {
    for (String key : path) {
      TomlValues.checkKey(key);
    }
  }

  /**
   * Walk to the table at a path for the editing API, creating any table along it that does not already exist.
   *
   * <p>
   * Unlike {@link #ensureTable}, which enforces the parse-time rules around inline and already-defined tables, any
   * table may be walked through or extended here: those rules describe what a document's syntax can express, not what
   * the editing API is allowed to build.
   *
   * @param path The path to walk, or create, as a table.
   * @return The table at the end of the path, or this table if {@code path} is empty.
   * @throws TomlInvalidTypeException If an element of the path exists and is not a table.
   */
  private LinkedTomlTable ensureEditedTable(List<String> path) {
    LinkedTomlTable table = this;
    int depth = path.size();
    int i = 0;
    for (; i < depth; i++) {
      Entry.KeyValue entry = table.properties.get(path.get(i));
      if (entry == null) {
        break;
      }
      if (!(entry.value instanceof LinkedTomlTable)) {
        String badPath = Toml.joinKeyPath(path.subList(0, i + 1));
        throw new TomlInvalidTypeException(
            "Value of '" + badPath + "' is a " + TomlType.typeNameFor(entry.value.get()));
      }
      table = (LinkedTomlTable) entry.value;
    }
    for (; i < depth; i++) {
      LinkedTomlTable newTable = new LinkedTomlTable();
      table.putEdited(path.get(i), newTable, Collections.emptyList());
      table = newTable;
    }
    return table;
  }

  @Override
  public LinkedTomlTable getOrCreateTable(List<String> path) {
    requireNoNullElement(path);
    checkKeyPath(path);
    return ensureEditedTable(path);
  }

  @Override
  public ListTomlArray getOrCreateArray(List<String> path) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    requireNoNullElement(path);
    checkKeyPath(path);
    int depth = path.size();
    LinkedTomlTable table = ensureEditedTable(path.subList(0, depth - 1));
    String key = path.get(depth - 1);
    Entry.KeyValue entry = table.properties.get(key);
    if (entry == null) {
      ListTomlArray array = new ListTomlArray(false, null);
      table.putEdited(key, array, Collections.emptyList());
      return array;
    }
    if (!(entry.value instanceof ListTomlArray)) {
      throw new TomlInvalidTypeException(
          "Value of '" + Toml.joinKeyPath(path) + "' is a " + TomlType.typeNameFor(entry.value.get()));
    }
    return (ListTomlArray) entry.value;
  }

  @Override
  @Nullable
  public Object remove(List<String> path) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    requireNoNullElement(path);
    LinkedTomlTable table = parentTable(path);
    Entry.KeyValue removed = (table != null) ? table.properties.remove(path.get(path.size() - 1)) : null;
    if (removed == null) {
      return null;
    }
    boolean removedFromElements = table.removeElement(removed);
    assert removedFromElements : "removed entry is not among elements()";
    return removed.value.get();
  }

  @Override
  public void clear() {
    properties.clear();
    removeAllEntries();
  }

  @Override
  public boolean isModified(List<String> path) {
    if (path.isEmpty()) {
      return isModified();
    }
    Entry.KeyValue entry = entry(path);
    return entry != null && entry.isModified();
  }

  @Override
  public LinkedTomlTable addComment(TomlComment comment) {
    addEditedComment(comment.requireUnattached().withoutPosition());
    return this;
  }

  @Override
  public boolean removeComment(TomlComment comment) {
    return removeElement(comment);
  }

  @Override
  public MutableTomlTable reformat(TomlWriteOptions.Keep keep) {
    reformatAs(keep);
    return this;
  }

  /**
   * Create a deep copy of this table for the editing API; see {@link #copyFrom(TomlTable, boolean)}. Keeps
   * {@link #inline}, since it describes how the table is written, not where its entries came from.
   *
   * @return A copy of this table.
   */
  LinkedTomlTable copy() {
    return copyFrom(this, inline);
  }

  /**
   * Build a table from another implementation of {@link TomlTable}; see {@link #copyFrom(TomlTable, boolean)}. The
   * public interface does not expose whether a foreign table is inline, so the copy is not.
   *
   * @param table The table to copy.
   * @return A new table with the same entries.
   */
  static LinkedTomlTable copyFrom(TomlTable table) {
    return copyFrom(table, false);
  }

  /**
   * Build a table with the same entries as another, walked through its public interface: the same entries in the same
   * order, each with no position and marked as added, with each entry's attached comments and the unattached comments
   * in their places, copied without their positions. A nested table or array is copied recursively, through
   * {@link TomlValues#normalize}; every other value is shared.
   *
   * <p>
   * An entry keeps the record of where it was written, and so do this table's own brackets if it was written as an
   * inline table, so that a copy of a parsed document can still be written the way the document was; the copy's own
   * header span stays null: a copy is not in a document, so it has no header. The amount to keep set through
   * {@link #reformat(TomlWriteOptions.Keep)} is copied as well, since it applies wherever the table is stored.
   *
   * @param table The table to copy.
   * @param inline Whether the copy is an inline table.
   * @return A new table with the same entries.
   */
  private static LinkedTomlTable copyFrom(TomlTable table, boolean inline) {
    LinkedTomlTable copy = new LinkedTomlTable(null, inline);
    if (table instanceof ElementContainer) {
      copy.bracketSpan = ((ElementContainer<?>) table).bracketSpan;
      copy.keep = ((ElementContainer<?>) table).keep;
    }
    if (table instanceof LinkedTomlTable) {
      copy.inlineRequested = ((LinkedTomlTable) table).inlineRequested;
    }
    for (TomlElement element : table.elements()) {
      if (element instanceof TomlComment) {
        copy.addParsedComment(((TomlComment) element).withoutPosition());
      } else {
        TomlKeyValue original = (TomlKeyValue) element;
        TomlValues.checkKey(original.key());
        // A fresh wrapper even for a scalar: an entry's position is dropped, and an array entry's is its value's.
        TomlValue originalValue = original.value();
        Value value = Value.copyOf(originalValue, TomlValues.normalize(originalValue.get()));
        Entry.KeyValue entry =
            copy.putEdited(original.key(), value, TomlComment.copyWithoutPositions(original.comments()));
        if (original instanceof Entry) {
          entry.span = ((Entry) original).span;
        }
      }
    }
    return copy;
  }
}
