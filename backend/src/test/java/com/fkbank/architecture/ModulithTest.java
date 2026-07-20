package com.fkbank.architecture;

import com.fkbank.FkbankApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith's own view of the modular monolith.
 *
 * <p>Complements the ArchUnit suite rather than repeating it: ArchUnit enforces the package
 * law, while {@code verify()} checks that bounded contexts collaborate only through their
 * declared interfaces and that the module graph stays acyclic.
 */
class ModulithTest {

  @Test
  void boundedContextsRespectTheirDeclaredBoundaries() {
    ApplicationModules.of(FkbankApplication.class).verify();
  }
}
