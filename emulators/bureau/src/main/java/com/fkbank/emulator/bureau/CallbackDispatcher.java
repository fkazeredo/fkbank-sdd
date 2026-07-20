package com.fkbank.emulator.bureau;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Posts the bureau's late answer back to the caller, signed.
 *
 * <p>The payload is serialized exactly once and every copy sent is those same bytes with that same
 * signature. Serializing per delivery would be the easy way to hand the receiver two bodies that
 * differ in some invisible way, which would make a redelivery look like a fresh decision — the
 * opposite of what the duplicate scenario is for.
 *
 * <p>The destination is taken from configuration and never from the request. A caller that could
 * name the address would be able to point signed traffic at any host the emulator can reach.
 */
@Component
class CallbackDispatcher {

  private static final Logger log = LoggerFactory.getLogger(CallbackDispatcher.class);

  private final BureauProperties properties;
  private final BureauState state;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  CallbackDispatcher(BureauProperties properties, BureauState state, ObjectMapper objectMapper) {
    this.properties = properties;
    this.state = state;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  /** Delivers the payload the given number of times, byte for byte identically. */
  void deliver(CallbackPayload payload, int copies) {
    byte[] body = objectMapper.writeValueAsBytes(payload);
    String signature = CallbackSignature.of(body, properties.getHmacSecret());
    String bodyAsSent = new String(body, StandardCharsets.UTF_8);

    for (int copy = 0; copy < copies; copy++) {
      state.record(post(body, signature, bodyAsSent));
    }
  }

  private SentCallback post(byte[] body, String signature, String bodyAsSent) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(properties.getCallbackUrl()))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header(CallbackSignature.HEADER, signature)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

    try {
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return new SentCallback(bodyAsSent, signature, response.statusCode(), null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(bodyAsSent, signature, e);
    } catch (Exception e) {
      // A receiver that is down or slow is a normal thing for an emulator to meet, and it must not
      // turn into a failed inquiry: the attempt is recorded so a test can see it and the emulator
      // keeps serving.
      return failed(bodyAsSent, signature, e);
    }
  }

  private SentCallback failed(String bodyAsSent, String signature, Exception cause) {
    log.warn("Callback delivery to {} failed: {}", properties.getCallbackUrl(), cause.toString());
    return new SentCallback(bodyAsSent, signature, null, cause.toString());
  }
}
