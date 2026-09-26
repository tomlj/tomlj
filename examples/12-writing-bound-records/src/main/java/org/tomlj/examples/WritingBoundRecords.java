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
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds a document to records, changes them, and writes the change back into the document, keeping its comments and the
 * way each unchanged value is written. Then writes new records as a new document.
 */
public final class WritingBoundRecords {

  record Deployment(String name, int replicas, Duration timeout, List<Integer> ports, List<Service> service) {}

  record Service(String name, String image, int memory) {}

  private static final Pattern DURATION = Pattern.compile("(\\d+)s");

  // Durations are written as a number of seconds, such as "30s". The second function writes them, and is needed to
  // write a Duration at all.
  private static final TomlBindOptions OPTIONS = TomlBindOptions
      .defaults()
      .withConverter(Duration.class, WritingBoundRecords::parseDuration, duration -> duration.toSeconds() + "s");

  public static void main(String[] args) throws IOException {
    Path file = Path.of(args.length > 0 ? args[0] : "deployment.toml");
    TomlParseResult document = Toml.parse(file);
    if (document.hasErrors()) {
      document.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
    }

    Deployment deployment;
    try {
      deployment = document.as(Deployment.class, OPTIONS);
    } catch (TomlBindException e) {
      e.errors().forEach(error -> System.err.println(error.toString()));
      System.exit(1);
      return;
    }

    // Change the records: more replicas, a new image for the web service, and a third service.
    List<Service> services = new ArrayList<>();
    for (Service service : deployment.service()) {
      if (service.name().equals("web")) {
        services.add(new Service("web", "registry.example.com/web:1.5", service.memory()));
      } else {
        services.add(service);
      }
    }
    services.add(new Service("cache", "registry.example.com/cache:7.2", 256));
    Deployment changed = new Deployment(deployment.name(), 5, deployment.timeout(), deployment.ports(), services);

    // update changes only the values that differ. The others keep the way they are written, such as 0x200 and "30s",
    // and every comment stays where it was.
    document.update(changed, OPTIONS);
    String updated = document.toToml();
    System.out.println(updated);

    // A new document is written from records alone, in the default layout.
    Deployment staging = new Deployment(
        "storefront-staging",
        1,
        Duration.ofSeconds(45),
        List.of(8080),
        List.of(new Service("web", "registry.example.com/web:1.5", 512)));
    String written = Toml.toToml(staging, OPTIONS);
    System.out.println(written);
  }

  private static Duration parseDuration(Object value) {
    Matcher matcher = DURATION.matcher((String) value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("expected a number of seconds, such as \"30s\"");
    }
    return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
  }
}
