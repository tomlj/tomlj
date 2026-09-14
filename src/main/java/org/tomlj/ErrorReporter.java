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

interface ErrorReporter {
  void reportError(TomlParseError error);

  /**
   * Check whether the parser reported a syntax error within a range of lines.
   *
   * @param firstLine The first line of the range (inclusive).
   * @param lastLine The last line of the range (inclusive).
   * @return {@code true} if a syntax error was reported on any line in the range.
   */
  boolean hasSyntaxErrorBetween(int firstLine, int lastLine);
}
