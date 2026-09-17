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

import static org.tomlj.Parser.parseDottedKey;
import static org.tomlj.TomlType.typeFor;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

final class LinkedTomlTable extends ElementContainer<Entry.KeyValue> implements TomlTable {

  private final Map<String, Entry.KeyValue> properties = new LinkedHashMap<>();

  // Not final: a table created implicitly by a dotted key or by a leading key of a header such as [a.b] has no
  // position until a header defines this table itself, at which point it takes the header's position; see #define. The
  // root table is defined from the start of the document, and an inline table from the moment it is created, at the
  // position of its opening '{'.
  private @Nullable TomlPosition position;

  private final boolean inline;

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

  boolean isDefined() {
    return position != null;
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
    return properties.keySet();
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
    }).collect(Collectors.toSet());
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
    return properties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value().get()));
  }

  /**
   * Append a key/value pair to this table's sequence, and index it by key.
   *
   * @param key The key.
   * @param value The value.
   * @param position The input position.
   * @param comments The comments attached to the entry.
   * @return The entry created.
   */
  private Entry.KeyValue put(String key, Value value, TomlPosition position, List<TomlComment> comments) {
    Entry.KeyValue entry = new Entry.KeyValue(key, value, position, comments);
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
}
