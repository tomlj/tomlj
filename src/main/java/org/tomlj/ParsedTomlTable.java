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

import java.util.List;

/**
 * The root table of a parsed document, paired with the errors reported while reading it.
 *
 * <p>
 * A subclass of {@link LinkedTomlTable} rather than a delegator, so that every method added to {@link TomlTable} or
 * {@link MutableTomlTable} is inherited rather than delegated by hand and the result cannot fall out of date.
 */
final class ParsedTomlTable extends LinkedTomlTable implements TomlParseResult {

  private final AccumulatingErrorListener errorListener;

  ParsedTomlTable(AccumulatingErrorListener errorListener) {
    super(TomlPosition.positionAt(1, 1));
    this.errorListener = errorListener;
  }

  @Override
  public List<TomlParseError> errors() {
    return errorListener.errors();
  }
}
