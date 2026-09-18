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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * An entry of a table or array: the slot a container indexes, with the value it holds and the comments attached to it.
 *
 * <p>
 * A table's entries are {@link TomlKeyValue}s; an array's entries are plain {@code TomlEntry}s. The other kind of
 * element a container's {@code elements()} holds is an unattached {@link TomlComment}, which is not an entry.
 */
@DefaultQualifier(value = NonNull.class ,
    locations = {TypeUseLocation.RETURN, TypeUseLocation.PARAMETER, TypeUseLocation.FIELD})
public interface TomlEntry extends TomlElement {

  /**
   * The value this entry holds.
   *
   * @return The value.
   */
  TomlValue value();

  /**
   * The comments attached to this entry.
   *
   * <p>
   * Returns the comments in document order: the run written directly above it, if any, then the comment on its line, if
   * any, so at most two, each with its {@link TomlComment#placement()}. These are the comments
   * {@link TomlTable#comments(List)} returns for a key and {@link TomlArray#comments(int)} for an index.
   *
   * @return The attached comments, in document order. Unmodifiable.
   */
  List<TomlComment> comments();

  /**
   * The comment attached to this entry at a placement.
   *
   * @param placement {@link TomlComment.Placement#ABOVE} for the run written directly above this entry, or
   *        {@link TomlComment.Placement#AFTER} for the comment on its line.
   * @return The comment at that placement, or {@code null} if this entry has none there.
   * @throws NullPointerException If {@code placement} is {@code null}.
   * @throws IllegalArgumentException If {@code placement} is {@link TomlComment.Placement#UNATTACHED}, since no entry
   *         holds an unattached comment.
   */
  @Nullable
  default TomlComment comment(TomlComment.Placement placement) {
    TomlComment.requireAttached(placement);
    for (TomlComment comment : comments()) {
      if (comment.placement() == placement) {
        return comment;
      }
    }
    return null;
  }
}
