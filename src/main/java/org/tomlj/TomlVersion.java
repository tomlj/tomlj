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

import javax.annotation.Nullable;

/**
 * Supported TOML specification versions.
 */
public enum TomlVersion {
  /**
   * The 0.4.0 version of TOML.
   *
   * <p>
   * This specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.4.0.md">https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.4.0.md</a>.
   */
  V0_4_0(null),
  /**
   * The 0.5.0 version of TOML.
   *
   * <p>
   * This specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.5.0.md">https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.5.0.md</a>.
   */
  V0_5_0(null),
  /**
   * The 1.0.0-rc.1 version of TOML.
   *
   * <p>
   * This specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/e597958af54a9230a872fdab3e538490e8758eb0/versions/en/toml-v1.0.0-rc.1.md">https://github.com/toml-lang/toml/blob/e597958af54a9230a872fdab3e538490e8758eb0/versions/en/toml-v1.0.0-rc.1.md</a>.
   */
  V1_0_0_RC1(null),
  /**
   * The latest stable specification of TOML.
   */
  LATEST(V1_0_0_RC1),
  /**
   * The head (development) specification of TOML.
   *
   * <p>
   * The latest specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/README.md">https://github.com/toml-lang/toml/blob/master/README.md</a>.
   *
   * <p>
   * Note: As the specification is under active development, this implementation may not match the latest changes.
   */
  HEAD(null);

  final TomlVersion canonical;

  TomlVersion(@Nullable TomlVersion canonical) {
    this.canonical = canonical != null ? canonical : this;
  }

  boolean after(TomlVersion other) {
    return this.ordinal() > other.ordinal();
  }
}
