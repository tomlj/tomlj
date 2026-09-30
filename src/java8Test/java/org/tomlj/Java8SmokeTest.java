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
import java.io.StringReader;

/**
 * Exercises the library on a Java 8 runtime, where the unit tests cannot run: JUnit 6 requires Java 17.
 */
public final class Java8SmokeTest {

  private Java8SmokeTest() {}

  public static void main(String[] args) throws IOException {
    System.out.println("Running on Java " + System.getProperty("java.version"));

    // Toml.parse(Reader) reads the input in a try-with-resources statement.
    TomlParseResult result =
        Toml.parse(new StringReader("name = \"prod\"\n\n[[servers]]\nhost = \"a\"\nport = 8001\n"));
    check(!result.hasErrors(), "parse errors: " + result.errors());
    check("prod".equals(result.getString("name")), "name");
    check(Long.valueOf(8001).equals(result.getArray("servers").getTable(0).getLong("port")), "port");
    check(Toml.parse("x = [").hasErrors(), "an unclosed array is reported");

    String written = result.toToml();
    TomlParseResult reparsed = Toml.parse(written);
    check(
        "prod".equals(reparsed.getString("name"))
            && Long.valueOf(8001).equals(reparsed.getArray("servers").getTable(0).getLong("port")),
        "written and parsed again: " + written);
    check(result.toJson().contains("\"prod\""), "JSON: " + result.toJson());

    System.out.println("Java 8 smoke test passed");
  }

  private static void check(boolean condition, String what) {
    if (!condition) {
      throw new AssertionError(what);
    }
  }
}
