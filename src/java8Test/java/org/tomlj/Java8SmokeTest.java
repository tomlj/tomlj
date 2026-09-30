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
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Exercises the library on a Java 8 runtime, where the unit tests cannot run: JUnit 6 requires Java 17. Each check
 * calls code that used a Java 9 API or construct before the library was compiled for Java 8.
 */
public final class Java8SmokeTest {

  static final class Server {
    String host = "localhost";
    int port;
  }

  static final class Config {
    String name = "";
    List<Server> servers = Collections.emptyList();
  }

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

    // Editing a comment looks the entry up through MutableTomlTables.
    result.setCommentAbove("name", "the environment");
    check(result.toToml().startsWith("# the environment\nname = \"prod\"\n"), "comment written: " + result.toToml());
    expect(NoSuchElementException.class, () -> result.setCommentAbove("missing", "text"));

    // Inserting into an array checks the index.
    MutableTomlArray servers = result.getArray("servers");
    expect(IndexOutOfBoundsException.class, () -> servers.insertBefore(5, "x"));

    // Binding checks whether a class belongs to the JDK and whether it is null-marked by its module.
    Config config = result.as(Config.class);
    check("prod".equals(config.name), "bound name");
    check(config.servers.size() == 1 && "a".equals(config.servers.get(0).host), "bound servers");
    check(config.servers.get(0).port == 8001, "bound port");
    String written = Toml.toToml(config);
    check(Objects.equals(Toml.parseAs(written, Config.class).name, "prod"), "written and bound again: " + written);

    System.out.println("Java 8 smoke test passed");
  }

  private static void check(boolean condition, String what) {
    if (!condition) {
      throw new AssertionError(what);
    }
  }

  private static void expect(Class<? extends RuntimeException> type, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      check(type.isInstance(e), "expected " + type.getSimpleName() + ", got " + e);
      return;
    }
    throw new AssertionError("expected " + type.getSimpleName());
  }
}
