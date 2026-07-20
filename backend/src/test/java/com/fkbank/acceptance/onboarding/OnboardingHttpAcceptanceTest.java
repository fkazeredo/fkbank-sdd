package com.fkbank.acceptance.onboarding;

import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.PostgresContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared wiring for the black-box, HTTP-level acceptance suite for sign-up and account opening.
 *
 * <p>Boots the whole application on a real port against real PostgreSQL, matching the way an
 * actual client reaches these routes: no {@code @WebMvcTest} slice, no mocked security context.
 * The bureau's synchronous answer is swapped for a controllable double so a test can choose
 * {@code approve}/{@code decline}/{@code undetermined} deterministically; the real network call
 * and the real timeout are exercised separately, against the real emulator, by the end-to-end
 * journey and by the emulator's own contract tests.
 *
 * <p>The bureau's asynchronous callback endpoint is exercised over real HTTP with a signature
 * computed independently of the production signer, using the default development secret the
 * {@code e2e} profile falls back to when no override is configured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(ControllableBureau.Configuration.class)
abstract class OnboardingHttpAcceptanceTest {

  /** The secret {@code application-e2e.yml} falls back to when nothing overrides it. */
  static final String CALLBACK_SECRET = "dev-bureau-secret";

  @LocalServerPort int port;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  HttpResponse<String> post(String path, String jsonBody) throws Exception {
    return send(
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)));
  }

  HttpResponse<String> postSigned(String path, String jsonBody, String secret) throws Exception {
    byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
    return send(
        request(path)
            .header("Content-Type", "application/json")
            .header("X-Bureau-Signature", sign(secret, bytes))
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)));
  }

  HttpResponse<String> postWithHeader(String path, String jsonBody, String headerName, String headerValue)
      throws Exception {
    HttpRequest.Builder builder =
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    if (headerValue != null) {
      builder.header(headerName, headerValue);
    }
    return send(builder);
  }

  HttpResponse<String> postRawNoSignatureHeader(String path, String jsonBody) throws Exception {
    return send(
        request(path)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)));
  }

  HttpResponse<String> get(String path) throws Exception {
    return send(request(path).GET());
  }

  HttpResponse<String> get(String path, String bearerToken) throws Exception {
    HttpRequest.Builder builder = request(path).GET();
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    return send(builder);
  }

  /**
   * Computes the callback signature independently of the production signer, so a mistake in the
   * implementation under test cannot be mirrored by the expectation it is checked against.
   */
  static String sign(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(15));
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
