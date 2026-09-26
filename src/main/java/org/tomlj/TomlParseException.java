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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a document to be bound has parse errors.
 *
 * <p>
 * {@link Toml#parseAs(String, Class)} and the other {@code parseAs} methods throw it, and bind nothing, if the document
 * has any parse error. It holds every parse error, in the order of the document.
 *
 * @see Toml#parseAs(String, Class, TomlBindOptions)
 */
public final class TomlParseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * The errors found.
   */
  @SuppressWarnings("serial")
  private final List<TomlParseError> errors;

  TomlParseException(List<TomlParseError> errors) {
    super(errors.stream().map(TomlParseError::toString).collect(Collectors.joining("\n")));
    this.errors = Collections.unmodifiableList(errors);
  }

  /**
   * The errors found.
   *
   * @return The parse errors, in the order of the document. Never empty.
   */
  public List<TomlParseError> errors() {
    return errors;
  }
}
