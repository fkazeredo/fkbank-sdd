package com.fkbank.emulator.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the business API over real HTTP and inspects the callbacks on the wire.
 *
 * <p>The delay is shortened to a few hundred milliseconds here. Its production value is chosen to
 * outlast the caller's timeout, which is a property of the pair rather than of this service, and
 * paying five seconds per slow scenario would buy the suite nothing it does not already prove.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "bureau.default-scenario=approve",
      "bureau.delay=300ms",
      "bureau.hmac-secret=integration-test-secret",
      "bureau.seed=99"
    })
@DisplayName("POST /inquiries")
class InquiryScenarioIT {

  private static final String SECRET = "integration-test-secret";
  private static final String CPF = "12345678909";
  private static final CallbackReceiver RECEIVER = CallbackReceiver.start();
  private static final ObjectMapper JSON = new ObjectMapper();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void callbackDestination(DynamicPropertyRegistry registry) {
    registry.add("bureau.callback-url", RECEIVER::url);
  }

  @BeforeEach
  void startFromAKnownState() throws Exception {
    post("/control/reset", "{}");
    RECEIVER.forgetEverythingReceived();
  }

  @Test
  @DisplayName("approves under the approve scenario, with no reason category")
  void approves() throws Exception {
    JsonNode body = JSON.readTree(inquire("ref-approve", CPF).body());

    assertThat(body.get("outcome").asString()).as("outcome").isEqualTo("APPROVED");
    assertThat(body.get("reasonCategory").isNull()).as("reasonCategory is present and null").isTrue();
    assertThat(UUID.fromString(body.get("inquiryId").asString())).as("inquiryId").isNotNull();
  }

  @Test
  @DisplayName("rejects with a reason category under the decline scenario")
  void declines() throws Exception {
    selectScenario("decline", CPF);

    JsonNode body = JSON.readTree(inquire("ref-decline", CPF).body());

    assertThat(body.get("outcome").asString()).as("outcome").isEqualTo("REJECTED");
    assertThat(body.get("reasonCategory").asString())
        .as("reason category")
        .isEqualTo("DOCUMENT_MISMATCH");
  }

  @Test
  @DisplayName("keeps the applicant's own scenario ahead of the global default")
  void perApplicantScenarioWins() throws Exception {
    selectScenario("decline", null);
    selectScenario("approve", CPF);

    assertThat(JSON.readTree(inquire("ref-mine", CPF).body()).get("outcome").asString())
        .as("outcome for the named applicant")
        .isEqualTo("APPROVED");
    assertThat(JSON.readTree(inquire("ref-other", "98765432100").body()).get("outcome").asString())
        .as("outcome for everyone else")
        .isEqualTo("REJECTED");
  }

  @Test
  @DisplayName("answers late under the delay scenario, then tells the caller by signed callback")
  void answersLateAndCallsBack() throws Exception {
    selectScenario("delay", CPF);

    long startedAt = System.nanoTime();
    HttpResponse<String> response = inquire("ref-delay", CPF);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(elapsed).as("time taken to answer").isGreaterThanOrEqualTo(Duration.ofMillis(300));
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("outcome").asString()).as("outcome").isEqualTo("APPROVED");

    List<CallbackReceiver.Received> callbacks = RECEIVER.received();
    assertThat(callbacks).as("callbacks delivered").hasSize(1);

    CallbackReceiver.Received callback = callbacks.getFirst();
    JsonNode callbackBody = JSON.readTree(new String(callback.body(), StandardCharsets.UTF_8));
    assertThat(callbackBody.get("reference").asString()).as("echoed reference").isEqualTo("ref-delay");
    assertThat(callbackBody.get("inquiryId").asString())
        .as("the id of the inquiry that timed out")
        .isEqualTo(body.get("inquiryId").asString());
    assertThat(callbackBody.get("outcome").asString()).as("callback outcome").isEqualTo("APPROVED");
    assertThat(callbackBody.get("reasonCategory").isNull())
        .as("callback reasonCategory is present and null")
        .isTrue();
  }

  @Test
  @DisplayName("signs the exact bytes it sent, so the receiver's own HMAC matches")
  void signsWhatItSent() throws Exception {
    selectScenario("delay", CPF);
    inquire("ref-signature", CPF);

    CallbackReceiver.Received callback = RECEIVER.received().getFirst();

    assertThat(callback.signature())
        .as("signature recomputed by the receiver over the bytes it read")
        .isEqualTo(CallbackSignature.of(callback.body(), SECRET));
  }

  @Test
  @DisplayName("a body altered in transit no longer matches the signature that came with it")
  void aTamperedBodyFailsVerification() throws Exception {
    selectScenario("delay", CPF);
    inquire("ref-tamper", CPF);

    CallbackReceiver.Received callback = RECEIVER.received().getFirst();
    byte[] tampered =
        new String(callback.body(), StandardCharsets.UTF_8)
            .replace("APPROVED", "REJECTED")
            .getBytes(StandardCharsets.UTF_8);

    assertThat(CallbackSignature.of(tampered, SECRET))
        .as("signature over the altered body")
        .isNotEqualTo(callback.signature());
  }

  @Test
  @DisplayName("a signature computed with the wrong secret does not match either")
  void theWrongSecretFailsVerification() throws Exception {
    selectScenario("delay", CPF);
    inquire("ref-secret", CPF);

    CallbackReceiver.Received callback = RECEIVER.received().getFirst();

    assertThat(CallbackSignature.of(callback.body(), "not-the-secret"))
        .as("signature under the wrong secret")
        .isNotEqualTo(callback.signature());
  }

  @Test
  @DisplayName("delivers the same callback twice under the duplicate-webhook scenario")
  void deliversTheSameCallbackTwice() throws Exception {
    selectScenario("duplicate-webhook", CPF);

    inquire("ref-duplicate", CPF);

    List<CallbackReceiver.Received> callbacks = RECEIVER.received();
    assertThat(callbacks).as("callbacks delivered").hasSize(2);
    assertThat(callbacks.get(1).body())
        .as("the redelivered body, byte for byte")
        .isEqualTo(callbacks.getFirst().body());
    assertThat(callbacks.get(1).signature())
        .as("the redelivered signature")
        .isEqualTo(callbacks.getFirst().signature());
  }

  @Test
  @DisplayName("records every delivery, and the receiver's answer to it")
  void recordsWhatItDelivered() throws Exception {
    selectScenario("duplicate-webhook", CPF);
    inquire("ref-recorded", CPF);

    JsonNode recorded = JSON.readTree(get("/control/callbacks").body());

    assertThat(recorded.size()).as("recorded deliveries").isEqualTo(2);
    assertThat(recorded.get(0).get("statusCode").asInt()).as("receiver's response").isEqualTo(200);
    assertThat(recorded.get(0).get("error").isNull()).as("no delivery error").isTrue();
    assertThat(recorded.get(1).get("body").asString())
        .as("the second recorded body")
        .isEqualTo(recorded.get(0).get("body").asString());
    assertThat(recorded.get(1).get("signature").asString())
        .as("the second recorded signature")
        .isEqualTo(recorded.get(0).get("signature").asString());
  }

  @Test
  @DisplayName("refuses an inquiry that is missing the fields a bureau needs")
  void refusesAnIncompleteInquiry() throws Exception {
    HttpResponse<String> response = post("/inquiries", "{\"reference\":\"ref-empty\"}");

    assertThat(response.statusCode()).as("status for a request with no CPF").isEqualTo(400);
  }

  private HttpResponse<String> inquire(String reference, String cpf) throws Exception {
    String body =
        """
        {"reference":"%s","cpf":"%s","fullName":"Ada Lovelace","birthDate":"1990-05-20"}
        """
            .formatted(reference, cpf);
    HttpResponse<String> response = post("/inquiries", body);
    assertThat(response.statusCode()).as("inquiry status").isEqualTo(200);
    return response;
  }

  private void selectScenario(String scenario, String cpf) throws Exception {
    String body =
        cpf == null
            ? "{\"scenario\":\"%s\"}".formatted(scenario)
            : "{\"scenario\":\"%s\",\"cpf\":\"%s\"}".formatted(scenario, cpf);
    assertThat(post("/control/scenario", body).statusCode())
        .as("scenario selection status")
        .isEqualTo(200);
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
