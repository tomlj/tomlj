/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

final class MutableTomlTable implements TomlTable {

  private static class Element {
    final Object value;
    final TomlPosition position;

    private Element(Object value, TomlPosition position) {
      this.value = value;
      this.position = position;
    }
  }

  static final TomlTable EMPTY = new MutableTomlTable(true);
  private Map<String, Element> properties = new LinkedHashMap<>();
  private boolean implicitlyDefined;

  MutableTomlTable() {
    this(false);
  }

  private MutableTomlTable(boolean implicitlyDefined) {
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
  @Nullable
  @SuppressWarnings("unchecked")
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
      MutableTomlTable newTable = new MutableTomlTable();
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

  MutableTomlTable createArrayTable(List<String> path, TomlPosition position) {
    if (path.isEmpty()) {
      throw new IllegalArgumentException("empty path");
    }

    int depth = path.size();
    MutableTomlTable table = ensureTable(path.subList(0, depth - 1), position, true);

    String key = path.get(depth - 1);
    Element element = table.properties.computeIfAbsent(key, k -> new Element(new MutableTomlArray(), position));
    if (!(element.value instanceof MutableTomlArray)) {
      String message = Toml.joinKeyPath(path) + " is not an array (previously defined at " + element.position + ")";
      throw new TomlParseError(message, position);
    }
    MutableTomlArray array = (MutableTomlArray) element.value;
    if (array.wasDefinedAsLiteral()) {
      String message = Toml.joinKeyPath(path) + " previously defined as a literal array at " + element.position;
      throw new TomlParseError(message, position);
    }
    MutableTomlTable newTable = new MutableTomlTable();
    array.append(newTable, position);
    return newTable;
  }

  MutableTomlTable set(String keyPath, Object value, TomlPosition position) {
    return set(parseDottedKey(keyPath), value, position);
  }

  MutableTomlTable set(List<String> path, Object value, TomlPosition position) {
    int depth = path.size();
    assert (depth > 0);
    assert (value != null);
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

  private MutableTomlTable ensureTable(List<String> path, TomlPosition position, boolean followArrayTables) {
    MutableTomlTable table = this;
    int depth = path.size();
    for (int i = 0; i < depth; ++i) {
      Element element =
          table.properties.computeIfAbsent(path.get(i), k -> new Element(new MutableTomlTable(true), position));
      if (element.value instanceof MutableTomlTable) {
        table = (MutableTomlTable) element.value;
        continue;
      }
      if (followArrayTables && element.value instanceof MutableTomlArray) {
        MutableTomlArray array = (MutableTomlArray) element.value;
        if (!array.wasDefinedAsLiteral() && !array.isEmpty() && array.containsTables()) {
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
