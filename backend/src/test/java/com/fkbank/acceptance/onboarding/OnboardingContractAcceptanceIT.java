package com.fkbank.acceptance.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * QA2-03 - whether the published contract at {@code /v3/api-docs} actually describes the
 * sign-up and account-opening operations this slice adds.
 *
 * <p>{@code OpenApiSnapshotIT} (builder-owned) proves the served document has not drifted from
 * the committed snapshot. It cannot catch the gap this test is about: the snapshot can be
 * perfectly self-consistent and still fail to describe the real contract, because it is
 * generated from the same controller methods this test reads. Every response code below was
 * observed for real, against the real running application, in this slice's black-box pass.
 */
@DisplayName("The published OpenAPI contract for sign-up and account opening")
class OnboardingContractAcceptanceIT extends OnboardingHttpAcceptanceTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  @DisplayName("POST /api/signup documents every status code the route actually returns")
  void signUpDocumentsItsRealResponses() throws Exception {
    JsonNode responses = operationResponses("/api/signup", "post");

    assertThat(fieldNames(responses))
        .as(
            "BR-1 requires 409 on a duplicate applicant and 422 on an invalid one, and the route"
                + " itself answers 200/201/202 depending on how the application resolved; a"
                + " consumer reading only the contract has no way to know any of that")
        .contains("200", "201", "202", "409", "422");
  }

  @Test
  @DisplayName("GET /api/signup/{onboardingId} documents 404 for an unknown id")
  void signUpStatusDocumentsNotFound() throws Exception {
    JsonNode responses = operationResponses("/api/signup/{onboardingId}", "get");

    assertThat(fieldNames(responses)).contains("200", "404", "422");
  }

  @Test
  @DisplayName("POST /api/webhooks/bureau documents 401 for an unverified signature")
  void bureauCallbackDocumentsUnauthorized() throws Exception {
    JsonNode responses = operationResponses("/api/webhooks/bureau", "post");

    assertThat(fieldNames(responses)).contains("200", "401", "404");
  }

  @Test
  @DisplayName("GET /api/account/me documents 401 for a missing or invalid token")
  void accountMeDocumentsUnauthorized() throws Exception {
    JsonNode responses = operationResponses("/api/account/me", "get");

    assertThat(fieldNames(responses)).contains("200", "401");
  }

  private JsonNode operationResponses(String path, String method) throws Exception {
    HttpResponse<String> apiDocs = get("/v3/api-docs");
    assertThat(apiDocs.statusCode()).isEqualTo(200);

    JsonNode document = json.readTree(apiDocs.body());
    JsonNode operation = document.path("paths").path(path).path(method);
    assertThat(operation.isMissingNode())
        .as("the operation itself must be documented before its responses can be")
        .isFalse();
    return operation.path("responses");
  }

  private static List<String> fieldNames(JsonNode object) {
    return object.propertyNames().stream().toList();
  }
}
