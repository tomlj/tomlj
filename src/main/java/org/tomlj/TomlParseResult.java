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
 * The result from parsing a TOML document.
 *
 * <p>
 * Parsing never throws for invalid input. Instead, every error is recorded in {@link #errors()} and parsing continues
 * with the next expression, so a document with errors still yields the values that could be parsed. Callers should
 * check {@link #hasErrors()} before relying on the result. A key/value pair containing a syntax error is omitted from
 * the result.
 */
public interface TomlParseResult extends TomlTable {

  /**
   * {@code true} if the TOML document contained errors.
   *
   * @return {@code true} if the TOML document contained errors.
   */
  default boolean hasErrors() {
    return !(errors().isEmpty());
  }

  /**
   * The errors that occurred during parsing.
   *
   * @return A list of errors.
   */
  List<TomlParseError> errors();
}
