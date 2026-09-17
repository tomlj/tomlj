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

final class MutableTomlTable extends ElementContainer<Entry.KeyValue> implements TomlTable {

  private final Map<String, Entry.KeyValue> properties = new LinkedHashMap<>();
  private final TomlVersion version;

  // Not final: a table created implicitly by a dotted key or by a leading key of a header such as [a.b] has no
  // position until a header defines this table itself, at which point it takes the header's position; see #define. The
  // root table is defined from the start of the document, and an inline table from the moment it is created, at the
  // position of its opening '{'.
  private @Nullable TomlPosition position;

  private final boolean inline;

  MutableTomlTable(TomlVersion version, @Nullable TomlPosition position) {
    this(version, position, false);
  }

  MutableTomlTable(TomlVersion version) {
    this(version, null, false);
  }

  private MutableTomlTable(TomlVersion version, @Nullable TomlPosition position, boolean inline) {
    this.version = version;
    this.position = position;
    this.inline = inline;
  }

  /**
   * Create a table for an inline table in a document.
   *
   * <p>
   * Inline tables are self-contained: once closed, no table header or dotted key may add to them.
   *
   * @param version The TOML version.
   * @param position The position of the inline table.
   * @return A new, defined table.
   */
  static MutableTomlTable inline(TomlVersion version, TomlPosition position) {
    return new MutableTomlTable(version, position, true);
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
    return properties.entrySet().stream().flatMap(entry -> {
      String key = entry.getKey();
      List<String> basePath = Collections.singletonList(key);

      Object value = entry.getValue().value().get();
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
        .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().value().get()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
    return properties.entrySet().stream().flatMap(entry -> {
      String key = entry.getKey();
      List<String> entryPath = Collections.singletonList(key);
      Object value = entry.getValue().value().get();

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
  public Object get(List<String> path) {
    if (path.isEmpty()) {
      return this;
    }
    Entry.KeyValue element = getElement(path);
    return (element != null) ? element.value().get() : null;
  }

  @Override
  @Nullable
  public TomlPosition inputPositionOf(List<String> path) {
    if (path.isEmpty()) {
      return position;
    }
    Entry.KeyValue element = getElement(path);
    return (element != null) ? element.position() : null;
  }

  @Override
  public List<TomlComment> comments(List<String> path) {
    if (path.isEmpty()) {
      return Collections.emptyList();
    }
    Entry.KeyValue element = getElement(path);
    return (element != null) ? element.comments() : Collections.emptyList();
  }

  private Entry.KeyValue getElement(List<String> path) {
    MutableTomlTable table = this;
    int depth = path.size();
    assert depth > 0;
    for (int i = 0; i < (depth - 1); ++i) {
      Entry.KeyValue element = table.properties.get(path.get(i));
      if (element == null) {
        return null;
      }
      if (element.element instanceof MutableTomlTable) {
        table = (MutableTomlTable) element.element;
        continue;
      }
      return null;
    }
    return table.properties.get(path.get(depth - 1));
  }

  @Override
  public Map<String, Object> toMap() {
    return properties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value().get()));
  }

  /**
   * Append a key/value pair to this table's sequence, and index it by key.
   *
   * <p>
   * The comments belong to the pair, not to the value, even when the value is a table or array: in {@code k = v # c}
   * the comment is attached to {@code k}.
   *
   * @param key The key.
   * @param value The value.
   * @param position The input position.
   * @param comments The comments attached to the entry.
   * @return The element created for the entry.
   */
  private Entry.KeyValue put(String key, Entry.Value value, TomlPosition position, List<TomlComment> comments) {
    assert value.comments().isEmpty() : "Comments on a key/value pair belong to the pair, not the value";
    Entry.KeyValue element = new Entry.KeyValue(key, value, position, comments);
    add(element);
    properties.put(key, element);
    return element;
  }

  MutableTomlTable createTable(List<String> path, TomlPosition position) {
    return createTable(path, position, Collections.emptyList());
  }

  MutableTomlTable createTable(List<String> path, TomlPosition position, List<TomlComment> comments) {
    if (path.isEmpty()) {
      return this;
    }

    int depth = path.size();
    final MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, true, true).table;

    String key = path.get(depth - 1);
    Entry.KeyValue element = table.properties.get(key);
    if (element == null) {
      final MutableTomlTable newTable = new MutableTomlTable(version, position);
      table.put(key, newTable, position, comments);
      return newTable;
    }
    if (element.element instanceof MutableTomlTable) {
      final MutableTomlTable subTable = (MutableTomlTable) element.element;
      if (!subTable.isDefined()) {
        subTable.define(position);
        // The entry was created implicitly by an earlier dotted key; it now takes over the header's position and
        // comments, in place, so it keeps its spot in the table's sequence.
        element.define(position, comments);
        return subTable;
      }
    }
    String message = Toml.joinKeyPath(path) + " previously defined at " + element.position();
    throw new TomlParseError(message, position);
  }

  MutableTomlTable createTableArray(List<String> path, TomlPosition position) {
    return createTableArray(path, position, Collections.emptyList());
  }

  MutableTomlTable createTableArray(List<String> path, TomlPosition position, List<TomlComment> comments) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("empty path");
    }

    int depth = path.size();
    final MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, true, true).table;

    String key = path.get(depth - 1);
    Entry.KeyValue element = table.properties.get(key);
    if (element == null) {
      element = table.put(key, new MutableTomlArray(true, position), position, Collections.emptyList());
    }
    if (!(element.element instanceof TomlArray)) {
      String message = Toml.joinKeyPath(path) + " is not an array (previously defined at " + element.position() + ")";
      throw new TomlParseError(message, position);
    }
    if (!(element.element instanceof MutableTomlArray) || !((MutableTomlArray) element.element).isTableArray()) {
      String message = Toml.joinKeyPath(path) + " previously defined as a literal array at " + element.position();
      throw new TomlParseError(message, position);
    }
    MutableTomlArray array = (MutableTomlArray) element.element;
    // The new table's own position is the header's, since [[x]] gives each element table it opens a position of its
    // own rather than sharing the array's.
    MutableTomlTable newTable = new MutableTomlTable(version, position);
    // Each header of an array of tables is an expression of its own, so its comments belong to the element it opens
    // rather than to the array as a whole.
    array.append(Entry.Value.of(newTable, position, comments));
    return newTable;
  }

  List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> set(
      String keyPath,
      Object value,
      TomlPosition position) {
    return set(parseDottedKey(keyPath), value, position);
  }

  List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> set(
      List<String> path,
      Object value,
      TomlPosition position) {
    return set(path, value, position, Collections.emptyList());
  }

  List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> set(
      List<String> path,
      Object value,
      TomlPosition position,
      List<TomlComment> comments) {
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }
    assert (typeFor(value).isPresent()) : "Unexpected value of type " + value.getClass();
    return set(path, Entry.Value.of(value, position), position, comments);
  }

  /**
   * Set the value at a key path, creating any intermediate tables the path needs.
   *
   * @param path The key path.
   * @param value The value, already wrapped as an element; see {@link Entry.Value#of}.
   * @param position The input position.
   * @param comments The comments attached to the entry.
   * @return The intermediate tables created along the path, each paired with the position it should be defined at if a
   *         later header claims it.
   */
  List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> set(
      List<String> path,
      Entry.Value value,
      TomlPosition position,
      List<TomlComment> comments) {
    int depth = path.size();
    assert (depth > 0);

    final EnsureTableResult result = ensureTable(path.subList(0, depth - 1), position, false, false);
    final MutableTomlTable table = result.table;

    String key = path.get(depth - 1);
    Entry.KeyValue prevElem = table.properties.get(key);
    if (prevElem != null) {
      String pathString = Toml.joinKeyPath(path);
      String message = pathString + " previously defined at " + prevElem.position();
      throw new TomlParseError(message, position);
    }
    table.put(key, value, position, comments);
    return result.intermediates;
  }

  private static class EnsureTableResult {
    final MutableTomlTable table;
    final List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> intermediates;

    private EnsureTableResult(
        MutableTomlTable table,
        List<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> intermediates) {
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
    MutableTomlTable table = this;
    int depth = path.size();

    if (depth == 0) {
      return new EnsureTableResult(table, Collections.emptyList());
    }

    ArrayList<AbstractMap.SimpleEntry<MutableTomlTable, TomlPosition>> elements = new ArrayList<>();
    for (int i = 0; i < depth; ++i) {
      String key = path.get(i);
      Entry.KeyValue element = table.properties.get(key);
      if (element == null) {
        element = table.put(key, new MutableTomlTable(version), position, Collections.emptyList());
      }
      if (element.element instanceof MutableTomlTable) {
        table = (MutableTomlTable) element.element;
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
        elements.add(new AbstractMap.SimpleEntry<>(table, element.position()));
        continue;
      }
      if (element.element instanceof TomlTable) {
        String message = Toml.joinKeyPath(path.subList(0, i + 1))
            + " is not a table (previously defined at "
            + element.position()
            + ")";
        throw new TomlParseError(message, position);
      }
      if (followTableArrays && element.element instanceof MutableTomlArray) {
        MutableTomlArray array = (MutableTomlArray) element.element;
        if (array.isTableArray()) {
          assert !array.isEmpty();
          table = (MutableTomlTable) array.get(array.size() - 1);
          elements.add(new AbstractMap.SimpleEntry<>(table, element.position()));
          continue;
        }
      }
      String message = Toml.joinKeyPath(path.subList(0, i + 1))
          + " is not a table (previously defined at "
          + element.position()
          + ")";
      throw new TomlParseError(message, position);
    }
    return new EnsureTableResult(table, elements);
  }
}
