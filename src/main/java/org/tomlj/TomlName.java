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
 * The name in TOML of a field, record component or enum constant, for {@link TomlTable#as(Class)} and
 * {@link MutableTomlTable#from(Object)}.
 *
 * <p>
 * On a field or component, it is the key the field or component is bound to. Without this annotation the key is the
 * name of the field or component, converted by the {@link TomlBindOptions#withKeyNaming(TomlBindOptions.KeyNaming) key
 * naming} of the options. With it, the key is the annotation's value, and the key naming is not applied.
 *
 * <pre>
 * record Server(&#64;TomlName("host-name") String host, int port) {}
 * </pre>
 *
 * <p>
 * On an enum constant, it is the string the constant is written as and matched by, in place of its name.
 *
 * <pre>
 * enum Trigger {
 *   &#64;TomlName("always")
 *   RUN_ALWAYS, ON_SUCCESS
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TomlName {

  /**
   * The name.
   *
   * @return For a field or component, a single key, not a dotted key. It is used as written, without quoting or
   *         escaping, so {@code @TomlName("a.b")} names the key {@code "a.b"} and not the key {@code b} in the table
   *         {@code a}. For an enum constant, the string.
   */
  String value();
}
