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

import static org.tomlj.ParseTrees.singleTokenText;

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

final class KeyVisitor extends TomlParserBaseVisitor<List<String>> {

  private final TomlVersion version;

  // The text the key is read from, or null when nothing is recorded about where it was written
  private final @Nullable Source source;
  private final List<String> keys = new ArrayList<>();

  KeyVisitor(TomlVersion version, @Nullable Source source) {
    this.version = version;
    this.source = source;
  }

  @Override
  public List<String> visitUnquotedKey(TomlParser.UnquotedKeyContext ctx) {
    keys.add(singleTokenText(ctx));
    return keys;
  }

  @Override
  public List<String> visitQuotedKey(TomlParser.QuotedKeyContext ctx) {
    StringBuilder builder = ctx.accept(new QuotedStringVisitor(version, source));
    keys.add(builder.toString());
    return keys;
  }

  @Override
  protected List<String> aggregateResult(List<String> aggregate, List<String> nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected List<String> defaultResult() {
    return keys;
  }
}
