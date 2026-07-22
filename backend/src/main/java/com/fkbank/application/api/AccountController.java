package com.fkbank.application.api;

import com.fkbank.domain.account.CurrentAccounts;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person's own account.
 *
 * <p>Scoped to whoever the token says is calling: there is no account identifier in the path, so
 * there is nothing a caller can change in order to read somebody else's balance.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final CurrentAccounts accounts;
  private final AuthenticatedCaller caller;

  AccountController(CurrentAccounts accounts, AuthenticatedCaller caller) {
    this.accounts = accounts;
    this.caller = caller;
  }

  /**
   * Returns the caller's account details and what it holds.
   *
   * @param jwt the validated access token; never null, because this route is not public
   * @return {@code 200} with the branch, number, balance and currency
   */
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The caller's account and what it holds."),
    @ApiResponse(
        responseCode = "401",
        description = "No valid bearer token was presented.",
        content = @Content(mediaType = "application/problem+json")),
    @ApiResponse(
        responseCode = "404",
        description = "The caller holds no account.",
        content = @Content(mediaType = "application/problem+json"))
  })
  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  public AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
    return AccountResponse.of(accounts.summaryOf(caller.resolve(jwt)));
  }
}
