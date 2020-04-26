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
import static org.tomlj.TomlType.typeFor;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

final class JsonSerializer {
	private JsonSerializer() {
	}

	static void toJson(TomlTable table, Appendable appendable, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		requireNonNull(table);
		requireNonNull(appendable);
		toJson(table, appendable, 0, optionalIndent);
		if(optIndent > 0) {appendable.append(System.lineSeparator());}
	}

	private static void toJson(TomlTable table, Appendable appendable, int indent, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		if (table.isEmpty()) {
			appendable.append("{}");
			return;
		}
		appendLine(appendable, "{", optIndent);
		for (Iterator<Map.Entry<String, Object>> iterator = table.entrySet().stream().iterator(); iterator.hasNext();) {
			Map.Entry<String, Object> entry = iterator.next();
			String key = entry.getKey();
			append(appendable, indent + optIndent , "\"" + escape(key) + "\": ");
			Object value = entry.getValue();
			assert value != null;
			appendTomlValue(value, appendable, indent, optionalIndent);
			if (iterator.hasNext()) {
				appendable.append(",");
				if(optIndent > 0) {appendable.append(System.lineSeparator());}
			}
		}
		if(optIndent > 0) {appendable.append(System.lineSeparator());}
		append(appendable, indent, "}");
	}

	static void toJson(TomlArray array, Appendable appendable, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		toJson(array, appendable, 0, optionalIndent);
		if(optIndent > 0) {appendable.append(System.lineSeparator());}
	}

	private static void toJson(TomlArray array, Appendable appendable, int indent, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		if (array.isEmpty()) {
			appendable.append("[]");
			return;
		}
		if (array.containsTables()) {
			append(appendable, 0, "[");
			for (Iterator<Object> iterator = array.toList().iterator(); iterator.hasNext();) {
				toJson((TomlTable) iterator.next(), appendable, indent, optionalIndent);
				if (iterator.hasNext()) {
					appendable.append(",");
				}
			}
			append(appendable, 0, "]");
		} else {
			appendLine(appendable, "[", optIndent);
			for (Iterator<Object> iterator = array.toList().iterator(); iterator.hasNext();) {
				indentLine(appendable, indent + optIndent);
				appendTomlValue(iterator.next(), appendable, indent, optionalIndent);
				if (iterator.hasNext()) {
					appendable.append(",");
					if(optIndent > 0) {appendable.append(System.lineSeparator());}
				}
			}
			if(optIndent > 0) {appendable.append(System.lineSeparator());}
			append(appendable, indent, "]");
		}
	}

	private static void appendTomlValue(Object value, Appendable appendable, int indent, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		Optional<TomlType> tomlType = typeFor(value);
		assert tomlType.isPresent();
		switch (tomlType.get()) {
		case STRING:
			append(appendable, 0, "\"" + escape((String) value) + "\"");
			break;
		case INTEGER:
		case FLOAT:
			append(appendable, 0, value.toString());
			break;
		case BOOLEAN:
			append(appendable, 0, ((Boolean) value) ? "true" : "false");
			break;
		case OFFSET_DATE_TIME:
		case LOCAL_DATE_TIME:
		case LOCAL_DATE:
		case LOCAL_TIME:
			append(appendable, 0, "\"" + value.toString() + "\"");
			break;
		case ARRAY:
			toJson((TomlArray) value, appendable, indent + optIndent, optionalIndent);
			break;
		case TABLE:
			toJson((TomlTable) value, appendable, indent + optIndent, optionalIndent);
			break;
		}
	}

	private static void append(Appendable appendable, int indent, String line) throws IOException {
		indentLine(appendable, indent);
		appendable.append(line);
	}

	private static void appendLine(Appendable appendable, String line, Integer... optionalIndent) throws IOException {
		Integer optIndent = ((optionalIndent.length > 0) ? (Integer)optionalIndent[0] : 2);
		appendable.append(line);
		if(optIndent > 0) {appendable.append(System.lineSeparator());}
	}

	private static void indentLine(Appendable appendable, int indent) throws IOException {
		for (int i = 0; i < indent; ++i) {
			appendable.append(' ');
		}
	}

	private static StringBuilder escape(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '"') {
				out.append("\\\"");
				continue;
			}
			if (ch == '\\') {
				out.append("\\\\");
				continue;
			}
			if (ch >= 0x20) {
				out.append(ch);
				continue;
			}

			switch (ch) {
			case '\t':
				out.append("\\t");
				break;
			case '\b':
				out.append("\\b");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\f':
				out.append("\\f");
				break;
			default:
				out.append("\\u").append(String.format("%04x", text.codePointAt(i)));
			}
		}
		return out;
	}
}
