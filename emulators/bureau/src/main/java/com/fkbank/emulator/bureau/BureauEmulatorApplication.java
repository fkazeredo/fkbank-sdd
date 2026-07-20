package com.fkbank.emulator.bureau;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * A stand-in for the external KYC credit bureau.
 *
 * <p>It exists so the bank's own code can be exercised against a real network hop, a real
 * timeout and a real signed callback, rather than against an in-process stub that proves none of
 * those work. Two surfaces live here and are kept apart on purpose: the business API mimics the
 * contract the bureau really publishes, and the control API under {@code /control} only exists so
 * a test or a demo can choose which answer comes back. Nothing in the bank calls the control API
 * — if it ever did, the bank would know it is talking to an emulator, which is exactly what this
 * service is built to avoid.
 *
 * <p>All state is in memory and dies with the process, and anything random is derived from a
 * configured seed, so a run reproduces exactly.
 */
@SpringBootApplication
@EnableConfigurationProperties(BureauProperties.class)
public class BureauEmulatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(BureauEmulatorApplication.class, args);
  }
}
