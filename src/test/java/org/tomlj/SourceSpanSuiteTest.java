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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.tomlj.TomlTestSuiteTest.suiteDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks what a parse records about where each line was written, over every valid document of the official TOML test
 * suite.
 *
 * <p>
 * A valid document has no rejected line, so the text its spans cover is the whole document: writing them out in order,
 * followed by the blank lines the document ends with, must give back the document byte for byte, and the same holds
 * between the brackets of every array and inline table it holds. Until there is a writer to round-trip through, this is
 * what says the spans of a real document fit together.
 */
class SourceSpanSuiteTest {

  @TestFactory
  Stream<DynamicTest> everyValidDocumentIsTheTextOfItsSpans() throws IOException {
    Path suiteDir = suiteDir();
    return Files
        .readAllLines(suiteDir.resolve("files-toml-1.1.0"), UTF_8)
        .stream()
        .filter(file -> file.startsWith("valid/") && file.endsWith(".toml"))
        .map(file -> DynamicTest.dynamicTest(file, () -> checkSpans(suiteDir.resolve(file))));
  }

  private static void checkSpans(Path file) throws IOException {
    String document = new String(Files.readAllBytes(file), UTF_8);
    ParsedTomlTable table = Parser
        .parseTable(CharStreams.fromString(document), TomlParseOptions.defaults(), new AccumulatingErrorListener());
    assertEquals(document, SourceSpanTest.reassemble(table));
    SourceSpanTest.assertContainersReassemble(table);
  }
}
