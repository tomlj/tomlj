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

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.time.LocalTime;

import org.antlr.v4.runtime.tree.ErrorNode;

final class LocalTimeVisitor extends TomlParserBaseVisitor<LocalTime> {

  private LocalTime time = LocalTime.MIN;

  @Override
  public LocalTime visitHour(TomlParser.HourContext ctx) {
    String text = ctx.getText();
    if (text.length() != 2) {
      throw new TomlParseError("Invalid hour (valid range 00..23)", new TomlPosition(ctx));
    }
    int hour;
    try {
      hour = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid hour", new TomlPosition(ctx), e);
    }
    if (hour < 0 || hour > 23) {
      throw new TomlParseError("Invalid hour (valid range 00..23)", new TomlPosition(ctx));
    }
    time = time.withHour(hour);
    return time;
  }

  @Override
  public LocalTime visitMinute(TomlParser.MinuteContext ctx) {
    String text = ctx.getText();
    if (text.length() != 2) {
      throw new TomlParseError("Invalid minutes (valid range 00..59)", new TomlPosition(ctx));
    }
    int minute;
    try {
      minute = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid minutes", new TomlPosition(ctx), e);
    }
    if (minute < 0 || minute > 59) {
      throw new TomlParseError("Invalid minutes (valid range 00..59)", new TomlPosition(ctx));
    }
    time = time.withMinute(minute);
    return time;
  }

  @Override
  public LocalTime visitSecond(TomlParser.SecondContext ctx) {
    String text = ctx.getText();
    if (text.length() != 2) {
      throw new TomlParseError("Invalid seconds (valid range 00..59)", new TomlPosition(ctx));
    }
    int second;
    try {
      second = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid seconds", new TomlPosition(ctx), e);
    }
    if (second < 0 || second > 59) {
      throw new TomlParseError("Invalid seconds (valid range 00..59)", new TomlPosition(ctx));
    }
    time = time.withSecond(second);
    return time;
  }

  @Override
  public LocalTime visitSecondFraction(TomlParser.SecondFractionContext ctx) {
    String text = ctx.getText();
    if (text.isEmpty() || text.length() > 9) {
      throw new TomlParseError("Invalid nanoseconds (valid range 0..999999999)", new TomlPosition(ctx));
    }
    if (text.length() < 9) {
      text = text + "000000000".substring(text.length());
    }
    int nano;
    try {
      nano = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid nanoseconds", new TomlPosition(ctx), e);
    }
    time = time.withNano(nano);
    return time;
  }

  @Override
  public LocalTime visitErrorNode(ErrorNode node) {
    return null;
  }

  @Override
  protected LocalTime aggregateResult(LocalTime aggregate, LocalTime nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected LocalTime defaultResult() {
    return time;
  }
}
