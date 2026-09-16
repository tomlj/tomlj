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
package org.tomlj.internal;

import java.util.ArrayList;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The superclass of every rule context of the generated {@link TomlParser}, named by the {@code contextSuperClass}
 * option of {@code TomlParser.g4}.
 *
 * <p>
 * It exists only to size the list that a context keeps its children in. {@link ParserRuleContext} creates that list at
 * {@link ArrayList}'s default capacity of ten, while four rule contexts in five hold a single child: parsing a 4.25 MB
 * configuration file left 12.29 million of its slots unused, 49 MB of the 460 MB the parse allocated.
 */
public abstract class AbstractTomlParserRuleContext extends ParserRuleContext {

  public AbstractTomlParserRuleContext() {}

  public AbstractTomlParserRuleContext(ParserRuleContext parent, int invokingStateNumber) {
    super(parent, invokingStateNumber);
  }

  @Override
  public <T extends ParseTree> T addAnyChild(T child) {
    if (children == null) {
      // Two rather than one: an array of one reference and an array of two are the same size once the object header is
      // padded, so the second slot costs nothing and spares the contexts holding a pair a copy into a larger array.
      children = new ArrayList<>(2);
    }
    children.add(child);
    return child;
  }
}
