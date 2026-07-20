package com.fkbank.infra.security;

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
 * The walking skeleton's security thread, end to end, against the real configuration.
 *
 * <p>Deliberately <em>not</em> a mocked security slice: the acceptance criteria are about what
 * an actual HTTP client experiences, and a {@code @WebMvcTest} with a stubbed principal would
 * pass even if the filter chain were wired wrong. This boots the whole application, drives a
 * genuine authorization-code + PKCE exchange against the embedded Authorization Server, and
 * calls the protected route with the token it issued.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("GET /api/me")
class MeEndpointSecurityIT {

  private static final String SEEDED_USERNAME = "e2e.user";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("answers 401 without a token, in the standard error contract")
  void unauthenticatedIsRejected() throws Exception {
    HttpResponse<String> response = get("/api/me", null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));
    assertThat(response.body()).contains("\"status\":401").contains("\"title\"");
  }

  @Test
  @DisplayName("leaks no stack trace or internal detail to an unauthenticated caller")
  void unauthenticatedResponseLeaksNothing() throws Exception {
    HttpResponse<String> response = get("/api/me", null);

    assertThat(response.body())
        .doesNotContain("Exception")
        .doesNotContain("at com.fkbank")
        .doesNotContain("org.springframework")
        .doesNotContain("java.lang");
  }

  @Test
  @DisplayName("answers 401 for a token that is not a token")
  void garbageTokenIsRejected() throws Exception {
    HttpResponse<String> response = get("/api/me", "not-a-real-token");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("answers 200 with the username for a token obtained through the PKCE flow")
  void tokenFromThePkceFlowIsAccepted() throws Exception {
    String accessToken = new PkceTokenFlow(port).obtainAccessToken();

    HttpResponse<String> response = get("/api/me", accessToken);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"username\":\"" + SEEDED_USERNAME + "\"}");
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
