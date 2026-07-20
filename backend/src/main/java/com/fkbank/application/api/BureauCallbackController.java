package com.fkbank.application.api;

import com.fkbank.domain.onboarding.BureauCallbackSignature;
import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.OnboardingId;
import com.fkbank.domain.onboarding.OnboardingOutcome;
import com.fkbank.domain.onboarding.RejectionReason;
import com.fkbank.domain.onboarding.UnverifiedBureauCallbackException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives the bureau's answer when it could not give one in time.
 *
 * <p>The route is public because the bureau has no account here, so the signature over the body
 * is the whole of its authentication. It is checked before the body is interpreted: an
 * unverified caller must not be able to reach the parser, let alone the application.
 *
 * <p>Delivering the same answer twice is expected rather than exceptional — a sender that never
 * retries is a sender that loses answers — so a repeat changes nothing and is still reported as
 * accepted. Reporting a repeat as an error teaches the sender to keep retrying.
 */
@RestController
@RequestMapping("/api/webhooks/bureau")
public class BureauCallbackController {

  private final BureauCallbackSignature signature;
  private final OnboardingOutcome outcome;
  private final ObjectMapper json;

  BureauCallbackController(
      BureauCallbackSignature signature, OnboardingOutcome outcome, ObjectMapper json) {
    this.signature = signature;
    this.outcome = outcome;
    this.json = json;
  }

  /**
   * Applies the bureau's answer to the application it refers to.
   *
   * <p>The body is taken as raw bytes because the signature covers exactly what was sent.
   * Parsing first and re-serializing to check the signature would compare our rendering of the
   * message with the sender's, and the two differ over whitespace and key order.
   *
   * @return {@code 200} once the answer has been applied, or found to have been applied already
   * @throws UnverifiedBureauCallbackException if the signature is absent or does not match,
   *     which the error contract renders as {@code 401} with nothing written
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> receive(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-Bureau-Signature", required = false) String presentedSignature) {

    if (!signature.isValid(body, presentedSignature)) {
      throw new UnverifiedBureauCallbackException();
    }

    Callback callback = read(body);
    outcome.apply(OnboardingId.of(callback.reference()), decisionOf(callback));
    return ResponseEntity.ok().build();
  }

  private Callback read(byte[] body) {
    try {
      return json.readValue(new String(body, StandardCharsets.UTF_8), Callback.class);
    } catch (RuntimeException malformed) {
      throw new IllegalArgumentException("the callback body could not be read", malformed);
    }
  }

  private static BureauDecision decisionOf(Callback callback) {
    String outcome = String.valueOf(callback.outcome()).toUpperCase(Locale.ROOT);
    return switch (outcome) {
      case "APPROVED" -> BureauDecision.approved();
      case "REJECTED" -> BureauDecision.rejected(RejectionReason.from(callback.reasonCategory()));
      // A verified sender saying something we do not understand leaves the application alone
      // rather than guessing. Guessing here would either open an account or refuse one on the
      // strength of a word nobody has defined.
      default -> BureauDecision.undetermined();
    };
  }

  /** What the bureau sends when it answers late. */
  private record Callback(
      String inquiryId, String reference, String outcome, String reasonCategory) {}
}
