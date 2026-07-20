package com.fkbank.infra.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id, present in every log line the request produces and
 * echoed back so the caller can quote it when reporting an issue.
 *
 * <p>An incoming id is trusted only when it is short and made of safe characters; anything else
 * (missing, too long, or carrying characters that could break out of a log line or a header) is
 * replaced with a freshly generated one rather than passed through unsanitized.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  static final String MDC_KEY = "correlationId";

  private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String correlationId = isWellFormed(incoming) ? incoming : UUID.randomUUID().toString();

    response.setHeader(HEADER, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static boolean isWellFormed(String value) {
    return value != null && WELL_FORMED.matcher(value).matches();
  }
}
