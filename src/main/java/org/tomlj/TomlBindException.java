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
 * Thrown when a table or array cannot be bound to a Java type.
 *
 * <p>
 * Binding does not stop at the first error: the exception is thrown once every value has been bound, and holds every
 * error found, in document order.
 *
 * @see TomlTable#as(Class)
 */
public final class TomlBindException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * The errors found.
   */
  @SuppressWarnings("serial")
  private final List<TomlBindError> errors;

  TomlBindException(List<TomlBindError> errors) {
    super(errors.stream().map(TomlBindError::toString).collect(Collectors.joining("\n")));
    this.errors = Collections.unmodifiableList(errors);
  }

  /**
   * The errors found.
   *
   * @return The errors, in document order, with errors that have no position last. Never empty.
   */
  public List<TomlBindError> errors() {
    return errors;
  }
}
