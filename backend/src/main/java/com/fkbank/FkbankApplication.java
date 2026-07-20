package com.fkbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FKBANK backend entry point.
 *
 * <p>The application is a modular monolith: exactly three packages sit below {@code com.fkbank}
 * ({@code domain}, {@code application}, {@code infra}) and the split is enforced by the
 * ArchUnit suite, not by convention.
 */
@SpringBootApplication
public class FkbankApplication {

  public static void main(String[] args) {
    SpringApplication.run(FkbankApplication.class, args);
  }
}
