package com.fkbank.application.api;

import com.fkbank.domain.account.Receipt;
import com.fkbank.domain.account.Statements;
import com.fkbank.domain.account.StatementCursor;
import com.fkbank.domain.account.StatementFilter;
import com.fkbank.domain.account.StatementPage;
import com.fkbank.domain.account.UnknownReceiptException;
import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.PostingId;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person's own statement and the receipts its lines prove.
 *
 * <p>Scoped the same way {@link AccountController} scopes the account itself: no account
 * identifier in the path, only the caller the bearer token names, so there is nothing a caller
 * can change in order to page through somebody else's movements.
 */
@RestController
@RequestMapping("/api/account/statement")
public class StatementController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final Statements statements;
  private final AuthenticatedCaller caller;
  private final Clock clock;

  StatementController(Statements statements, AuthenticatedCaller caller, Clock clock) {
    this.statements = statements;
    this.caller = caller;
    this.clock = clock;
  }

  /**
   * One page of the caller's statement.
   *
   * @param from the period start (inclusive); {@code to} must also be given, or both are ignored
   * @param to the period end (exclusive); {@code from} must also be given, or both are ignored
   * @param direction {@code IN}, {@code OUT}, or omitted for both
   * @param cursor the opaque cursor a previous page returned; omitted for the first page
   * @param size the page size; default {@value #DEFAULT_PAGE_SIZE}, capped at
   *     {@value #MAX_PAGE_SIZE}
   * @return {@code 200} with the page's lines, newest first, and the cursor for the next page
   *     when there is one
   */
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "One page of the caller's statement."),
    @ApiResponse(
        responseCode = "401",
        description = "No valid bearer token was presented.",
        content = @Content(mediaType = "application/problem+json")),
    @ApiResponse(
        responseCode = "404",
        description = "The caller holds no account.",
        content = @Content(mediaType = "application/problem+json")),
    @ApiResponse(
        responseCode = "422",
        description = "The direction, cursor or page size could not be understood.",
        content = @Content(mediaType = "application/problem+json"))
  })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public StatementPageResponse statement(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String size) {

    Instant parsedFrom = instantOf("from", from);
    Instant parsedTo = instantOf("to", to);
    Direction parsedDirection = directionOf(direction);
    StatementFilter filter =
        (parsedFrom == null || parsedTo == null)
            ? StatementFilter.currentMonth(parsedDirection, clock)
            : StatementFilter.of(parsedFrom, parsedTo, parsedDirection);
    Optional<StatementCursor> parsedCursor =
        cursor == null ? Optional.empty() : Optional.of(StatementCursor.decode(cursor));

    StatementPage page =
        statements.statementOf(caller.resolve(jwt), filter, parsedCursor, pageSizeOf(size));
    return StatementPageResponse.of(page);
  }

  /**
   * The receipt for one of the caller's own movements.
   *
   * @return {@code 200} with the receipt, from the caller's own perspective
   */
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The receipt for that movement."),
    @ApiResponse(
        responseCode = "401",
        description = "No valid bearer token was presented.",
        content = @Content(mediaType = "application/problem+json")),
    @ApiResponse(
        responseCode = "404",
        description = "No such movement exists, or the caller was not a party to it.",
        content = @Content(mediaType = "application/problem+json"))
  })
  @GetMapping(value = "/receipts/{postingId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ReceiptResponse receipt(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String postingId) {
    Receipt receipt = statements.receiptOf(caller.resolve(jwt), postingIdOf(postingId));
    return ReceiptResponse.of(receipt);
  }

  private static Direction directionOf(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw.toUpperCase(Locale.ROOT)) {
      case "IN" -> Direction.CREDIT;
      case "OUT" -> Direction.DEBIT;
      default -> throw new IllegalArgumentException("direction must be IN or OUT, was " + raw);
    };
  }

  /**
   * Parses a period bound the same way {@code direction}/{@code cursor} are parsed: as a raw
   * string, with a curated message on failure — never a typed {@code @RequestParam}, whose
   * conversion failure is Spring's own generic {@code MethodArgumentTypeMismatchException},
   * naming its internal types (QA-0003-01) rather than this endpoint's own domain language.
   */
  private static Instant instantOf(String paramName, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          paramName + " must be a full ISO-8601 instant, was " + raw);
    }
  }

  private static int pageSizeOf(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_PAGE_SIZE;
    }
    int requested;
    try {
      requested = Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("size must be a whole number, was " + raw);
    }
    if (requested < 1 || requested > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return requested;
  }

  /**
   * An id that is not even a well-formed UUID cannot name a movement, so it is refused the same
   * way one that is well-formed but unknown would be — a caller probing for a valid shape learns
   * nothing a genuinely unknown id would not also tell them.
   */
  private static PostingId postingIdOf(String raw) {
    try {
      return PostingId.of(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      throw new UnknownReceiptException(raw);
    }
  }
}
