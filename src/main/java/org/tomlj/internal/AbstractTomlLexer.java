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
package org.tomlj.internal;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.misc.IntegerStack;

/**
 * The superclass of the generated {@link TomlLexer}, holding the code called by the actions in {@code TomlLexer.g4}.
 *
 * <p>
 * Kept out of the grammar so that it is formatted and checked like the rest of the sources, which generated code is
 * not.
 */
public abstract class AbstractTomlLexer extends Lexer {

  // State is made public to allow incremental lexers to save and restore it (e.g. NetBeans)
  public final IntegerStack arrayDepthStack = new IntegerStack();
  public int arrayDepth = 0;

  AbstractTomlLexer(CharStream input) {
    super(input);
  }

  boolean inArray() {
    return arrayDepth > 0;
  }

  void pushValueModeIfInArray() {
    if (inArray()) {
      pushMode(TomlLexer.ValueMode);
    }
  }

  void resetArrayDepth() {
    arrayDepthStack.clear();
    arrayDepth = 0;
  }

  void pushArrayDepth() {
    arrayDepthStack.push(arrayDepth);
    arrayDepth = 0;
  }

  void popArrayDepth() {
    arrayDepth = arrayDepthStack.pop();
  }

  // A run of digits starts a date or a time when a dash or a colon follows it, and is a decimal integer otherwise.
  // An action reads that following character. A semantic predicate can read it too, but ANTLR then caches no DFA edge
  // for input that reaches the predicate, leaving every digit of every number to ATN simulation: with one here, an
  // array of 500,000 integers lexed about 60 times slower.
  void decimalIntegerOrDateStart() {
    if ("-:".indexOf(_input.LA(1)) < 0) {
      pushValueModeIfInArray();
      popMode();
    } else if (isDigits(getText())) {
      pushValueModeIfInArray();
      setType(TomlLexer.DateDigits);
      mode(TomlLexer.DateMode);
    } else {
      // A dash or a colon follows, but a run holding a sign or an underscore starts no date or time, and a dash or a
      // colon ends no integer.
      setType(TomlLexer.Error);
      popMode();
    }
  }

  private static boolean isDigits(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }
}
