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
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports every syntax error in a file, then checks the values of a valid file and reports the ones out of range at
 * their positions in the file.
 */
public final class ReportingErrors {

  public static void main(String[] args) throws IOException {
    reportSyntaxErrors(Path.of("broken.toml"));
    System.out.println();
    checkLimits(Path.of("limits.toml"));
  }

  private static void reportSyntaxErrors(Path file) throws IOException {
    TomlParseResult result = Toml.parse(file);

    // The parser records each error and continues at the next line, so one parse finds every error in the file. Errors
    // in the syntax are listed before errors such as a key defined twice, so sort them by position.
    List<TomlParseError> errors = new ArrayList<>(result.errors());
    Comparator<TomlParseError> byLine = Comparator.comparingInt(error -> error.position().line());
    errors.sort(byLine.thenComparingInt(error -> error.position().column()));
    System.out.println(file + ": " + errors.size() + " errors");
    for (TomlParseError error : errors) {
      TomlPosition position = error.position();
      System.out.println(file + ":" + position.line() + ":" + position.column() + ": " + error.getMessage());
    }

    // What parsed without error is still in the result.
    System.out.println("name is still readable: " + result.getString("name"));
  }

  private static void checkLimits(Path file) throws IOException {
    TomlParseResult result = Toml.parse(file);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(file + ": " + error));
      System.exit(1);
    }

    // A value's position in the file lets a check of its own point at it, as the parser's errors do.
    long port = result.getLong("server.port");
    if (port < 1 || port > 65535) {
      report(file, result, "server.port", "port " + port + " is not between 1 and 65535");
    }
    long timeout = result.getLong("client.timeout");
    if (timeout < 0) {
      report(file, result, "client.timeout", "timeout " + timeout + " is negative");
    }
  }

  private static void report(Path file, TomlParseResult result, String key, String message) {
    TomlPosition position = result.inputPositionOf(key);
    System.out.println(file + ":" + position.line() + ":" + position.column() + ": " + message);
  }
}
