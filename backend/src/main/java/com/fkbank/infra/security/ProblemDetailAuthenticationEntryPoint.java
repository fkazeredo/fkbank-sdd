package com.fkbank.infra.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers unauthenticated API calls with the standard {@code application/problem+json} error
 * contract.
 *
 * <p>The body is built field by field on purpose: it must never carry a stack trace, an
 * exception class name or any other internal detail that would tell an unauthenticated caller
 * how the system is put together. Every call here is also an authorization failure worth
 * counting, so it increments a metric before writing the response.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final Counter authorizationFailures;

  ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    this.objectMapper = objectMapper;
    this.authorizationFailures =
        Counter.builder("fkbank.authorization.failures")
            .description("Requests rejected for missing or invalid authentication")
            .register(meterRegistry);
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {

    authorizationFailures.increment();

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
    problem.setTitle("Unauthorized");
    problem.setType(URI.create("https://fkbank.example/problems/unauthorized"));
    problem.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("WWW-Authenticate", "Bearer");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
