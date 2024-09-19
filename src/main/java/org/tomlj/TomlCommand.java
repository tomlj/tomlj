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

import java.io.IOException;

/**
 * A simple command line tool for parsing TOML and outputing in JSON.
 * <p>
 * The output format is suitable for
 * <a href="https://github.com/BurntSushi/toml-test">https://github.com/BurntSushi/toml-test</a>
 */
public class TomlCommand {

  private TomlCommand() {}

  /**
   * The main method.
   *
   * @param args Command line arguments (ignored).
   **/
  public static void main(String[] args) {
    try {
      TomlParseResult result = Toml.parse(System.in);
      if (result.hasErrors()) {
        result.errors().forEach(error -> {
          System.err.println(error.getMessage());
        });
        System.exit(1);
      }
      result.toJson(System.out, JsonOptions.VALUES_AS_OBJECTS_WITH_TYPE, JsonOptions.ALL_VALUES_AS_STRINGS);
      System.exit(0);
    } catch (IOException e) {
      System.err.println("IO Error: " + e.getClass().getCanonicalName());
      System.exit(1);
    }
  }
}
