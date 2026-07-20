package com.fkbank.infra.observability;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fixes the OpenAPI document's declared server to the app's own configured origin, the same one
 * already used as the OAuth2 issuer.
 *
 * <p>Without this, springdoc derives the server URL from whichever host and port answered the
 * request, so the same document would list a different URL depending on who asked - and, in
 * tests, a different URL on every run, since the test server binds to a random port. Deriving it
 * from configuration instead makes the document deterministic, which is what a committed
 * drift-gate snapshot needs.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  @Bean
  OpenAPI openApiDocument(
      @Value("${spring.security.oauth2.authorizationserver.issuer}") String issuer) {
    return new OpenAPI().servers(List.of(new Server().url(issuer)));
  }
}
