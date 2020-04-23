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

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.time.DateTimeException;
import java.time.LocalDate;

import org.antlr.v4.runtime.tree.ErrorNode;

final class LocalDateVisitor extends TomlParserBaseVisitor<LocalDate> {

  private static final LocalDate INITIAL = LocalDate.parse("1900-01-01");
  private LocalDate date = INITIAL;

  @Override
  public LocalDate visitYear(TomlParser.YearContext ctx) {
    String text = ctx.getText();
    if (text.length() != 4) {
      throw new TomlParseError("Invalid year (valid range 0000..9999)", new TomlPosition(ctx));
    }
    int year;
    try {
      year = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid year", new TomlPosition(ctx), e);
    }
    date = date.withYear(year);
    return date;
  }

  @Override
  public LocalDate visitMonth(TomlParser.MonthContext ctx) {
    String text = ctx.getText();
    if (text.length() != 2) {
      throw new TomlParseError("Invalid month (valid range 01..12)", new TomlPosition(ctx));
    }
    int month;
    try {
      month = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid month", new TomlPosition(ctx), e);
    }
    if (month < 1 || month > 12) {
      throw new TomlParseError("Invalid month (valid range 01..12)", new TomlPosition(ctx));
    }
    date = date.withMonth(month);
    return date;
  }

  @Override
  public LocalDate visitDay(TomlParser.DayContext ctx) {
    String text = ctx.getText();
    if (text.length() != 2) {
      throw new TomlParseError("Invalid day (valid range 01..28/31)", new TomlPosition(ctx));
    }
    int day;
    try {
      day = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new TomlParseError("Invalid day", new TomlPosition(ctx), e);
    }
    if (day < 1 || day > 31) {
      throw new TomlParseError("Invalid day (valid range 01..28/31)", new TomlPosition(ctx));
    }
    try {
      date = date.withDayOfMonth(day);
    } catch (DateTimeException e) {
      throw new TomlParseError(e.getMessage(), new TomlPosition(ctx), e);
    }
    return date;
  }

  @Override
  public LocalDate visitErrorNode(ErrorNode node) {
    return null;
  }

  @Override
  protected LocalDate aggregateResult(LocalDate aggregate, LocalDate nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected LocalDate defaultResult() {
    return date;
  }
}
