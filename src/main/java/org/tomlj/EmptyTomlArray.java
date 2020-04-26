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

final class EmptyTomlArray implements TomlArray {

  static final TomlArray EMPTY_ARRAY = new EmptyTomlArray();

  private EmptyTomlArray() {}

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public boolean containsStrings() {
    return false;
  }

  @Override
  public boolean containsLongs() {
    return false;
  }

  @Override
  public boolean containsDoubles() {
    return false;
  }

  @Override
  public boolean containsBooleans() {
    return false;
  }

  @Override
  public boolean containsOffsetDateTimes() {
    return false;
  }

  @Override
  public boolean containsLocalDateTimes() {
    return false;
  }

  @Override
  public boolean containsLocalDates() {
    return false;
  }

  @Override
  public boolean containsLocalTimes() {
    return false;
  }

  @Override
  public boolean containsArrays() {
    return false;
  }

  @Override
  public boolean containsTables() {
    return false;
  }

  @Override
  public Object get(int index) {
    throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
  }

  @Override
  public TomlPosition inputPositionOf(int index) {
    throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
  }

  @Override
  public List<Object> toList() {
    return Collections.emptyList();
  }
}
