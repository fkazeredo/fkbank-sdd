package com.fkbank.testsupport;

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

/**
 * Drives the real authorization-code + PKCE exchange against the embedded Authorization Server
 * and returns the access token it issues, the same way a browser would obtain one.
 *
 * <p>Deliberately not a mocked security slice: every step here is one an actual HTTP client
 * performs, so a test using this helper exercises the real filter chain rather than a stubbed
 * principal.
 */
public final class PkceTokenFlow {

  private static final String CLIENT_ID = "fkbank-spa";
  private static final String REDIRECT_URI = "http://127.0.0.1:8090/auth/callback";

  // The generated login form writes the hidden input's attributes in no guaranteed order
  // (`type="hidden"` sits between `name` and `value`), so match either arrangement rather than
  // depending on the exact markup of a framework-rendered page.
  private static final Pattern CSRF_INPUT =
      Pattern.compile(
          "name=\"_csrf\"[^>]*?value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*?name=\"_csrf\"");
  private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

  private final int port;
  private final HttpClient client;

  public PkceTokenFlow(int port) {
    this.port = port;
    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    this.client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  /**
   * Obtains an access token for a real credential.
   *
   * <p>The credential has to have been issued by opening an account: there is no demo user any
   * more, so a test that wants a token signs somebody up first. That makes every authenticated
   * test exercise the path a real person takes.
   *
   * @param username what the person signs in with, which is their e-mail address
   * @param password the password they chose at sign-up
   */
  public String obtainAccessToken(String username, String password) throws Exception {
    String verifier = randomUrlSafe();
    String challenge = s256(verifier);

    signIn(username, password);

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
    if (authorized.statusCode() != 302) {
      throw new IllegalStateException(
          "the authorize endpoint must redirect back to the SPA with a code, got "
              + authorized.statusCode());
    }

    String location =
        authorized
            .headers()
            .firstValue("Location")
            .orElseThrow(() -> new IllegalStateException("the authorize redirect carried no Location header"));
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

    if (token.statusCode() != 200) {
      throw new IllegalStateException("token exchange failed: " + token.body());
    }

    Matcher matcher = ACCESS_TOKEN.matcher(token.body());
    if (!matcher.find()) {
      throw new IllegalStateException("no access_token in the token response: " + token.body());
    }
    return matcher.group(1);
  }

  private void signIn(String username, String password) throws Exception {
    HttpResponse<String> loginPage = send(request("/login").GET());
    Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
    if (!csrf.find()) {
      throw new IllegalStateException("the login page carried no CSRF token; page was:\n" + loginPage.body());
    }
    String csrfToken = csrf.group(1) != null ? csrf.group(1) : csrf.group(2);

    String form =
        query(
            Map.of(
                "username", username,
                "password", password,
                "_csrf", csrfToken));

    HttpResponse<String> signedIn =
        send(
            request("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));

    if (signedIn.statusCode() != 302
        || signedIn.headers().firstValue("Location").map(target -> target.contains("error")).orElse(true)) {
      throw new IllegalStateException("sign-in failed: " + signedIn.statusCode());
    }
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
      throw new IllegalStateException("no '" + name + "' parameter in " + url);
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
