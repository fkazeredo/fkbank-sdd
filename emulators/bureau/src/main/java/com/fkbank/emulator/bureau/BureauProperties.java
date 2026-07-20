package com.fkbank.emulator.bureau;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything about this emulator that a deployment chooses: which answer it gives by default,
 * how slow it is, where its callback goes, what it signs with, and the seed that makes a run
 * reproducible.
 *
 * <p>The callback URL is configuration and never comes from a request. Letting a caller name the
 * address a signed request is delivered to would turn this service into a confused deputy that
 * happily posts authenticated traffic at whatever host was asked for.
 */
@ConfigurationProperties(prefix = "bureau")
public class BureauProperties {

  private Scenario defaultScenario = Scenario.APPROVE;

  /**
   * How long the slow scenarios take to answer. The default is deliberately longer than the
   * caller's own timeout, because the behaviour worth rehearsing is the caller giving up and then
   * being told the outcome by callback instead.
   */
  private Duration delay = Duration.ofSeconds(5);

  private String callbackUrl = "http://127.0.0.1:8080/api/webhooks/bureau";

  /**
   * The shared secret both sides sign and verify with. The default matches the bank's own
   * development default so the pair works with no configuration at all; anywhere that matters,
   * both sides are given a real one.
   */
  private String hmacSecret = "dev-bureau-secret";

  private long seed = 20260720L;

  public Scenario getDefaultScenario() {
    return defaultScenario;
  }

  public void setDefaultScenario(Scenario defaultScenario) {
    this.defaultScenario = defaultScenario;
  }

  public Duration getDelay() {
    return delay;
  }

  public void setDelay(Duration delay) {
    this.delay = delay;
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public String getHmacSecret() {
    return hmacSecret;
  }

  public void setHmacSecret(String hmacSecret) {
    this.hmacSecret = hmacSecret;
  }

  public long getSeed() {
    return seed;
  }

  public void setSeed(long seed) {
    this.seed = seed;
  }
}
