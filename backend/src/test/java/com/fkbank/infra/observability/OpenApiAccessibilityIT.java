package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The API contract is browsable, not just internally validated (OR-4): the interactive UI and
 * the JSON document behind it are both public, listing every R0-R1 endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("OpenAPI UI and document")
class OpenApiAccessibilityIT {

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("/v3/api-docs is reachable without a token and lists the app's routes")
  void apiDocsIsPublicAndListsRoutes() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"/api/me\"").contains("\"/api/version\"");
  }

  @Test
  @DisplayName("the Swagger UI is reachable without a token")
  void swaggerUiIsPublic() throws Exception {
    HttpResponse<String> response = get("/swagger-ui/index.html");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Swagger UI");
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
