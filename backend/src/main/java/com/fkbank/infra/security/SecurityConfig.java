package com.fkbank.infra.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * The authorization baseline every later slice inherits.
 *
 * <p>Two filter chains, in order:
 *
 * <ol>
 *   <li>the Authorization Server's own endpoints, which must be matched first so the token and
 *       authorize endpoints are not swallowed by the application's default-deny rule;
 *   <li>the application, where <strong>every</strong> route requires authentication.
 * </ol>
 *
 * <p>The public allowlist is deliberately empty in this slice. {@code /login} and {@code /error}
 * are not on it: they are authentication and error-dispatch infrastructure rendered by the
 * framework rather than application routes, and the route-permission completeness test asserts
 * that every route FKBANK itself registers is covered.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class SecurityConfig {

  /**
   * The OAuth2/OIDC endpoints of the embedded Authorization Server.
   *
   * <p>A browser asking for HTML is sent to the login page; anything else gets the protocol's
   * own error responses.
   */
  @Bean
  @Order(1)
  SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServer =
        new OAuth2AuthorizationServerConfigurer();

    http.securityMatcher(authorizationServer.getEndpointsMatcher())
        .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        // The authorization endpoints are driven by redirects and a token exchange, not by a
        // session-bound form post, so CSRF tokens do not apply to them.
        .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()))
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

    return http.build();
  }

  /**
   * The application itself: default-deny with a short, explicit public allowlist.
   *
   * <p>{@code anyRequest().authenticated()} remains the fallback for everything not named
   * above it — a route added later is protected because it exists, not because someone
   * remembered to protect it. An unauthenticated API call is answered by
   * {@link ProblemDetailAuthenticationEntryPoint} with {@code 401} and the standard error
   * contract.
   */
  @Bean
  @Order(2)
  SecurityFilterChain applicationSecurityFilterChain(
      HttpSecurity http, ProblemDetailAuthenticationEntryPoint problemDetailEntryPoint)
      throws Exception {

    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        pathPattern("/actuator/health"),
                        pathPattern("/api/version"),
                        pathPattern("/v3/api-docs/**"),
                        pathPattern("/swagger-ui/**"),
                        pathPattern("/swagger-ui.html"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // The API is authenticated by bearer token, never by an ambient cookie, so no CSRF
        // token is required for it. Form login keeps its CSRF protection.
        .csrf(
            csrf ->
                csrf.ignoringRequestMatchers(
                    pathPattern("/api/**")))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(problemDetailEntryPoint))
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    problemDetailEntryPoint,
                    pathPattern("/api/**")))
        // The login form is how a person authenticates at the Authorization Server; the SPA
        // reaches it through the PKCE redirect.
        .formLogin(Customizer.withDefaults());

    return http.build();
  }
}
