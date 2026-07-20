package com.fkbank.infra.integration.bureau;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** How to reach the credit bureau, how long to wait for it, and how to recognize its callbacks. */
@ConfigurationProperties(prefix = "fkbank.bureau")
public class BureauProperties {

  /** The value that must be replaced before production is allowed to start. */
  public static final String DEV_DEFAULT_HMAC_SECRET = "dev-bureau-secret";

  private String baseUrl = "http://127.0.0.1:9101";

  /**
   * How long a person waits at the sign-up form before the answer is left to arrive by callback.
   *
   * <p>Short on purpose. Somebody is watching a spinner, and an application left open is
   * recoverable, whereas a request held for half a minute is a page that looks broken.
   */
  private Duration timeout = Duration.ofSeconds(3);

  private String hmacSecret = DEV_DEFAULT_HMAC_SECRET;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public String getHmacSecret() {
    return hmacSecret;
  }

  public void setHmacSecret(String hmacSecret) {
    this.hmacSecret = hmacSecret;
  }
}
