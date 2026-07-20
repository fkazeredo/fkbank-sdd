package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private static final String HEADER = "X-Correlation-Id";

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesAnIdWhenNoHeaderIsSent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(HEADER)).isNotBlank();
  }

  @Test
  void echoesAWellFormedIncomingId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    request.addHeader(HEADER, "client-generated-id-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(HEADER)).isEqualTo("client-generated-id-123");
  }

  @Test
  void acceptsExactlySixtyFourCharacters() throws Exception {
    String sixtyFour = "a".repeat(64);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    request.addHeader(HEADER, sixtyFour);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(HEADER)).isEqualTo(sixtyFour);
  }

  @Test
  void regeneratesAnIdOneCharacterPastTheLimit() throws Exception {
    String sixtyFive = "a".repeat(65);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    request.addHeader(HEADER, sixtyFive);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(HEADER)).isNotEqualTo(sixtyFive);
  }

  @Test
  void regeneratesAnIdContainingControlCharacters() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    request.addHeader(HEADER, "id-with-\r\ninjected-header");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(HEADER)).doesNotContain("\r", "\n");
  }

  @Test
  void everyLogLineDuringTheRequestCarriesTheCorrelationIdInMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] observedDuringRequest = new String[1];
    FilterChain chain = (req, res) -> observedDuringRequest[0] = MDC.get("correlationId");

    filter.doFilter(request, response, chain);

    assertThat(observedDuringRequest[0]).isEqualTo(response.getHeader(HEADER));
  }

  @Test
  void clearsTheMdcAfterTheRequestSoAPooledThreadNeverLeaksIntoTheNextOne() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void twoSequentialRequestsOnTheSameThreadNeverShareAnId() throws Exception {
    String[] ids =
        IntStream.range(0, 2)
            .mapToObj(
                i -> {
                  try {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    filter.doFilter(request, response, new MockFilterChain());
                    return response.getHeader(HEADER);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(String[]::new);

    assertThat(ids[0]).isNotEqualTo(ids[1]);
  }
}
