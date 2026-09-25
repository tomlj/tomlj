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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Edits a hand-written file and writes it back. Everything the edits do not touch, the comments, alignment, blank lines
 * and quoting included, is written as it was read.
 */
public final class EditingInPlace {

  public static void main(String[] args) throws IOException {
    Path source = Path.of("manifest.toml");
    // The example writes to a copy, so that it can be run again.
    Path target = Path.of(args.length > 0 ? args[0] : "build/manifest.toml");

    String original = Files.readString(source);
    TomlParseResult manifest = Toml.parse(original);
    if (manifest.hasErrors()) {
      manifest.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    // A parse result is a MutableTomlTable. Replacing a value keeps its line: the key, the spacing and the comment
    // after it stay as they were.
    manifest.set("package.version", "1.5.0");

    // An array edited in place keeps its brackets and layout. An element added to an array written on one line goes on
    // that line, and one added to an array written over lines goes on a line of its own.
    manifest.getArray("package.authors").add("Ben <ben@example.com>");
    manifest.getArray("features.default").add("cli");

    // A new entry is placed next to an existing one, and indented like the entries around it.
    MutableTomlTable clap = MutableTomlTable.createInline();
    clap.set("version", "4");
    clap.set("features", MutableTomlArray.of("derive"));
    manifest.insertAfter("dependencies.anyhow", "clap", clap);

    // Comments are set by placement.
    manifest.setCommentAfter("dependencies.tokio", "pinned until 2.0");

    // Removing a table removes its section, and the comment above its header.
    manifest.remove("patch");

    String edited = manifest.toToml();
    Files.createDirectories(target.getParent());
    Files.writeString(target, edited);

    System.out.println("Wrote " + target + ", with these changes:");
    LineDiff.print(original, edited);
  }
}
