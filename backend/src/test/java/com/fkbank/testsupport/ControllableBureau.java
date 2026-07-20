package com.fkbank.testsupport;

import com.fkbank.domain.onboarding.BureauCheck;
import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.Onboarding;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A bureau that answers whatever a test tells it to.
 *
 * <p>The real client is exercised against the emulator over a real network, in the emulator's own
 * contract tests and in the end-to-end journey. What the tests here need instead is to choose an
 * answer — including "no answer" — without depending on a container being up, so that a test
 * about what the bank does with a refusal is not also a test of whether a socket was listening.
 */
public class ControllableBureau implements BureauCheck {

  private final AtomicReference<BureauDecision> answer =
      new AtomicReference<>(BureauDecision.approved());

  private final AtomicInteger enquiries = new AtomicInteger();

  @Override
  public BureauDecision decide(Onboarding onboarding) {
    enquiries.incrementAndGet();
    return answer.get();
  }

  public void willAnswer(BureauDecision decision) {
    answer.set(decision);
  }

  /** How many times the bureau was actually asked, which is what proves a check was skipped. */
  public int enquiries() {
    return enquiries.get();
  }

  public void reset() {
    answer.set(BureauDecision.approved());
    enquiries.set(0);
  }

  /** Replaces the real client for tests that import it. */
  @TestConfiguration
  public static class Configuration {

    @Bean
    @Primary
    ControllableBureau controllableBureau() {
      return new ControllableBureau();
    }
  }
}
