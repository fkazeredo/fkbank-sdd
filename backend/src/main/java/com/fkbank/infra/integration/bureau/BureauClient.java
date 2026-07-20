package com.fkbank.infra.integration.bureau;

import com.fkbank.domain.onboarding.BureauCheck;
import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.Onboarding;
import com.fkbank.domain.onboarding.RejectionReason;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the credit bureau, and keeps its vocabulary out of the rest of the product.
 *
 * <p>Everything specific to the bureau stops here: its request shape, its own names for
 * outcomes, and the fact that it sometimes does not answer. What leaves this class is a decision
 * in our terms.
 *
 * <p>Not answering is treated as an outcome rather than a failure. The bureau is a third party
 * and being slow is something third parties do; an application that has been submitted is not
 * lost by it, and the answer arrives later through the callback.
 */
@Component
@EnableConfigurationProperties(BureauProperties.class)
class BureauClient implements BureauCheck {

  private static final Logger log = LoggerFactory.getLogger(BureauClient.class);

  private final RestClient client;

  BureauClient(BureauProperties properties) {
    // Built rather than taken from a shared, auto-configured builder: this client's timeouts are
    // the difference between an applicant waiting and an applicant being told to come back, and
    // they must not change because something else in the application reconfigured a builder
    // everyone shares.
    this.client =
        RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory(properties.getTimeout()))
            .build();
  }

  @Override
  public BureauDecision decide(Onboarding onboarding) {
    try {
      InquiryResponse response =
          client
              .post()
              .uri("/inquiries")
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new InquiryRequest(
                      onboarding.id().value().toString(),
                      onboarding.cpf().value(),
                      onboarding.fullName().value(),
                      onboarding.birthDate()))
              .retrieve()
              .body(InquiryResponse.class);

      return response == null ? BureauDecision.undetermined() : toDecision(response);
    } catch (RuntimeException unreachable) {
      // A timeout, a refused connection or an error status all mean the same thing to the
      // caller: no answer yet. The application stays open, and the bureau's own callback
      // settles it when it can. The CPF is not logged — an unanswered check is not a reason to
      // write someone's tax number into a log file.
      log.warn(
          "bureau did not answer for onboarding {}: {}",
          onboarding.id(),
          unreachable.getClass().getSimpleName());
      return BureauDecision.undetermined();
    }
  }

  private static BureauDecision toDecision(InquiryResponse response) {
    String outcome = String.valueOf(response.outcome()).toUpperCase(Locale.ROOT);
    return switch (outcome) {
      case "APPROVED" -> BureauDecision.approved();
      case "REJECTED" -> BureauDecision.rejected(RejectionReason.from(response.reasonCategory()));
      default -> BureauDecision.undetermined();
    };
  }

  /**
   * Bounds both halves of the wait.
   *
   * <p>A read timeout alone still lets a connection that never establishes hang for as long as
   * the operating system allows.
   */
  private static ClientHttpRequestFactory requestFactory(Duration timeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    return factory;
  }

  /** What the bureau is asked. */
  private record InquiryRequest(String reference, String cpf, String fullName, LocalDate birthDate) {}

  /** What the bureau answers. */
  private record InquiryResponse(String inquiryId, String outcome, String reasonCategory) {}
}
