package com.fkbank.acceptance.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.onboarding.Onboarding;
import com.fkbank.domain.onboarding.OnboardingRepository;
import com.fkbank.testsupport.OnboardingFixture;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The bureau's asynchronous callback, {@code POST /api/webhooks/bureau}, driven with real HTTP
 * requests and a real HMAC-SHA256 signature computed independently of the production signer.
 */
@DisplayName("POST /api/webhooks/bureau")
class BureauCallbackAcceptanceIT extends OnboardingHttpAcceptanceTest {

  @Autowired private OnboardingFixture onboardingFixture;
  @Autowired private OnboardingRepository onboardings;

  @Nested
  @DisplayName("signature verification")
  class SignatureVerification {

    @Test
    @DisplayName("a forged signature is refused 401, and nothing is applied")
    void forgedSignatureIsRejected() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String body = callbackJson("00000000-0000-0000-0000-000000000000", pending.id().toString(), "APPROVED", null);

      HttpResponse<String> response = postSigned("/api/webhooks/bureau", body, "the-wrong-secret");

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.body()).contains("\"code\":\"UNVERIFIED_BUREAU_CALLBACK\"");
      assertThat(onboardings.findById(pending.id())).hasValueSatisfying(o -> assertThat(o.isPending()).isTrue());
    }

    @Test
    @DisplayName("an absent signature header is refused 401")
    void absentSignatureIsRejected() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String body = callbackJson("00000000-0000-0000-0000-000000000000", pending.id().toString(), "APPROVED", null);

      HttpResponse<String> response = postWithHeader("/api/webhooks/bureau", body, "X-Ignored", null);

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a truncated hex digest is refused 401 rather than crashing the parser")
    void truncatedSignatureIsRejected() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String body = callbackJson("00000000-0000-0000-0000-000000000000", pending.id().toString(), "APPROVED", null);
      String genuine = sign(CALLBACK_SECRET, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      HttpResponse<String> response =
          postWithHeader("/api/webhooks/bureau", body, "X-Bureau-Signature", genuine.substring(0, 20));

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a signature naming the wrong algorithm prefix is refused 401")
    void malformedPrefixIsRejected() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String body = callbackJson("00000000-0000-0000-0000-000000000000", pending.id().toString(), "APPROVED", null);
      String genuine = sign(CALLBACK_SECRET, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String withoutPrefix = genuine.substring("sha256=".length());

      HttpResponse<String> response =
          postWithHeader("/api/webhooks/bureau", body, "X-Bureau-Signature", withoutPrefix);

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a genuinely signed callback naming an onboarding nobody ever submitted is 404")
    void unknownReferenceIsNotFound() throws Exception {
      String body =
          callbackJson(
              "00000000-0000-0000-0000-000000000000",
              "99999999-9999-9999-9999-999999999999",
              "APPROVED",
              null);

      HttpResponse<String> response = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);

      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body()).contains("\"code\":\"UNKNOWN_ONBOARDING\"");
    }
  }

  @Nested
  @DisplayName("delivering the same answer twice")
  class DuplicateDelivery {

    @Test
    @DisplayName("the same signed callback applied twice changes nothing the second time")
    void repeatedDeliveryIsANoOp() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String body = callbackJson("11111111-1111-1111-1111-111111111111", pending.id().toString(), "APPROVED", null);

      HttpResponse<String> first = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);
      HttpResponse<String> second = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);

      assertThat(first.statusCode()).isEqualTo(200);
      assertThat(second.statusCode())
          .as("a repeat delivery is reported as accepted, not as an error, so a retrying sender is not taught to stop")
          .isEqualTo(200);
      assertThat(onboardings.findById(pending.id()))
          .hasValueSatisfying(o -> assertThat(o.status().name()).isEqualTo("APPROVED"));
    }
  }

  @Nested
  @DisplayName("QA2-01 - the callback does not bind inquiryId to the onboarding it decides")
  class InquiryBinding {

    /**
     * A correctly signed callback is accepted, and applied, as long as it names a known,
     * still-pending {@code reference} - regardless of what {@code inquiryId} it carries. The
     * production code parses {@code inquiryId} into {@code BureauCallbackController.Callback}
     * and never reads it again ({@code grep -rn inquiryId backend/src/main} finds it nowhere
     * else), and {@code OnboardingOutcome.apply(OnboardingId, BureauDecision)} has no inquiry
     * parameter to check it against.
     *
     * <p>This is the executable repro for the finding: anyone who can compute a valid signature
     * (the shared secret, not any information specific to a particular application) can settle
     * any other applicant's still-pending decision using a completely fabricated inquiry
     * identifier, using only the onboarding id the applicant is handed back at sign-up time. The
     * assertion below is what a bound callback would refuse; today it does not, so this test
     * fails until the callback is validated against the inquiry actually opened for the
     * onboarding it names.
     */
    @Test
    @DisplayName("a signed callback with a fabricated inquiryId still overrides a pending decision")
    void aFabricatedInquiryIdShouldNotBeAbleToDecideSomeoneElsesApplication() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String fabricatedInquiryId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
      String body =
          callbackJson(fabricatedInquiryId, pending.id().toString(), "REJECTED", "FRAUD_SUSPECTED");

      HttpResponse<String> response = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);

      assertThat(response.statusCode())
          .as(
              "a callback is authenticated by more than a shared secret and a known reference: it"
                  + " must also carry the inquiryId the application was actually opened under, or a"
                  + " fabricated one should be refused rather than silently accepted")
          .isNotEqualTo(200);
      assertThat(onboardings.findById(pending.id()))
          .as("the application must still be waiting on its real inquiry, not settled by a fabricated one")
          .hasValueSatisfying(o -> assertThat(o.isPending()).isTrue());
    }
  }

  private static String callbackJson(String inquiryId, String reference, String outcome, String reasonCategory) {
    String reason = reasonCategory == null ? "null" : "\"" + reasonCategory + "\"";
    return """
        {"inquiryId":"%s","reference":"%s","outcome":"%s","reasonCategory":%s}"""
        .formatted(inquiryId, reference, outcome, reason);
  }
}
