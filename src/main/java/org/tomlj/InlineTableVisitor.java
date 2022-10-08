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

import static org.tomlj.TomlVersion.V0_5_0;

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.List;

final class InlineTableVisitor extends TomlParserBaseVisitor<MutableTomlTable> {

  private final TomlVersion version;
  private final MutableTomlTable table;

  public InlineTableVisitor(TomlVersion version) {
    this.version = version;
    this.table = new MutableTomlTable(version);
  }

  @Override
  public MutableTomlTable visitKeyval(TomlParser.KeyvalContext ctx) {
    TomlParser.KeyContext keyContext = ctx.key();
    TomlParser.ValContext valContext = ctx.val();
    if (keyContext != null && valContext != null) {
      List<String> path = keyContext.accept(new KeyVisitor(version));
      if (path != null && !path.isEmpty()) {
        Object value = valContext.accept(new ValueVisitor(version));
        if (value != null) {
          table.set(path, value, new TomlPosition(ctx), !version.after(V0_5_0));
        }
      }
    }
    return table;
  }

  @Override
  protected MutableTomlTable aggregateResult(MutableTomlTable aggregate, MutableTomlTable nextResult) {
    return table;
  }

  @Override
  protected MutableTomlTable defaultResult() {
    return table;
  }
}
