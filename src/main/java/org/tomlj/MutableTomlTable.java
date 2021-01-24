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
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

final class MutableTomlTable implements TomlTable {

  private static class Element {
    final Object value;
    final TomlPosition position;

    private Element(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }
  }

  private final Map<String, Element> properties = new LinkedHashMap<>();
  private final TomlVersion version;
  private boolean implicitlyDefined;

  MutableTomlTable(TomlVersion version) {
    this(version, false);
  }

  private MutableTomlTable(TomlVersion version, boolean implicitlyDefined) {
    this.version = version;
    this.implicitlyDefined = implicitlyDefined;
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

      Element element = entry.getValue();
      if (!(element.value instanceof TomlTable)) {
        return Stream.of(basePath);
      }

      Stream<List<String>> subKeys = ((TomlTable) element.value).keyPathSet(includeTables).stream().map(subPath -> {
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
  public Set<Entry<String, Object>> entrySet() {
    return properties
        .entrySet()
        .stream()
        .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().value))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Set<Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
    return properties.entrySet().stream().flatMap(entry -> {
      String key = entry.getKey();
      List<String> entryPath = Collections.singletonList(key);
      Element element = entry.getValue();

      if (!(element.value instanceof TomlTable)) {
        return Stream.of(new AbstractMap.SimpleEntry<>(entryPath, element.value));
      }

      Stream<Entry<List<String>, Object>> subEntries =
          ((TomlTable) element.value).entryPathSet(includeTables).stream().map(subEntry -> {
            List<String> subPath = subEntry.getKey();
            List<String> path = new ArrayList<>(subPath.size() + 1);
            path.add(key);
            path.addAll(subPath);
            return new AbstractMap.SimpleEntry<>(path, subEntry.getValue());
          });

      if (includeTables) {
        return Stream.concat(Stream.of(new AbstractMap.SimpleEntry<>(entryPath, element.value)), subEntries);
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
    Element element = getElement(path);
    return (element != null) ? element.value : null;
  }

  @Override
  @Nullable
  public TomlPosition inputPositionOf(List<String> path) {
    if (path.isEmpty()) {
      return TomlPosition.positionAt(1, 1);
    }
    Element element = getElement(path);
    return (element != null) ? element.position : null;
  }

  private Element getElement(List<String> path) {
    MutableTomlTable table = this;
    int depth = path.size();
    assert depth > 0;
    for (int i = 0; i < (depth - 1); ++i) {
      Element element = table.properties.get(path.get(i));
      if (element == null) {
        return null;
      }
      if (element.value instanceof MutableTomlTable) {
        table = (MutableTomlTable) element.value;
        continue;
      }
      return null;
    }
    return table.properties.get(path.get(depth - 1));
  }

  @Override
  public Map<String, Object> toMap() {
    return properties.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().value));
  }

  MutableTomlTable createTable(List<String> path, TomlPosition position) {
    if (path.isEmpty()) {
      return this;
    }

    int depth = path.size();
    MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, true);

    String key = path.get(depth - 1);
    Element element = table.properties.get(key);
    if (element == null) {
      MutableTomlTable newTable = new MutableTomlTable(version);
      table.properties.put(key, new Element(newTable, position));
      return newTable;
    }
    if (element.value instanceof MutableTomlTable) {
      table = (MutableTomlTable) element.value;
      if (table.implicitlyDefined) {
        table.implicitlyDefined = false;
        table.properties.put(key, new Element(table, position));
        return table;
      }
    }
    String message = Toml.joinKeyPath(path) + " previously defined at " + element.position;
    throw new TomlParseError(message, position);
  }

  MutableTomlTable createTableArray(List<String> path, TomlPosition position) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("empty path");
    }

    int depth = path.size();
    MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, true);

    String key = path.get(depth - 1);
    Element element =
        table.properties.computeIfAbsent(key, k -> new Element(MutableTomlArray.create(version, true), position));
    if (!(element.value instanceof TomlArray)) {
      String message = Toml.joinKeyPath(path) + " is not an array (previously defined at " + element.position + ")";
      throw new TomlParseError(message, position);
    }
    if (!(element.value instanceof MutableTomlArray) || !((MutableTomlArray) element.value).isTableArray()) {
      String message = Toml.joinKeyPath(path) + " previously defined as a literal array at " + element.position;
      throw new TomlParseError(message, position);
    }
    MutableTomlArray array = (MutableTomlArray) element.value;
    MutableTomlTable newTable = new MutableTomlTable(version);
    array.append(newTable, position);
    return newTable;
  }

  MutableTomlTable set(String keyPath, Object value, TomlPosition position) {
    return set(parseDottedKey(keyPath), value, position);
  }

  MutableTomlTable set(List<String> path, Object value, TomlPosition position) {
    int depth = path.size();
    assert (depth > 0);
    if (value instanceof Integer) {
      value = ((Integer) value).longValue();
    }
    assert (typeFor(value).isPresent()) : "Unexpected value of type " + value.getClass();

    MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, false);
    Element prevElem = table.properties.putIfAbsent(path.get(depth - 1), new Element(value, position));
    if (prevElem != null) {
      String pathString = Toml.joinKeyPath(path);
      String message = pathString + " previously defined at " + prevElem.position;
      throw new TomlParseError(message, position);
    }
    return this;
  }

  /**
   * Ensure a table exists at a given path.
   *
   * @param path The path to ensure exists (as a table)
   * @param position The input position.
   * @param followTableArrays If `true`, path walking is permitted via the last element of array tables.
   * @return The table at that path.
   * @throws TomlParseError If the table cannot be created.
   */
  private MutableTomlTable ensureTable(List<String> path, TomlPosition position, boolean followTableArrays) {
    MutableTomlTable table = this;
    int depth = path.size();
    for (int i = 0; i < depth; ++i) {
      Element element = table.properties
          .computeIfAbsent(path.get(i), k -> new Element(new MutableTomlTable(version, true), position));
      if (element.value instanceof MutableTomlTable) {
        table = (MutableTomlTable) element.value;
        continue;
      }
      if (element.value instanceof TomlTable) {
        String message = Toml.joinKeyPath(path.subList(0, i + 1))
            + " is not a table (previously defined at "
            + element.position
            + ")";
        throw new TomlParseError(message, position);
      }
      if (followTableArrays && element.value instanceof MutableTomlArray) {
        MutableTomlArray array = (MutableTomlArray) element.value;
        if (array.isTableArray()) {
          assert !array.isEmpty();
          table = (MutableTomlTable) array.get(array.size() - 1);
          continue;
        }
      }
      String message =
          Toml.joinKeyPath(path.subList(0, i + 1)) + " is not a table (previously defined at " + element.position + ")";
      throw new TomlParseError(message, position);
    }
    return table;
  }
}
