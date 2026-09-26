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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Operations on generic Java types: erasure, and substituting type arguments for type variables.
 */
final class JavaTypes {
  private JavaTypes() {}

  static Class<?> rawClass(Type type) {
    if (type instanceof Class) {
      return (Class<?>) type;
    }
    if (type instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) type).getRawType();
    }
    if (type instanceof GenericArrayType) {
      return Array.newInstance(rawClass(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
    }
    if (type instanceof TypeVariable) {
      return rawClass(((TypeVariable<?>) type).getBounds()[0]);
    }
    if (type instanceof WildcardType) {
      return rawClass(((WildcardType) type).getUpperBounds()[0]);
    }
    throw new IllegalArgumentException("Unsupported type " + type.getTypeName());
  }

  /**
   * The type arguments of a parameterized type, by the type variable of its class they are given for. Empty for a
   * class.
   */
  static Map<TypeVariable<?>, Type> typeArguments(Type type) {
    if (!(type instanceof ParameterizedType)) {
      return Collections.emptyMap();
    }
    ParameterizedType parameterized = (ParameterizedType) type;
    TypeVariable<?>[] variables = rawClass(type).getTypeParameters();
    Type[] arguments = parameterized.getActualTypeArguments();
    Map<TypeVariable<?>, Type> map = new HashMap<>();
    for (int i = 0; i < variables.length; i++) {
      map.put(variables[i], arguments[i]);
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Substitute the type arguments for the type variables in a type. A wildcard is replaced by its bound: the lower
   * bound if it has one, otherwise the upper bound.
   */
  static Type resolve(Type type, Map<TypeVariable<?>, Type> arguments) {
    if (type instanceof TypeVariable) {
      Type argument = arguments.get(type);
      return argument == null ? type : resolve(argument, Collections.<TypeVariable<?>, Type>emptyMap());
    }
    if (type instanceof WildcardType) {
      WildcardType wildcard = (WildcardType) type;
      Type[] lower = wildcard.getLowerBounds();
      return resolve(lower.length > 0 ? lower[0] : wildcard.getUpperBounds()[0], arguments);
    }
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterized = (ParameterizedType) type;
      Type[] original = parameterized.getActualTypeArguments();
      Type[] resolved = new Type[original.length];
      for (int i = 0; i < original.length; i++) {
        resolved[i] = resolve(original[i], arguments);
      }
      return new ParameterizedTypeImpl((Class<?>) parameterized.getRawType(), resolved, parameterized.getOwnerType());
    }
    if (type instanceof GenericArrayType) {
      Type component = resolve(((GenericArrayType) type).getGenericComponentType(), arguments);
      if (component instanceof Class) {
        return Array.newInstance((Class<?>) component, 0).getClass();
      }
      return new GenericArrayTypeImpl(component);
    }
    return type;
  }

  /**
   * The type a supertype has as seen from a type, such as {@code Collection<String>} for {@code Collection} from
   * {@code ArrayList<String>}.
   *
   * @return The supertype, or {@code null} if {@code target} is not a supertype of {@code type}.
   */
  @Nullable
  static Type supertype(Type type, Class<?> target) {
    Class<?> raw = rawClass(type);
    if (raw == target) {
      return type;
    }
    if (!target.isAssignableFrom(raw)) {
      return null;
    }
    Map<TypeVariable<?>, Type> arguments = typeArguments(type);
    List<Type> supertypes = new ArrayList<>(Arrays.asList(raw.getGenericInterfaces()));
    Type superclass = raw.getGenericSuperclass();
    if (superclass != null) {
      supertypes.add(superclass);
    }
    for (Type supertype : supertypes) {
      Type found = supertype(resolve(supertype, arguments), target);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * A type argument a supertype has as seen from a type, such as {@code String} for the element type of
   * {@code Collection} from {@code ArrayList<String>}; {@code Object} if the supertype is used without arguments.
   */
  static Type typeArgument(Type type, Class<?> target, int index) {
    Type supertype = supertype(type, target);
    if (supertype instanceof ParameterizedType) {
      return ((ParameterizedType) supertype).getActualTypeArguments()[index];
    }
    return Object.class;
  }

  private static final class ParameterizedTypeImpl implements ParameterizedType {
    private final Class<?> raw;
    private final Type[] arguments;
    @Nullable
    private final Type owner;

    ParameterizedTypeImpl(Class<?> raw, Type[] arguments, @Nullable Type owner) {
      this.raw = raw;
      this.arguments = arguments;
      this.owner = owner;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return arguments.clone();
    }

    @Override
    public Type getRawType() {
      return raw;
    }

    @Override
    @Nullable
    public Type getOwnerType() {
      return owner;
    }

    // Equal to, and with the same hash code as, the JDK's own implementation of ParameterizedType
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ParameterizedType)) {
        return false;
      }
      ParameterizedType other = (ParameterizedType) obj;
      return raw.equals(other.getRawType())
          && Objects.equals(owner, other.getOwnerType())
          && Arrays.equals(arguments, other.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(arguments) ^ Objects.hashCode(owner) ^ raw.hashCode();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(raw.getTypeName()).append('<');
      for (int i = 0; i < arguments.length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(arguments[i].getTypeName());
      }
      return sb.append('>').toString();
    }
  }

  private static final class GenericArrayTypeImpl implements GenericArrayType {
    private final Type component;

    GenericArrayTypeImpl(Type component) {
      this.component = component;
    }

    @Override
    public Type getGenericComponentType() {
      return component;
    }

    // Equal to, and with the same hash code as, the JDK's own implementation of GenericArrayType
    @Override
    public boolean equals(Object obj) {
      return obj instanceof GenericArrayType && component.equals(((GenericArrayType) obj).getGenericComponentType());
    }

    @Override
    public int hashCode() {
      return component.hashCode();
    }

    @Override
    public String toString() {
      return component.getTypeName() + "[]";
    }
  }
}
