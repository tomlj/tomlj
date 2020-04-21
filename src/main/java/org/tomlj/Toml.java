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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

/**
 * Methods for parsing data stored in Tom's Obvious, Minimal Language (TOML).
 */
public final class Toml {
	private static final Pattern simpleKeyPattern = Pattern.compile("^[A-Za-z0-9_-]+$");

	private Toml() {}

	/**
	 * Parse a TOML string.
	 *
	 * @param input The input to parse.
	 * @return The parse result. Continues parse even if error.
	 */
	public static TomlParseResult parse(String input) {
		return parse(input, TomlVersion.LATEST, false);
	}

	/**
	 * Parse a TOML string.
	 *
	 * @param input  The input to parse.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result. Continues parse even if error.
	 */
	public static TomlParseResult parse(String input, boolean throwiferror) {
		return parse(input, TomlVersion.LATEST, throwiferror);
	}

	/**
	 * Parse a TOML string.
	 *
	 * @param input The input to parse.
	 * @param version The version level to parse at.
	 * @return The parse result. Continues parse even if error.
	 */
	public static TomlParseResult parse(String input, TomlVersion version) {
		CharStream stream = CharStreams.fromString(input);
		return Parser.parse(stream, version.canonical, false);
	}

	/**
	 * Parse a TOML string.
	 *
	 * @param input The input to parse.
	 * @param version The version level to parse at.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 */
	public static TomlParseResult parse(String input, TomlVersion version, boolean throwiferror) {
		CharStream stream = CharStreams.fromString(input);
		return Parser.parse(stream, version.canonical, throwiferror);
	}

	/**
	 * Parse a TOML file.
	 *
	 * @param file The input file to parse.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(Path file) throws IOException {
		return parse(file, TomlVersion.LATEST, false);
	}

	/**
	 * Parse a TOML file.
	 *
	 * @param file The input file to parse.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(Path file, boolean throwiferror) throws IOException {
		return parse(file, TomlVersion.LATEST, throwiferror);
	}

	/**
	 * Parse a TOML file.
	 *
	 * @param file The input file to parse.
	 * @param version The version level to parse at.
	 * @return The parse result.
	 * @throws IOExceptio If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(Path file, TomlVersion version) throws IOException {
		CharStream stream = CharStreams.fromPath(file);
		return Parser.parse(stream, version.canonical, false);
	}

	/**
	 * Parse a TOML file.
	 *
	 * @param file The input file to parse.
	 * @param version The version level to parse at.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(Path file, TomlVersion version, boolean throwiferror) throws IOException {
		CharStream stream = CharStreams.fromPath(file);
		return Parser.parse(stream, version.canonical, throwiferror);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param is The input stream to read the TOML document from.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(InputStream is) throws IOException {
		return parse(is, TomlVersion.LATEST, false);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param is The input stream to read the TOML document from.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(InputStream is, boolean throwiferror) throws IOException {
		return parse(is, TomlVersion.LATEST, throwiferror);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param is The input stream to read the TOML document from.
	 * @param version The version level to parse at.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(InputStream is, TomlVersion version) throws IOException {
		CharStream stream = CharStreams.fromStream(is);
		return Parser.parse(stream, version.canonical, false);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param is The input stream to read the TOML document from.
	 * @param version The version level to parse at.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(InputStream is, TomlVersion version, boolean throwiferror) throws IOException {
		CharStream stream = CharStreams.fromStream(is);
		return Parser.parse(stream, version.canonical, throwiferror);
	}

	/**
	 * Parse a TOML reader.
	 *
	 * @param reader The reader to obtain the TOML document from.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(Reader reader) throws IOException {
		return parse(reader, TomlVersion.LATEST, false);
	}

	/**
	 * Parse a TOML reader.
	 *
	 * @param reader The reader to obtain the TOML document from.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(Reader reader, boolean throwiferror) throws IOException {
		return parse(reader, TomlVersion.LATEST, throwiferror);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param reader The reader to obtain the TOML document from.
	 * @param version The version level to parse at.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(Reader reader, TomlVersion version) throws IOException {
		CharStream stream = CharStreams.fromReader(reader);
		return Parser.parse(stream, version.canonical, false);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param reader The reader to obtain the TOML document from.
	 * @param version The version level to parse at.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(Reader reader, TomlVersion version, boolean throwiferror) throws IOException {
		CharStream stream = CharStreams.fromReader(reader);
		return Parser.parse(stream, version.canonical, throwiferror);
	}

	/**
	 * Parse a TOML reader.
	 *
	 * @param channel The channel to read the TOML document from.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(ReadableByteChannel channel) throws IOException {
		return parse(channel, TomlVersion.LATEST, false);
	}

	/**
	 * Parse a TOML reader.
	 *
	 * @param channel The channel to read the TOML document from.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(ReadableByteChannel channel, boolean throwiferror) throws IOException {
		return parse(channel, TomlVersion.LATEST, throwiferror);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param channel The channel to read the TOML document from.
	 * @param version The version level to parse at.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs. Continues parse even if error.
	 */
	public static TomlParseResult parse(ReadableByteChannel channel, TomlVersion version) throws IOException {
		CharStream stream = CharStreams.fromChannel(channel);
		return Parser.parse(stream, version.canonical, false);
	}

	/**
	 * Parse a TOML input stream.
	 *
	 * @param channel The channel to read the TOML document from.
	 * @param version The version level to parse at.
	 * @param throwiferror To throw error immediately while parsing.
	 * @return The parse result.
	 * @throws IOException If an IO error occurs.
	 */
	public static TomlParseResult parse(ReadableByteChannel channel, TomlVersion version, boolean throwiferror)	throws IOException {
		CharStream stream = CharStreams.fromChannel(channel);
		return Parser.parse(stream, version.canonical, throwiferror);
	}

	/**
	 * Parse a dotted key into individual parts.
	 *
	 * @param dottedKey A dotted key (e.g. {@code server.address.port}).
	 * @return A list of individual keys in the path.
	 * @throws IllegalArgumentException If the dotted key cannot be parsed.
	 */
	public static List<String> parseDottedKey(String dottedKey) {
		requireNonNull(dottedKey);
		return Parser.parseDottedKey(dottedKey);
	}

	/**
	 * Join a list of keys into a single dotted key string.
	 *
	 * @param path The list of keys that form the path.
	 * @return The path string.
	 */
	public static String joinKeyPath(List<String> path) {
		requireNonNull(path);

		StringJoiner joiner = new StringJoiner(".");
		for (String key : path) {
			if (simpleKeyPattern.matcher(key).matches()) {
				joiner.add(key);
			} else {
				joiner.add("\"" + tomlEscape(key) + '\"');
			}
		}
		return joiner.toString();
	}

	/**
	 * Get the canonical form of the dotted key.
	 *
	 * @param dottedKey A dotted key (e.g. {@code server.address.port}).
	 * @return The canonical form of the dotted key.
	 * @throws IllegalArgumentException If the dotted key cannot be parsed.
	 */
	public static String canonicalDottedKey(String dottedKey) {
		return joinKeyPath(parseDottedKey(dottedKey));
	}

	/**
	 * Escape a text string using the TOML escape sequences.
	 *
	 * @param text The text string to escape.
	 * @return A {@link StringBuilder} holding the results of escaping the text.
	 */
	public static StringBuilder tomlEscape(String text) {
		final StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			int codepoint = text.codePointAt(i);
			if (Character.charCount(codepoint) > 1) {
				out.append("\\U").append(String.format("%08x", codepoint));
				++i;
				continue;
			}

			char ch = Character.toChars(codepoint)[0];
			if (ch == '\'') {
				out.append("\\'");
				continue;
			}
			if (ch >= 0x20 && ch < 0x7F) {
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
				out.append("\\u").append(String.format("%04x", codepoint));
			}
		}
		return out;
	}
}
