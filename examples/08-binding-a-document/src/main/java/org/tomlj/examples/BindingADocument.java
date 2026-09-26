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

import org.tomlj.Toml;
import org.tomlj.TomlBindException;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Binds a whole configuration file to a tree of records.
 */
public final class BindingADocument {

  // Each key of a table is bound to the record component of the same name, and each value to the component's type.
  record Config(String title, Server server, Database database, List<Warehouse> warehouse) {}

  record Server(String host, int port, double timeout) {}

  // The dotted keys pool.min and pool.max define a table pool, bound to a Pool.
  record Database(String url, Pool pool) {}

  record Pool(int min, int max) {}

  // Each [[warehouse]] section is an element of the list.
  record Warehouse(String name, LocalDate opened, List<Integer> bins) {}

  public static void main(String[] args) throws IOException {
    Path file = Path.of(args.length > 0 ? args[0] : "config.toml");
    TomlParseResult result = Toml.parse(file);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    try {
      Config config = result.as(Config.class);
      System.out.println(config.title() + " listens on " + config.server().host() + ":" + config.server().port());
      System.out.println("database pool: " + config.database().pool().min() + " to " + config.database().pool().max());
      for (Warehouse warehouse : config.warehouse()) {
        System.out
            .println("warehouse " + warehouse.name() + ", opened " + warehouse.opened() + ", bins " + warehouse.bins());
      }
    } catch (TomlBindException e) {
      // Binding throws once every value has been bound, with each value that does not fit the type declared for it.
      // Each error prints with its path and position.
      e.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }
  }
}
