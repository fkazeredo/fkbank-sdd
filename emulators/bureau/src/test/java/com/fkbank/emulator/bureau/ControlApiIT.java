package com.fkbank.emulator.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the surface that exists only for tests and demos: choosing what the bureau will say,
 * reading back what it has been told, and putting it all back.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"bureau.default-scenario=approve", "bureau.delay=50ms"})
@DisplayName("/control")
class ControlApiIT {

  private static final String CPF = "12345678909";
  private static final ObjectMapper JSON = new ObjectMapper();

  @LocalServerPort private int port;

  @BeforeEach
  void startFromAKnownState() throws Exception {
    post("/control/reset", "{}");
  }

  @Test
  @DisplayName("echoes a per-applicant assignment as it was stored")
  void echoesAPerApplicantAssignment() throws Exception {
    HttpResponse<String> response =
        post("/control/scenario", "{\"scenario\":\"decline\",\"cpf\":\"123.456.789-09\"}");

    assertThat(response.statusCode()).as("status").isEqualTo(200);
    JsonNode echo = JSON.readTree(response.body());
    assertThat(echo.get("scenario").asString()).as("stored scenario").isEqualTo("decline");
    assertThat(echo.get("cpf").asString()).as("stored CPF, reduced to digits").isEqualTo(CPF);
  }

  @Test
  @DisplayName("treats an assignment with no CPF as the global default")
  void echoesAGlobalAssignment() throws Exception {
    HttpResponse<String> response = post("/control/scenario", "{\"scenario\":\"delay\"}");

    JsonNode echo = JSON.readTree(response.body());
    assertThat(echo.get("scenario").asString()).as("stored scenario").isEqualTo("delay");
    assertThat(echo.get("cpf").isNull()).as("no CPF means the global default").isTrue();
  }

  @Test
  @DisplayName("reports the default alongside every per-applicant override")
  void reportsTheWholeConfiguration() throws Exception {
    post("/control/scenario", "{\"scenario\":\"delay\"}");
    post("/control/scenario", "{\"scenario\":\"decline\",\"cpf\":\"%s\"}".formatted(CPF));

    JsonNode settings = JSON.readTree(get("/control/scenario").body());

    assertThat(settings.get("defaultScenario").asString()).as("default").isEqualTo("delay");
    assertThat(settings.get("perCpf").get(CPF).asString()).as("override").isEqualTo("decline");
  }

  @Test
  @DisplayName("puts everything back to the configured default on reset")
  void resetsToTheConfiguredDefault() throws Exception {
    post("/control/scenario", "{\"scenario\":\"delay\"}");
    post("/control/scenario", "{\"scenario\":\"decline\",\"cpf\":\"%s\"}".formatted(CPF));

    HttpResponse<String> response = post("/control/reset", "{}");

    assertThat(response.statusCode()).as("reset status").isEqualTo(200);
    JsonNode settings = JSON.readTree(response.body());
    assertThat(settings.get("defaultScenario").asString())
        .as("default after reset")
        .isEqualTo("approve");
    assertThat(settings.get("perCpf").isEmpty()).as("overrides after reset").isTrue();
    assertThat(JSON.readTree(get("/control/callbacks").body()).isEmpty())
        .as("callbacks after reset")
        .isTrue();
  }

  @Test
  @DisplayName("refuses a scenario it does not implement")
  void refusesAnUnknownScenario() throws Exception {
    HttpResponse<String> response = post("/control/scenario", "{\"scenario\":\"explode\"}");

    assertThat(response.statusCode()).as("status for an unknown scenario").isEqualTo(400);
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
