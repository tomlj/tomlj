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

import java.util.EnumSet;

/**
 * Options for serializing TOML to JSON.
 * <p>
 * These options are suitable for use with `toJson` methods, e.g. {@link TomlTable#toJson(JsonOptions...)},
 * {@link TomlArray#toJson(JsonOptions...)}.
 */
public enum JsonOptions {
  /**
   * Output values as objects including a "type" and "value" property. E.g.:
   * <p>
   * {@code { "type": "string", "value": "hello world" }}
   */
  VALUES_AS_OBJECTS_WITH_TYPE,
  /**
   * Output all values as strings, rather than using integer, float or boolean values.
   */
  ALL_VALUES_AS_STRINGS;

  static EnumSet<JsonOptions> setFrom(JsonOptions[] options) {
    return options.length > 0 ? EnumSet.of(options[0], options) : EnumSet.noneOf(JsonOptions.class);
  }
}
