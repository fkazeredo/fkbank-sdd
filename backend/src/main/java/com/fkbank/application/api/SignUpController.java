package com.fkbank.application.api;

import com.fkbank.domain.onboarding.OnboardingId;
import com.fkbank.domain.onboarding.OnboardingStatus;
import com.fkbank.domain.onboarding.OnboardingView;
import com.fkbank.domain.onboarding.SignUp;
import com.fkbank.domain.onboarding.SignUpRequest;
import com.fkbank.domain.onboarding.SignUpResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening an account, for someone who is not a customer yet.
 *
 * <p>Necessarily public: an applicant has no credentials until their application succeeds, so
 * there is nothing to authenticate them with. What protects the status route instead is the
 * identifier itself, which is generated rather than derived from anything about the applicant
 * and reveals only an outcome.
 */
@RestController
@RequestMapping("/api/signup")
public class SignUpController {

  private final SignUp signUp;

  SignUpController(SignUp signUp) {
    this.signUp = signUp;
  }

  /**
   * Submits an application to open an account.
   *
   * <p>The status distinguishes three outcomes that all mean "we received it": the application
   * was decided immediately, it is waiting on the bureau, or it was already under way because
   * this CPF had been submitted before and not yet answered. Resubmitting is not an error and
   * never creates a second application.
   *
   * @return {@code 201} when this submission started an application that has been decided,
   *     {@code 202} when it started one that is still being checked, and {@code 200} when an
   *     application was already under way and nothing was created
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OnboardingResponse> submit(@RequestBody SignUpRequestBody body) {
    SignUpResult result =
        signUp.submit(
            new SignUpRequest(
                body.fullName(),
                body.cpf(),
                body.email(),
                body.password(),
                body.birthDate(),
                body.monthlyIncome()));

    return ResponseEntity.status(statusFor(result))
        .body(OnboardingResponse.of(result.application()));
  }

  /**
   * Reports where an application got to, for someone holding its identifier.
   *
   * @return {@code 200} with the outcome, or {@code 404} if no such application exists
   */
  @GetMapping(value = "/{onboardingId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public OnboardingResponse status(@PathVariable String onboardingId) {
    OnboardingView application = signUp.statusOf(OnboardingId.of(onboardingId));
    return OnboardingResponse.of(application);
  }

  private static HttpStatus statusFor(SignUpResult result) {
    if (!result.started()) {
      return HttpStatus.OK;
    }
    return result.application().status() == OnboardingStatus.PENDING
        ? HttpStatus.ACCEPTED
        : HttpStatus.CREATED;
  }
}
