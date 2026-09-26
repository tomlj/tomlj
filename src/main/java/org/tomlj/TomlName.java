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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The TOML key a field or record component is bound to by {@link TomlTable#as(Class)}.
 *
 * <p>
 * Without this annotation the key is the name of the field or component, converted by the
 * {@link TomlBindOptions#withKeyNaming(TomlBindOptions.KeyNaming) key naming} of the options. With it, the key is the
 * annotation's value, and the key naming is not applied.
 *
 * <pre>
 * record Server(&#64;TomlName("host-name") String host, int port) {}
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TomlName {

  /**
   * The key.
   *
   * @return A single key, not a dotted key. It is used as written, without quoting or escaping, so
   *         {@code @TomlName("a.b")} names the key {@code "a.b"} and not the key {@code b} in the table {@code a}.
   */
  String value();
}
