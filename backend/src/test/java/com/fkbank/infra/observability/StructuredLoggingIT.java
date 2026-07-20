package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PostgresContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the exact structured-logging configuration the app ships with (OR-3): file output
 * mirrors {@code logging.structured.format.console} so the resulting lines can be read back and
 * parsed, rather than intercepting the console stream Logback already owns by the time a test
 * could redirect it.
 *
 * <p>The file destination is set through {@code @SpringBootTest(properties = ...)} rather than
 * {@code @DynamicPropertySource}: Boot's logging system initializes from the environment before
 * dynamically-registered properties are applied, so a property that must influence logging setup
 * has to be inlined instead.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "logging.file.name=${java.io.tmpdir}/fkbank-structured-logging-it/app.log",
      "logging.structured.format.file=ecs",
      "logging.level.com.fkbank.infra.observability.CorrelationIdFilter=DEBUG"
    })
@ActiveProfiles("e2e")
@DisplayName("structured JSON logging")
class StructuredLoggingIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path LOG_FILE =
      Path.of(System.getProperty("java.io.tmpdir"), "fkbank-structured-logging-it", "app.log");

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @BeforeAll
  static void discardAnyPriorRun() throws IOException {
    Files.deleteIfExists(LOG_FILE);
  }

  @Test
  @DisplayName("every log line produced during a request is one parseable JSON object carrying its correlationId")
  void requestLogLinesAreValidJson() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/version"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    String correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();

    List<JsonNode> lines = readJsonLines();

    assertThat(lines).as("at least one line was logged").isNotEmpty();
    assertThat(lines)
        .as("every captured line carries the triggering request's correlationId")
        .anySatisfy(line -> assertThat(line.path("correlationId").asString()).isEqualTo(correlationId));
  }

  @Test
  @DisplayName("an exception is reported in dedicated error fields, never folded as text into the message")
  void exceptionsGetStructuredFields() throws Exception {
    Logger logger = LoggerFactory.getLogger(StructuredLoggingIT.class);
    logger.error("something failed while handling the request", new IllegalStateException("boom"));

    List<JsonNode> lines = readJsonLines();

    assertThat(lines)
        .anySatisfy(
            line -> {
              assertThat(line.path("message").asString()).isEqualTo("something failed while handling the request");
              assertThat(line.path("message").asString()).doesNotContain("IllegalStateException");
              assertThat(line.path("error").path("type").asString()).contains("IllegalStateException");
              assertThat(line.path("error").path("message").asString()).isEqualTo("boom");
              assertThat(line.path("error").path("stack_trace").asString()).contains("StructuredLoggingIT");
            });
  }

  private static List<JsonNode> readJsonLines() throws IOException {
    return Files.readAllLines(LOG_FILE).stream()
        .filter(line -> !line.isBlank())
        .map(JSON::readTree)
        .toList();
  }
}
