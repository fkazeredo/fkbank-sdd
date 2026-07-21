package com.fkbank.domain.account;

import com.fkbank.domain.ledger.Direction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * What slice of a statement to show: a half-open period {@code [from, to)} and, optionally, a
 * direction.
 *
 * <p>An unspecified period defaults to the current month, drawn at the product's fixed MVP
 * timezone — UTC-3, the same boundary {@code docs/DOMAIN.md}'s "product timezone" decision fixes
 * for SPEC-0013's daily counters, applied here to a monthly one instead. The instants themselves
 * are always UTC; only where the boundary falls is decided in local time.
 */
public record StatementFilter(Instant from, Instant to, Direction direction) {

  private static final ZoneOffset PRODUCT_TIMEZONE = ZoneOffset.of("-03:00");

  public StatementFilter {
    Objects.requireNonNull(from, "period start must not be null");
    Objects.requireNonNull(to, "period end must not be null");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("period start must be before its end");
    }
  }

  /** An explicit period, in either direction or both ({@code direction == null}). */
  public static StatementFilter of(Instant from, Instant to, Direction direction) {
    return new StatementFilter(from, to, direction);
  }

  /** The current month at the product timezone, translated to the UTC instants the ledger uses. */
  public static StatementFilter currentMonth(Direction direction, Clock clock) {
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(PRODUCT_TIMEZONE);
    ZonedDateTime start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(PRODUCT_TIMEZONE);
    ZonedDateTime end = start.plusMonths(1);
    return new StatementFilter(start.toInstant(), end.toInstant(), direction);
  }
}
