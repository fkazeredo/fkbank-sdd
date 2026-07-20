package com.fkbank.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PostgresContainer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
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

  private static final String CLIENT_ID = "fkbank-spa";
  private static final String REDIRECT_URI = "http://127.0.0.1:8090/auth/callback";
  private static final String SEEDED_USERNAME = "e2e.user";
  private static final String SEEDED_PASSWORD = "e2e-password";

  // The generated login form writes the hidden input's attributes in no guaranteed order
  // (`type="hidden"` sits between `name` and `value`), so match either arrangement rather than
  // depending on the exact markup of a framework-rendered page.
  private static final Pattern CSRF_INPUT =
      Pattern.compile(
          "name=\"_csrf\"[^>]*?value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*?name=\"_csrf\"");
  private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

  @LocalServerPort private int port;

  private HttpClient client;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @BeforeEach
  void freshClientPerTest() {
    // A cookie jar per test keeps the session of one scenario from authenticating another.
    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
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
    String accessToken = authorizationCodeWithPkce();

    HttpResponse<String> response = get("/api/me", accessToken);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"username\":\"" + SEEDED_USERNAME + "\"}");
  }

  /**
   * Drives the real authorization-code + PKCE exchange and returns the issued access token.
   *
   * <p>Every step is the one a browser performs: sign in at the form, hit the authorize
   * endpoint, then exchange the code together with the verifier that proves this client started
   * the flow.
   */
  private String authorizationCodeWithPkce() throws Exception {
    String verifier = randomUrlSafe();
    String challenge = s256(verifier);

    signIn();

    String authorizeQuery =
        query(
            Map.of(
                "response_type", "code",
                "client_id", CLIENT_ID,
                "scope", "openid profile",
                "redirect_uri", REDIRECT_URI,
                "code_challenge", challenge,
                "code_challenge_method", "S256"));

    HttpResponse<String> authorized = send(request("/oauth2/authorize?" + authorizeQuery).GET());
    assertThat(authorized.statusCode())
        .as("the authorize endpoint must redirect back to the SPA with a code")
        .isEqualTo(302);

    String location =
        authorized.headers().firstValue("Location").orElseThrow(() -> new AssertionError(
            "the authorize redirect carried no Location header"));
    assertThat(location).startsWith(REDIRECT_URI);
    String code = parameterFrom(location, "code");

    String tokenForm =
        query(
            Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", REDIRECT_URI,
                "client_id", CLIENT_ID,
                "code_verifier", verifier));

    HttpResponse<String> token =
        send(
            request("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenForm)));

    assertThat(token.statusCode()).as("token exchange failed: %s", token.body()).isEqualTo(200);

    Matcher matcher = ACCESS_TOKEN.matcher(token.body());
    assertThat(matcher.find()).as("no access_token in the token response").isTrue();
    return matcher.group(1);
  }

  /** Authenticates the seeded person at the generated login form. */
  private void signIn() throws Exception {
    HttpResponse<String> loginPage = send(request("/login").GET());
    Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
    assertThat(csrf.find())
        .as("the login page carried no CSRF token; page was:%n%s", loginPage.body())
        .isTrue();
    String csrfToken = csrf.group(1) != null ? csrf.group(1) : csrf.group(2);

    String form =
        query(
            Map.of(
                "username", SEEDED_USERNAME,
                "password", SEEDED_PASSWORD,
                "_csrf", csrfToken));

    HttpResponse<String> signedIn =
        send(
            request("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));

    assertThat(signedIn.statusCode()).isEqualTo(302);
    assertThat(signedIn.headers().firstValue("Location"))
        .as("a failed sign-in redirects back to /login?error")
        .hasValueSatisfying(target -> assertThat(target).doesNotContain("error"));
  }

  private HttpResponse<String> get(String path, String bearerToken) throws Exception {
    HttpRequest.Builder builder = request(path).GET();
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    return send(builder);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(15));
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String query(Map<String, String> parameters) {
    Map<String, String> ordered = new LinkedHashMap<>(parameters);
    StringBuilder builder = new StringBuilder();
    ordered.forEach(
        (key, value) -> {
          if (!builder.isEmpty()) {
            builder.append('&');
          }
          builder
              .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
    return builder.toString();
  }

  private static String parameterFrom(String url, String name) {
    Matcher matcher = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
    if (!matcher.find()) {
      throw new AssertionError("no '" + name + "' parameter in " + url);
    }
    return java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
  }

  private static String randomUrlSafe() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String s256(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }
}
