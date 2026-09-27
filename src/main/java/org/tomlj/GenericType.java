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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * A generic type to bind a table or array to, such as {@code Map<String, Server>} or {@code List<Server>}.
 *
 * <p>
 * A {@code Class} cannot name a generic type, so a {@code GenericType} is created as an anonymous subclass that names
 * it as its type argument:
 *
 * <pre>{@code
 * Map<String, Server> servers = table.as(new GenericType<Map<String, Server>>() {});
 * }</pre>
 *
 * <p>
 * A class between the subclass and {@code GenericType} is allowed. Its type variables are replaced with the type
 * arguments the subclass gives it, so with {@code abstract class ServerMap<V> extends GenericType<Map<String, V>>},
 * {@code new ServerMap<Server>() {}} names {@code Map<String, Server>}.
 *
 * @param <T> The type to bind to.
 * @see TomlTable#as(GenericType)
 * @see TomlArray#as(GenericType)
 */
public abstract class GenericType<T> {

  private final Type type;

  /**
   * Create a generic type that names the type argument given to {@code GenericType} by the subclass, resolved through
   * any class between it and {@code GenericType}.
   *
   * @throws IllegalStateException If no type argument is given to {@code GenericType}.
   */
  protected GenericType() {
    Type supertype = JavaTypes.supertype(getClass(), GenericType.class);
    Type argument =
        (supertype instanceof ParameterizedType) ? ((ParameterizedType) supertype).getActualTypeArguments()[0] : null;
    if (argument == null) {
      throw new IllegalStateException(
          "A GenericType must be created with a type argument, such as new GenericType<List<String>>() {}");
    }
    this.type = argument;
  }

  /**
   * The type named.
   *
   * @return The type argument given to {@code GenericType} by the subclass.
   */
  public final Type type() {
    return type;
  }

  @Override
  public String toString() {
    return "GenericType{" + type.getTypeName() + '}';
  }
}
