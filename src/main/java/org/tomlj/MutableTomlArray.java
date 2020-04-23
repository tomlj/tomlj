/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class MutableTomlArray implements TomlArray {

	private static class Element {
		final Object value;
		final TomlPosition position;

		private Element(Object value, TomlPosition position) {
			this.value = value;
			this.position = position;
		}
	}

	static final TomlArray EMPTY = new MutableTomlArray(true);
	private final List<Element> elements = new ArrayList<>();
	private final boolean definedAsLiteral;
	private TomlType type = null;

	MutableTomlArray() {
		this(false);
	}

	MutableTomlArray(boolean definedAsLiteral) {
		this.definedAsLiteral = definedAsLiteral;
	}

	boolean wasDefinedAsLiteral() {
		return definedAsLiteral;
	}

	@Override
	public int size() {
		return elements.size();
	}

	@Override
	public boolean isEmpty() {
		return type == null;
	}

	@Override
	public boolean containsStrings() {
		if(iteratearray().contains("STRING")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsLongs() {
		if(iteratearray().contains("INTEGER")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsDoubles() {
		if(iteratearray().contains("FLOAT")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsBooleans() {
		if(iteratearray().contains("BOOLEAN")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsOffsetDateTimes() {
		if(iteratearray().contains("OFFSET DATE-TIME")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsLocalDateTimes() {
		if(iteratearray().contains("LOCAL DATE-TIME")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsLocalDates() {
		if(iteratearray().contains("LOCAL DATE")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsLocalTimes() {
		if(iteratearray().contains("LOCAL TIME")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsArrays() {
		if(iteratearray().contains("ARRAY")) {
			return true;
		}else {
			return false;
		}
	}

	@Override
	public boolean containsTables() {
		if(iteratearray().contains("TABLE")) {
			return true;
		}else {
			return false;
		}
	}

	public String iteratearray() {
		String TypeofElment = "";
		for (int index = 0; index < elements.size(); index++) {
			TypeofElment = TypeofElment + "[" + (TomlType.typeNameFor(elements.get(index).value).toUpperCase()) + "]";
		}
		return TypeofElment;
	}
	
	@Override
	public Object get(int index) {
		return elements.get(index).value;
	}

	@Override
	public TomlPosition inputPositionOf(int index) {
		return elements.get(index).position;
	}

	MutableTomlArray append(Object value, TomlPosition position) {
		requireNonNull(value);
		if (value instanceof Integer) {
			value = ((Integer) value).longValue();
		}

		TomlType origType = type;
		Optional<TomlType> valueType = TomlType.typeFor(value);
		if (!valueType.isPresent()) {
			throw new IllegalArgumentException("Unsupported type " + value.getClass().getSimpleName());
		}
		if (type != null) {
//			 if (valueType.get() != type) {
//			 throw new TomlInvalidTypeException(
//			 "Cannot add a " + TomlType.typeNameFor(value) + " to an array containing " + type.typeName() + "s");
//			 }
		} else {
			type = valueType.get();
		}

		try {
			elements.add(new Element(value, position));
		} catch (Throwable e) {
			type = origType;
			throw e;
		}
		return this;
	}

	@Override
	public List<Object> toList() {
		return elements.stream().map(e -> e.value).collect(Collectors.toList());
	}
}
