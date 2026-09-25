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

import static org.tomlj.TomlComment.Placement.ABOVE;
import static org.tomlj.TomlComment.Placement.AFTER;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlComment;
import org.tomlj.TomlElement;
import org.tomlj.TomlKeyValue;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the comments of a settings file and prints a reference of its settings, described by their comments.
 */
public final class ReadingComments {

  public static void main(String[] args) throws IOException {
    TomlParseResult settings = Toml.parse(Path.of(args.length > 0 ? args[0] : "agent.toml"));
    if (settings.hasErrors()) {
      settings.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    // A comment run directly above an entry, or a comment on its line, is attached to the entry.
    TomlComment above = settings.comment("concurrency", ABOVE);
    TomlComment after = settings.comment("concurrency", AFTER);
    System.out.println("concurrency, above: " + above.lines());
    System.out.println("concurrency, after: " + after.text());
    System.out.println();

    // Every other comment is unattached. elements() lists a table's entries and its unattached comments together, in
    // document order.
    for (TomlElement element : settings.elements()) {
      if (element instanceof TomlComment comment) {
        System.out.println("unattached, line " + comment.position().line() + ": " + comment.lines());
      }
    }
    System.out.println();

    // Elements of an array have comments of their own.
    TomlArray mirrors = settings.getArray("cache.mirrors");
    for (int i = 0; i < mirrors.size(); i++) {
      for (TomlComment comment : mirrors.comments(i)) {
        System.out.println(mirrors.getString(i) + ", " + comment.placement() + ": " + comment.text());
      }
    }
    System.out.println();

    System.out.println("Settings reference");
    printReference(settings, new ArrayList<>());
  }

  // Prints each entry with the comment above it as its description and the comment after it as a note.
  private static void printReference(TomlTable table, List<String> path) {
    for (TomlElement element : table.elements()) {
      if (!(element instanceof TomlKeyValue entry)) {
        continue;
      }
      List<String> keyPath = new ArrayList<>(path);
      keyPath.add(entry.key());

      List<String> text = new ArrayList<>();
      TomlComment description = entry.comment(ABOVE);
      if (description != null) {
        text.add(String.join(" ", description.lines()));
      }
      TomlComment note = entry.comment(AFTER);
      if (note != null) {
        text.add("(" + note.text() + ")");
      }
      System.out.println(String.format("  %-16s%s", Toml.joinKeyPath(keyPath), String.join(" ", text)).stripTrailing());

      if (entry.value().isTable()) {
        printReference(entry.value().getTable(), keyPath);
      }
    }
  }
}
