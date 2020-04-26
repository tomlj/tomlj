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

import java.time.DateTimeException;
import java.time.ZoneOffset;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;

final class ZoneOffsetVisitor extends TomlParserBaseVisitor<ZoneOffset> {

  private int hours = 0;
  private int minutes = 0;

  @Override
  public ZoneOffset visitHourOffset(TomlParser.HourOffsetContext ctx) {
    int hours;
    try {
      hours = Integer.parseInt(ctx.getText());
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid zone offset", new TomlPosition(ctx), e);
    }
    if (hours < -18 || hours > 18) {
      throw new TomlParseError("Invalid zone offset hours (valid range -18..+18)", new TomlPosition(ctx));
    }
    ZoneOffset offset = toZoneOffset(hours, minutes, ctx, 0);
    this.hours = hours;
    return offset;
  }

  @Override
  public ZoneOffset visitMinuteOffset(TomlParser.MinuteOffsetContext ctx) {
    int minutes;
    try {
      minutes = Integer.parseInt(ctx.getText());
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid zone offset", new TomlPosition(ctx), e);
    }
    if (minutes < 0 || minutes > 59) {
      throw new TomlParseError("Invalid zone offset minutes (valid range 0..59)", new TomlPosition(ctx));
    }
    ZoneOffset offset = toZoneOffset(hours, minutes, ctx, -4);
    this.minutes = minutes;
    return offset;
  }

  private static ZoneOffset toZoneOffset(int hours, int minutes, ParserRuleContext ctx, int offset) {
    try {
      return ZoneOffset.ofHoursMinutes(hours, (hours < 0) ? -minutes : minutes);
    } catch (DateTimeException e) {
      throw new TomlParseError("Invalid zone offset (valid range -18:00..+18:00)", new TomlPosition(ctx, offset), e);
    }
  }

  @Override
  public ZoneOffset visitErrorNode(ErrorNode node) {
    return null;
  }

  @Override
  protected ZoneOffset aggregateResult(ZoneOffset aggregate, ZoneOffset nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected ZoneOffset defaultResult() {
    return ZoneOffset.ofHoursMinutes(this.hours, (this.hours < 0) ? -this.minutes : this.minutes);
  }
}
