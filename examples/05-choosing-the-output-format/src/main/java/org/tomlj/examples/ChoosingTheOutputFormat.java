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
import org.tomlj.TomlParseOptions;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlVersion;
import org.tomlj.TomlWriteOptions;
import org.tomlj.TomlWriteOptions.Keep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes one document in each of the ways TomlWriteOptions offers: as it was read, with its notation kept and a new
 * layout, and entirely in the default style.
 */
public final class ChoosingTheOutputFormat {

  public static void main(String[] args) throws IOException {
    String text = Files.readString(Path.of(args.length > 0 ? args[0] : "deploy.toml"));
    TomlParseResult doc = parse(text, TomlParseOptions.defaults());

    // Keep.LAYOUT, the default, writes an unedited document back exactly as it was read.
    String unchanged = doc.toToml();
    System.out.println("--- Keep.LAYOUT: identical to the input: " + unchanged.equals(text));

    // Keep.NOTATION keeps the order, the comments and the form of each key and value (0x03, 'ops team', the inline
    // table), and lays the document out anew from the options.
    TomlWriteOptions notation = TomlWriteOptions.defaults().keep(Keep.NOTATION).withIndent(2).withMaxLineWidth(60);
    System.out.println("--- Keep.NOTATION, indented by 2, lines up to 60 columns");
    System.out.print(doc.toToml(notation));

    // Keep.NOTHING writes the document in the default style, as a document built in code is written.
    System.out.println("--- Keep.NOTHING");
    System.out.print(doc.toToml(TomlWriteOptions.defaults().keep(Keep.NOTHING)));

    // reformat() sets how much of one table is kept. The rest of the document keeps its layout.
    TomlParseResult partly = parse(text, TomlParseOptions.defaults());
    partly.getTable("targets").reformat(Keep.NOTHING);
    System.out.println("--- targets reformatted, the rest kept");
    System.out.print(partly.toToml());

    // Writing for TOML 1.0.0 throws where the document holds text that 1.0.0 does not allow, here the time 22:00, which
    // has no seconds.
    TomlWriteOptions v100 = TomlWriteOptions.defaults().withVersion(TomlVersion.V1_0_0);
    System.out.println("--- written for TOML 1.0.0");
    try {
      doc.toToml(v100);
    } catch (IllegalArgumentException e) {
      System.out.println(e.getMessage());
    }
    // A value set through the editing API is written anew, as 22:00:00, so replacing the time with itself makes the
    // document one that 1.0.0 allows. Keep.NOTHING would do the same for every value, as it copies no text.
    TomlParseResult forV100 = parse(text, TomlParseOptions.defaults());
    forV100.set("alerts.quiet-from", forV100.getLocalTime("alerts.quiet-from"));
    System.out.print(forV100.toToml(v100));

    // A document that will not be written back can be parsed without its text, which saves memory. It is written in
    // the default style.
    TomlParseResult readOnly = parse(text, TomlParseOptions.defaults().withoutSource());
    boolean same = readOnly.toToml().equals(doc.toToml(TomlWriteOptions.defaults().keep(Keep.NOTHING)));
    System.out.println("--- parsed without the source, same as Keep.NOTHING: " + same);
  }

  private static TomlParseResult parse(String text, TomlParseOptions options) {
    TomlParseResult result = Toml.parse(text, options);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }
    return result;
  }
}
