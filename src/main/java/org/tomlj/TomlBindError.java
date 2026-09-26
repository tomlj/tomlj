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

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A value of a TOML document that could not be bound to its Java type.
 *
 * @see TomlBindException
 */
public final class TomlBindError implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * The path of the value.
   */
  private final String path;
  /**
   * What is wrong with the value.
   */
  private final String message;
  /**
   * The position of the value, if it has one.
   */
  @Nullable
  private final TomlPosition position;
  /**
   * The exception that caused the error, if there was one.
   */
  @Nullable
  private final Throwable cause;

  TomlBindError(String path, String message, @Nullable TomlPosition position) {
    this(path, message, position, null);
  }

  TomlBindError(String path, String message, @Nullable TomlPosition position, @Nullable Throwable cause) {
    requireNonNull(path);
    requireNonNull(message);
    this.path = path;
    this.message = message;
    this.position = position;
    this.cause = cause;
  }

  /**
   * The path of the value within the table or array that was bound.
   *
   * <p>
   * The path is a dotted key, with {@code [i]} after the key of an array for its element at index {@code i}, such as
   * {@code servers[1].host}. It is empty for the table or array that was bound.
   *
   * @return The path of the value.
   */
  public String path() {
    return path;
  }

  /**
   * What is wrong with the value, such as {@code expected an integer, found a string}.
   *
   * @return A description of the error, without the path or position.
   */
  public String message() {
    return message;
  }

  /**
   * The position of the value in the TOML document.
   *
   * <p>
   * For an unknown key, this is the position of the key, and for a missing key, the position of the table the key is
   * missing from: its header, or the start of the document for the root table of a document.
   *
   * @return The position, or {@code null} if the value, or the table for a missing key, was not read from a document.
   */
  @Nullable
  public TomlPosition position() {
    return position;
  }

  /**
   * The exception that caused this error.
   *
   * <p>
   * An exception thrown by a record's constructor or by a converter is reported as an error with its message, and kept
   * here. The cause is not part of the error's equality or its string form.
   *
   * @return The exception thrown, or {@code null} if the error was found by binding itself.
   */
  @Nullable
  public Throwable cause() {
    return cause;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TomlBindError)) {
      return false;
    }
    TomlBindError other = (TomlBindError) obj;
    return path.equals(other.path) && message.equals(other.message) && Objects.equals(position, other.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, message, position);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (!path.isEmpty()) {
      sb.append(path).append(": ");
    }
    sb.append(message);
    if (position != null) {
      sb.append(" (").append(position).append(')');
    }
    return sb.toString();
  }
}
