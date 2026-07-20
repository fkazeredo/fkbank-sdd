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
      String body =
          callbackJson(
              "11111111-1111-1111-1111-111111111111",
              pending.bureauReference().value().toString(),
              "APPROVED",
              null);

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
  @DisplayName("QA2-01 - the callback's reference, not the onboarding id, names the application")
  class ReferenceBinding {

    /**
     * The original QA2-01 forgery, replayed against the fix: a validly-signed callback naming the
     * onboarding's own public id (returned to the applicant at sign-up, and the address of the
     * public status page) as {@code reference}, with a completely fabricated {@code inquiryId}.
     *
     * <p>This used to be accepted and applied ({@code 200}), because {@code reference} was the
     * onboarding id and nothing checked {@code inquiryId} at all. The fix replaces the
     * correlation value with a private, bank-generated {@code bureauReference} that is never
     * disclosed to the applicant, so the public id no longer resolves to any application. This
     * test is the security property the fix bought: it must keep failing to resolve, not merely
     * happen to today.
     */
    @Test
    @DisplayName("the public onboarding id is refused as a reference, even when correctly signed")
    void thePublicOnboardingIdIsRefusedAsAReference() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String fabricatedInquiryId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
      String body =
          callbackJson(fabricatedInquiryId, pending.id().toString(), "REJECTED", "FRAUD_SUSPECTED");

      HttpResponse<String> response = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);

      assertThat(response.statusCode())
          .as("the applicant's own public identifier must never double as the callback's reference")
          .isEqualTo(404);
      assertThat(response.body()).contains("\"code\":\"UNKNOWN_ONBOARDING\"");
      assertThat(onboardings.findById(pending.id()))
          .as("a callback that resolves to no application must leave the real one untouched")
          .hasValueSatisfying(o -> assertThat(o.isPending()).isTrue());
    }

    /**
     * Documents the design's other half, so a future change cannot silently narrow it without a
     * test noticing: the bureau's own {@code inquiryId} is accepted but never checked, by design,
     * because on the one path a callback exists at all (the bureau timed out), the bank never saw
     * the response that would have carried it. What authenticates a callback is the signature
     * plus a reference the bank itself generated and disclosed only to the bureau - not the
     * inquiry id.
     */
    @Test
    @DisplayName("the genuine reference is accepted with any inquiryId - the reference is what authenticates the correlation")
    void theGenuineReferenceIsAcceptedRegardlessOfInquiryId() throws Exception {
      Onboarding pending = onboardingFixture.pendingApplication();
      String fabricatedInquiryId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
      String body =
          callbackJson(
              fabricatedInquiryId,
              pending.bureauReference().value().toString(),
              "REJECTED",
              "FRAUD_SUSPECTED");

      HttpResponse<String> response = postSigned("/api/webhooks/bureau", body, CALLBACK_SECRET);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(onboardings.findById(pending.id()))
          .hasValueSatisfying(o -> assertThat(o.status().name()).isEqualTo("REJECTED"));
    }
  }

  private static String callbackJson(String inquiryId, String reference, String outcome, String reasonCategory) {
    String reason = reasonCategory == null ? "null" : "\"" + reasonCategory + "\"";
    return """
        {"inquiryId":"%s","reference":"%s","outcome":"%s","reasonCategory":%s}"""
        .formatted(inquiryId, reference, outcome, reason);
  }
}
