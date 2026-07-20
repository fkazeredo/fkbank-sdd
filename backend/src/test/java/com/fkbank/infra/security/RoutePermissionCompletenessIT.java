package com.fkbank.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PostgresContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Default-deny, proven by enumeration rather than by inspection.
 *
 * <p>Every HTTP route FKBANK registers is listed from the live handler mapping and then
 * actually called without credentials. A route that answers anything other than {@code 401}
 * while absent from the public allowlist is an unmapped route, and the count of those must be
 * zero. Adding an unprotected endpoint in a later slice therefore fails this test on the way
 * in, instead of being discovered by whoever finds it in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("Route permission completeness")
class RoutePermissionCompletenessIT {

  /**
   * The explicit public allowlist.
   *
   * <p>Anything added here is a deliberate, reviewable decision to expose a route. The sign-up
   * routes are public because an applicant has no credentials until their application succeeds;
   * the callback is public because the credit bureau holds no account here and authenticates by
   * signing its request body instead.
   */
  private static final Set<String> PUBLIC_ALLOWLIST =
      Set.of(
          "/api/version",
          "/api/signup",
          "/api/signup/{onboardingId}",
          "/api/webhooks/bureau");

  @LocalServerPort private int port;

  // Actuator registers its own ControllerEndpointHandlerMapping alongside the regular MVC one;
  // this test only cares about the routes FKBANK's own controllers register.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("registers no route that is reachable without authentication")
  void everyRouteIsCoveredByAPermission() throws Exception {
    List<String> applicationRoutes = applicationRoutes();

    assertThat(applicationRoutes)
        .as("the skeleton must expose its one protected route, or this test proves nothing")
        .contains("/api/me");

    HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    List<String> unmapped =
        applicationRoutes.stream()
            .filter(route -> !PUBLIC_ALLOWLIST.contains(route))
            .filter(route -> isReachableWithoutAuthentication(client, route))
            .toList();

    assertThat(unmapped)
        .as("routes without a permission mapping or allowlist entry")
        .isEmpty();
  }

  private boolean isReachableWithoutAuthentication(HttpClient client, String route) {
    try {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                  .timeout(Duration.ofSeconds(15))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      // 401 is the only acceptable answer for a protected route reached anonymously. A 403
      // would mean the request authenticated as somebody and then failed authorization, which
      // for an anonymous call would itself be a finding.
      return response.statusCode() != 401;
    } catch (Exception exception) {
      throw new IllegalStateException("could not probe " + route, exception);
    }
  }

  /**
   * The routes FKBANK itself registers.
   *
   * <p>Framework-provided handlers are excluded: Spring's error dispatch ({@code /error}) is an
   * internal forward rather than a route a client navigates to, and the generated login form
   * belongs to the authentication mechanism, not to the application's API surface. Filtering by
   * the handler's own package keeps the assertion about code this repository owns.
   */
  private List<String> applicationRoutes() {
    return handlerMapping.getHandlerMethods().entrySet().stream()
        .filter(entry -> isFkbankHandler(entry.getValue()))
        .map(entry -> firstPatternOf(entry.getKey()))
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  private static boolean isFkbankHandler(HandlerMethod handlerMethod) {
    return handlerMethod.getBeanType().getPackageName().startsWith("com.fkbank");
  }

  private static String firstPatternOf(RequestMappingInfo mappingInfo) {
    if (mappingInfo.getPathPatternsCondition() == null) {
      return null;
    }
    return mappingInfo.getPathPatternsCondition().getPatternValues().stream().findFirst().orElse(null);
  }
}
