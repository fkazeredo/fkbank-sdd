package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PostgresContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The CI drift gate (OR-4): the OpenAPI JSON the app serves must be the exact document this
 * snapshot holds. A later endpoint change that doesn't regenerate the snapshot fails here
 * instead of silently drifting from what the Swagger UI shows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("OpenAPI document matches the committed snapshot")
class OpenApiSnapshotIT {

  private static final Path SNAPSHOT =
      Path.of("src/test/resources/openapi/openapi-snapshot.json");

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  /**
   * Rewrites the snapshot instead of comparing against it.
   *
   * <p>Every slice that adds an endpoint has to regenerate this file, and without a supported way
   * to do it the next person edits the assertion out, runs it, and puts it back — which is the
   * same thing done in a way that can be forgotten halfway. Naming the flag makes the
   * regeneration a deliberate, visible act: it shows up in the diff as a changed snapshot, and
   * the gate is still what runs by default.
   */
  private static final boolean REGENERATE = Boolean.getBoolean("openapi.snapshot.update");

  @Test
  @DisplayName("/v3/api-docs is byte-for-byte identical to the committed snapshot")
  void servedDocumentMatchesSnapshot() throws Exception {
    String served = fetchApiDocs();

    if (REGENERATE) {
      Files.writeString(SNAPSHOT, served, StandardCharsets.UTF_8);
    }

    String snapshot = Files.readString(SNAPSHOT, StandardCharsets.UTF_8);

    assertThat(served)
        .as(
            "the served OpenAPI document drifted from %s - regenerate it deliberately if this"
                + " endpoint change was intentional",
            SNAPSHOT)
        .isEqualTo(snapshot);
  }

  private String fetchApiDocs() throws IOException, InterruptedException {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    return response.body();
  }
}
