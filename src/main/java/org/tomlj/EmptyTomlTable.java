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
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class EmptyTomlTable implements TomlTable {

  static final TomlTable EMPTY_TABLE = new EmptyTomlTable();

  private EmptyTomlTable() {}

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public Set<String> keySet() {
    return Collections.emptySet();
  }

  @Override
  public Set<List<String>> keyPathSet(boolean includeTables) {
    return Collections.emptySet();
  }

  @Override
  public Set<Map.Entry<String, Object>> entrySet() {
    return Collections.emptySet();
  }

  @Override
  public Set<Map.Entry<List<String>, Object>> entryPathSet(boolean includeTables) {
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public Object get(List<String> path) {
    return null;
  }

  @Nullable
  @Override
  public TomlPosition inputPositionOf(List<String> path) {
    return null;
  }

  @Override
  public Map<String, Object> toMap() {
    return Collections.emptyMap();
  }
}
