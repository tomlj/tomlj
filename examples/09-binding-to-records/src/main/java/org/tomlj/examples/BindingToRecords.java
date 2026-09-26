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
package org.tomlj.examples;

import org.tomlj.GenericType;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlBindException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Binds a configuration file to records with enums, maps, keys that may be missing and a table left unbound, then binds
 * single tables and arrays of it.
 */
public final class BindingToRecords {

  // [labels] is bound to a map, with an entry for each of its keys. [plugins] is not bound: the component holds the
  // TomlTable itself, for each plugin to read its own settings from.
  record Inventory(
      String name,
      Level level,
      Server server,
      Map<String, String> labels,
      TomlTable plugins,
      List<Warehouse> warehouse) {}

  record Server(String host, int port, boolean tls) {}

  // capacity and manager may be missing: an Optional is then empty, and a @Nullable component is null.
  record Warehouse(
      String name,
      LocalDate opened,
      List<Integer> bins,
      Optional<Integer> capacity,
      @Nullable String manager) {}

  // A string is bound to an enum constant by its name, ignoring case: "info" is INFO.
  enum Level {
    DEBUG, INFO, WARN, ERROR
  }

  public static void main(String[] args) throws IOException {
    Path file = Path.of(args.length > 0 ? args[0] : "inventory.toml");
    TomlParseResult result = Toml.parse(file);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    try {
      Inventory inventory = result.as(Inventory.class);
      System.out
          .println(
              inventory.name()
                  + " logs at "
                  + inventory.level()
                  + ", listens on "
                  + inventory.server().host()
                  + ":"
                  + inventory.server().port()
                  + (inventory.server().tls() ? " with TLS" : ""));
      System.out.println("labels: " + inventory.labels());
      for (Warehouse warehouse : inventory.warehouse()) {
        System.out
            .printf(
                "warehouse %s, opened %d, bins %s, capacity %s, manager %s%n",
                warehouse.name(),
                warehouse.opened().getYear(),
                warehouse.bins(),
                warehouse.capacity().map(String::valueOf).orElse("not set"),
                warehouse.manager() == null ? "not set" : warehouse.manager());
      }
      startPlugins(inventory.plugins());

      // Any table or array of the document can be bound on its own. A Class cannot name a generic type such as
      // Map<String, String>, so a GenericType names it instead.
      TomlTable serverTable = result.getTable("server");
      Server server = serverTable.as(Server.class);

      TomlTable labelsTable = result.getTable("labels");
      GenericType<Map<String, String>> labelsType = new GenericType<Map<String, String>>() {};
      Map<String, String> labels = labelsTable.as(labelsType);

      TomlArray warehouseArray = result.getArray("warehouse");
      Warehouse[] warehouses = warehouseArray.as(Warehouse[].class);

      System.out.println(server + ", " + labels.size() + " labels, " + warehouses.length + " warehouses");
    } catch (TomlBindException e) {
      e.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }
  }

  // Each plugin is given its table of [plugins], and reads it with the getters of TomlTable.
  private static void startPlugins(TomlTable plugins) {
    for (String name : plugins.keySet()) {
      TomlTable settings = plugins.getTable(List.of(name));
      boolean enabled = settings.getBoolean("enabled", () -> false);
      System.out.println("plugin " + name + (enabled ? " enabled" : " disabled") + ", settings " + settings.keySet());
    }
  }
}
