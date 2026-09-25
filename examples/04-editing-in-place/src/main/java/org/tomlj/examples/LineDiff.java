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
package org.tomlj.examples;

/**
 * Prints two texts line by line, marking the lines removed with "-" and the lines added with "+".
 */
final class LineDiff {

  private LineDiff() {}

  static void print(String before, String after) {
    String[] a = before.lines().toArray(String[]::new);
    String[] b = after.lines().toArray(String[]::new);

    // common[i][j] is the length of the longest common subsequence of a[i..] and b[j..].
    int[][] common = new int[a.length + 1][b.length + 1];
    for (int i = a.length - 1; i >= 0; i--) {
      for (int j = b.length - 1; j >= 0; j--) {
        common[i][j] = a[i].equals(b[j]) ? common[i + 1][j + 1] + 1 : Math.max(common[i + 1][j], common[i][j + 1]);
      }
    }

    int i = 0;
    int j = 0;
    while (i < a.length || j < b.length) {
      if (i < a.length && j < b.length && a[i].equals(b[j])) {
        System.out.println("  " + a[i++]);
        j++;
      } else if (i < a.length && (j == b.length || common[i + 1][j] >= common[i][j + 1])) {
        System.out.println("- " + a[i++]);
      } else {
        System.out.println("+ " + b[j++]);
      }
    }
  }
}
