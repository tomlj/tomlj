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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.CodeSource;

import org.junit.jupiter.api.Test;

/** Runs against the shaded jar, with no ANTLR runtime on the classpath. */
class ShadedJarTest {

  @Test
  void parsesWithTheBundledRuntime() {
    TomlParseResult result = Toml.parse("port = 8080\nname = \"server\"\n");
    assertFalse(result.hasErrors());
    assertEquals(Long.valueOf(8080), result.getLong("port"));
    assertEquals("server", result.getString("name"));
  }

  @Test
  void reportsErrorsWithTheBundledRuntime() {
    TomlParseResult result = Toml.parse("deps = [\n  \"a\",\n  \"b\"\n");
    assertEquals(1, result.errors().size());
    assertEquals(
        "Unexpected end of input, expected ] or a comma; the array opened at line 1, column 8 is unclosed",
        result.errors().get(0).getMessage());
  }

  @Test
  void bundledRuntimeIsRelocatedIntoTheJar() throws Exception {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.antlr.v4.runtime.Parser"));
    Class<?> relocated = Class.forName("org.tomlj.internal.antlr.v4.runtime.Parser");
    CodeSource jar = Toml.class.getProtectionDomain().getCodeSource();
    assertEquals(jar.getLocation(), relocated.getProtectionDomain().getCodeSource().getLocation());
  }
}
