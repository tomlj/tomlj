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

import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * A key/value pair written in a table.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface TomlKeyValue extends TomlElement {

  /**
   * The key of this pair.
   *
   * <p>
   * This is the single key in its table, not a dotted path: the raw key as {@link TomlTable#keySet()} returns it.
   *
   * @return The key.
   */
  String key();

  /**
   * The value of this pair.
   *
   * @return The value.
   */
  TomlValue value();

  /**
   * The comments attached to this pair.
   *
   * <p>
   * Returns the comments in document order: the run written directly above the key, if any, then the comment on its
   * line, if any, so at most two, each with its {@link TomlComment#placement()}. These are the comments
   * {@link TomlTable#comments(List)} returns for the key.
   *
   * @return The attached comments, in document order. Unmodifiable.
   */
  List<TomlComment> comments();
}
