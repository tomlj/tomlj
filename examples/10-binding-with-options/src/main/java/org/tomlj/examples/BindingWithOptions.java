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
import org.tomlj.TomlBindOptions;
import org.tomlj.TomlBindOptions.KeyNaming;
import org.tomlj.TomlName;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds a configuration file to a class whose fields hold the defaults, with kebab-case keys, a renamed key, and
 * converters for types TomlJ does not bind itself.
 */
public final class BindingWithOptions {

  // A class is created with its constructor without parameters, then a field is set for each key of the table. A
  // field whose key the document does not have keeps the value it was initialized with.
  static final class ServerSettings {
    private String listenAddress = "127.0.0.1";
    private int port = 80;
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration idleTimeout = Duration.ofMinutes(1);
    private Path documentRoot = Path.of("public");
    private List<String> aliases = List.of();

    // @TomlName gives the key itself, in place of the key naming of the options.
    @TomlName("trust-x-forwarded-for")
    private boolean trustProxy;

    // Neither this class nor its package is null-marked, so a field without an initializer may be missing, and is
    // then null.
    private Tls tls;
  }

  static final class Tls {
    private Path certFile;
    private Path keyFile;
  }

  public static void main(String[] args) throws IOException {
    Path file = Path.of(args.length > 0 ? args[0] : "server.toml");
    TomlParseResult result = Toml.parse(file);
    if (result.hasErrors()) {
      result.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    // Field names are camel case and the keys are kebab case: listenAddress is bound to listen-address. TomlJ binds
    // neither Duration nor Path, so each has a converter, given the value as it is in the document.
    TomlBindOptions options = TomlBindOptions
        .defaults()
        .withKeyNaming(KeyNaming.KEBAB_CASE)
        .withConverter(Duration.class, BindingWithOptions::parseDuration)
        .withConverter(Path.class, value -> Path.of((String) value));

    // A key that names no field is an error, and the [metrics] table is not for the server.
    try {
      result.as(ServerSettings.class, options);
    } catch (TomlBindException e) {
      System.out.println("with the default options:");
      e.errors().forEach(error -> System.out.println("  " + error));
    }

    // With unknown keys ignored, [metrics] is left for the program it is for.
    TomlBindOptions ignoringUnknownKeys = options.withUnknownKeysIgnored(true);
    try {
      ServerSettings settings = result.as(ServerSettings.class, ignoringUnknownKeys);
      System.out.println("listen on " + settings.listenAddress + ":" + settings.port);
      System.out.println("request timeout " + settings.requestTimeout + ", idle timeout " + settings.idleTimeout);
      System.out.println("serve " + settings.documentRoot + " for " + settings.aliases.size() + " aliases");
      System.out.println("trust X-Forwarded-For: " + settings.trustProxy);
      System.out
          .println("TLS: " + (settings.tls == null ? "off" : settings.tls.certFile + ", " + settings.tls.keyFile));
    } catch (TomlBindException e) {
      e.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }
  }

  private static final Pattern DURATION = Pattern.compile("(\\d+)(ms|s|m|h)");

  // A converter's exception is reported as a binding error at the value, with the exception's message.
  private static Duration parseDuration(Object value) {
    Matcher matcher = DURATION.matcher(value instanceof String text ? text : "");
    if (!matcher.matches()) {
      throw new IllegalArgumentException("expected a duration such as \"30s\", \"5m\" or \"250ms\"");
    }
    long amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      default -> Duration.ofHours(amount);
    };
  }
}
