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

import org.tomlj.MutableTomlArray;
import org.tomlj.MutableTomlTable;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlValue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Builds a document from scratch, with tables, arrays, comments and chosen notations, and writes it as TOML.
 */
public final class BuildingADocument {

  public static void main(String[] args) {
    MutableTomlTable doc = MutableTomlTable.create();

    // An unattached comment added first is written at the top of the document.
    doc.addComment("Written by the 03-building-a-document example.");

    // set() adds an entry after the last one, or replaces the value of an entry already there.
    doc.set("title", "Inventory service");
    doc.set("released", LocalDate.of(2026, 9, 25));
    doc.setCommentAbove("title", "The name shown in the dashboard.");

    // A dotted key creates the tables it passes through.
    doc.set("server.host", "0.0.0.0");
    doc.set("server.port", 8443);
    doc.setCommentAfter("server.port", "HTTPS only");

    // getOrCreateTable returns the table under a key, creating it if needed.
    MutableTomlTable server = doc.getOrCreateTable("server");
    server.set("allowed", MutableTomlArray.of("10.0.0.0/8", "192.168.0.0/16"));
    server.set("started", OffsetDateTime.of(2026, 9, 25, 8, 0, 0, 0, ZoneOffset.UTC));

    // The factories on TomlValue set the notation a value is written in.
    MutableTomlTable storage = doc.getOrCreateTable("storage");
    storage.set("path", TomlValue.literal("C:\\data\\inventory"));
    storage.set("mode", TomlValue.octal(0640));
    storage.set("max-bytes", TomlValue.grouped(10_000_000_000L));
    storage.set("flags", TomlValue.hex(0xA0));
    storage.set("origin", TomlValue.parse("{ x = 0, y = 0 }"));

    // A table made with createInline() is written between braces on its entry's line.
    MutableTomlTable limits = MutableTomlTable.createInline();
    limits.set("cpu", 2);
    limits.set("memory", "4GB");
    storage.set("limits", limits);

    // Tables added to an array are written as [[warehouse]] sections.
    MutableTomlArray warehouses = doc.getOrCreateArray("warehouse");
    for (String name : new String[] {"North", "South"}) {
      MutableTomlTable warehouse = MutableTomlTable.create();
      warehouse.set("name", name);
      warehouse.set("bins", MutableTomlArray.of(1, 2, 3));
      warehouses.add(warehouse);
    }
    warehouses.setCommentAbove(0, "The warehouse used by default.");

    // insertBefore and insertAfter place an entry next to one already there.
    doc.insertAfter("title", "version", "2.1.0");

    String toml = doc.toToml();
    System.out.print(toml);

    // The output parses back to the values that were set.
    TomlParseResult reparsed = Toml.parse(toml);
    if (reparsed.hasErrors()) {
      throw new IllegalStateException("the document did not parse: " + reparsed.errors());
    }
    System.out.println();
    System.out.println("mode read back: " + reparsed.getLong("storage.mode"));
  }
}
