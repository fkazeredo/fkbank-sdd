package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PkceTokenFlow;
import com.fkbank.testsupport.PostgresContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The Actuator surface follows the opposite default from the rest of the app: health is public,
 * everything else (Prometheus included) stays behind the same bearer-token wall as the API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("Actuator route security")
class ObservabilityRouteSecurityIT {

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("/actuator/health is reachable without a token")
  void healthIsPublic() throws Exception {
    HttpResponse<String> response = get("/actuator/health", null);

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("/actuator/prometheus answers 401 without a token")
  void prometheusIsProtected() throws Exception {
    HttpResponse<String> response = get("/actuator/prometheus", null);

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("/actuator/prometheus serves exposition text, with the authorization-failures counter present after one 401")
  void prometheusExposesTheAuthorizationFailuresCounter() throws Exception {
    get("/actuator/prometheus", null); // one exercised 401 to give the counter a data point

    String accessToken = new PkceTokenFlow(port).obtainAccessToken();
    HttpResponse<String> response = get("/actuator/prometheus", accessToken);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("fkbank_authorization_failures_total");
  }

  private HttpResponse<String> get(String path, String bearerToken) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(15))
            .GET();
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
