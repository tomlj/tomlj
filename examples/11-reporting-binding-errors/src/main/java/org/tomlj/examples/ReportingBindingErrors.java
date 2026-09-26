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
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Binds a file with mistakes in it, and lists every value that could not be bound with its position.
 */
public final class ReportingBindingErrors {

  record Pipeline(String name, LocalTime start, int retries, List<Step> step) {}

  // A record's constructor can check what it is given. An exception it throws is reported like any other error, at
  // the table the record was bound from.
  record Step(String name, String command, @Nullable When when, @Nullable Integer timeout) {
    Step {
      if (timeout != null && timeout <= 0) {
        throw new IllegalArgumentException("timeout must be positive, but is " + timeout);
      }
    }
  }

  enum When {
    ALWAYS, ON_SUCCESS, ON_FAILURE
  }

  public static void main(String[] args) throws IOException {
    bind(Path.of("pipeline.toml"));
    System.out.println();

    // fixed.toml corrects each of those errors. A record's constructor is only called once all of its values are
    // bound, so the check in Step's constructor is reported only now.
    bind(Path.of("fixed.toml"));
  }

  private static void bind(Path file) throws IOException {
    TomlParseResult result = Toml.parse(file);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    // Binding does not stop at the first error: every value is bound, and the errors are thrown together, in the order
    // of the document.
    try {
      Pipeline pipeline = result.as(Pipeline.class);
      System.out.println(file + ": " + pipeline.step().size() + " steps");
    } catch (TomlBindException e) {
      // Each error prints as its path, what is wrong, and its position. path(), message() and position() give the
      // parts, for a program that reports errors in its own format.
      System.out.println(file + " could not be bound:");
      e.errors().forEach(error -> System.out.println("  " + error));
    }
  }
}
