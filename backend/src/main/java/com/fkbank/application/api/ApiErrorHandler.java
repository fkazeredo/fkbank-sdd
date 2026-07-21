package com.fkbank.application.api;

import com.fkbank.domain.account.UnknownAccountException;
import com.fkbank.domain.account.UnknownReceiptException;
import com.fkbank.domain.customer.DuplicateCustomerException;
import com.fkbank.domain.customer.UnderageCustomerException;
import com.fkbank.domain.identity.WeakPasswordException;
import com.fkbank.domain.onboarding.OnboardingAlreadyPendingException;
import com.fkbank.domain.onboarding.UnknownBureauReferenceException;
import com.fkbank.domain.onboarding.UnknownOnboardingException;
import com.fkbank.domain.onboarding.UnverifiedBureauCallbackException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a refusal the domain raised into the response a client sees.
 *
 * <p>One place decides how each business failure is reported, so the same refusal cannot be a
 * {@code 409} on one route and a {@code 400} on another. Each response carries the stable code
 * the domain assigned, which is what a client can safely branch on — status alone is too coarse
 * and message text is free to change.
 *
 * <p>Nothing here reports an internal failure. An exception with no handler falls through to the
 * framework's own error response, which is configured to disclose neither message nor stack
 * trace.
 */
@RestControllerAdvice
class ApiErrorHandler {

  private static final String PROBLEM_BASE = "https://fkbank.example/problems/";

  /** Somebody already banks here with this CPF or e-mail address. */
  @ExceptionHandler(DuplicateCustomerException.class)
  ProblemDetail duplicateCustomer(DuplicateCustomerException refusal) {
    return problem(HttpStatus.CONFLICT, refusal.code(), refusal.getMessage());
  }

  /**
   * The submitted values could not be accepted.
   *
   * <p>Reported as {@code 422} rather than {@code 400}: the request was well-formed JSON and was
   * understood, and what failed was the content of it. The distinction matters to a form, which
   * can show a field error for one and only a general failure for the other.
   */
  @ExceptionHandler({
    UnderageCustomerException.class,
    WeakPasswordException.class,
    IllegalArgumentException.class
  })
  ProblemDetail unacceptableSubmission(RuntimeException refusal) {
    return problem(HttpStatus.UNPROCESSABLE_CONTENT, codeOf(refusal), refusal.getMessage());
  }

  /** No application, account or receipt exists behind the identifier that was asked for. */
  @ExceptionHandler({
    UnknownOnboardingException.class,
    UnknownBureauReferenceException.class,
    UnknownAccountException.class,
    UnknownReceiptException.class
  })
  ProblemDetail notFound(RuntimeException refusal) {
    return problem(HttpStatus.NOT_FOUND, codeOf(refusal), refusal.getMessage());
  }

  /**
   * A callback arrived that the bureau could not have signed.
   *
   * <p>Answered with {@code 401} and nothing else. The detail is deliberately unhelpful: an
   * unauthenticated caller learning why their signature failed is a caller being helped towards
   * one that works.
   */
  @ExceptionHandler(UnverifiedBureauCallbackException.class)
  ProblemDetail unverifiedCallback(UnverifiedBureauCallbackException refusal) {
    return problem(HttpStatus.UNAUTHORIZED, refusal.code(), "Unauthorized");
  }

  /**
   * Two submissions for the same CPF raced and this one lost, and the winner could not be read
   * back.
   *
   * <p>Reported as {@code 409} because a second application genuinely does exist. The caller
   * normally never sees this — the submission path answers the race by returning the
   * application that won.
   */
  @ExceptionHandler(OnboardingAlreadyPendingException.class)
  ProblemDetail alreadyPending(OnboardingAlreadyPendingException refusal) {
    return problem(HttpStatus.CONFLICT, refusal.code(), refusal.getMessage());
  }

  /**
   * Builds the response body.
   *
   * <p>The code travels as its own property rather than only inside the type URI, so a client
   * reads one field instead of parsing a URL.
   */
  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setProperty("code", code);
    return problem;
  }

  /**
   * Reads the stable code a refusal carries.
   *
   * <p>Anything without one is an argument the domain rejected while reading a submission, which
   * is a single condition from a client's point of view: what was sent could not be used.
   */
  private static String codeOf(RuntimeException refusal) {
    if (refusal instanceof UnderageCustomerException) {
      return UnderageCustomerException.CODE;
    }
    if (refusal instanceof WeakPasswordException weak) {
      return weak.code();
    }
    if (refusal instanceof UnknownOnboardingException) {
      return UnknownOnboardingException.CODE;
    }
    if (refusal instanceof UnknownBureauReferenceException) {
      return UnknownBureauReferenceException.CODE;
    }
    if (refusal instanceof UnknownAccountException) {
      return UnknownAccountException.CODE;
    }
    if (refusal instanceof UnknownReceiptException receipt) {
      return receipt.code();
    }
    return "INVALID_SUBMISSION";
  }
}
