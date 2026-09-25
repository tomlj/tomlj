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
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Parses a configuration file and reads values from it with the typed getters.
 */
public final class ReadingADocument {

  public static void main(String[] args) throws IOException {
    Path file = Path.of(args.length > 0 ? args[0] : "config.toml");
    TomlParseResult config = Toml.parse(file);

    // Parsing never throws on invalid TOML. Check the errors before using the result.
    if (config.hasErrors()) {
      config.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    // A String key is a dotted key: "server.host" is the key host in the table server.
    String title = config.getString("title");
    String host = config.getString("server.host");
    long port = config.getLong("server.port");
    double timeout = config.getDouble("server.timeout");
    System.out.println(title + " listens on " + host + ":" + port + " with a " + timeout + "s timeout");

    // A getter returns null for a missing key, and the overload with a supplier returns a default instead.
    String logLevel = config.getString("server.log-level", () -> "info");
    Long workers = config.getLong("server.workers");
    System.out.println("log level: " + logLevel + ", workers: " + (workers == null ? "not set" : workers));

    // Tables can be read whole. The dotted keys pool.min and pool.max define a table pool.
    TomlTable pool = config.getTable("database.pool");
    System.out.println("database pool: " + pool.getLong("min") + " to " + pool.getLong("max") + " connections");

    // [[warehouse]] sections form an array of tables.
    TomlArray warehouses = config.getArrayOrEmpty("warehouse");
    for (int i = 0; i < warehouses.size(); i++) {
      TomlTable warehouse = warehouses.getTable(i);
      String name = warehouse.getString("name");
      LocalDate opened = warehouse.getLocalDate("opened");
      TomlArray bins = warehouse.getArrayOrEmpty("bins");
      System.out.printf("warehouse %s, opened %d, %d bins%n", name, opened.getYear(), bins.size());
    }
  }
}
