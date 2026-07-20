package com.fkbank.emulator.bureau;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The knobs a test or a demo turns to decide what the bureau will say next.
 *
 * <p>This surface has no counterpart at the real bureau and belongs to no product journey. It is
 * kept in its own controller, under its own path prefix, so that the separation is visible rather
 * than a matter of trust: nothing in the bank's code has any reason to reference this file.
 */
@RestController
@RequestMapping("/control")
class ControlController {

  private final BureauState state;

  ControlController(BureauState state) {
    this.state = state;
  }

  /** Chooses the answer for one applicant, or — with no CPF — for everyone else. */
  @PostMapping(
      value = "/scenario",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ScenarioAssignment assign(@Valid @RequestBody ScenarioAssignment assignment) {
    return state.assign(assignment);
  }

  @GetMapping(value = "/scenario", produces = MediaType.APPLICATION_JSON_VALUE)
  ScenarioSettings settings() {
    return state.settings();
  }

  /** Forgets every assignment and every delivered callback, back to the configured default. */
  @PostMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
  ScenarioSettings reset() {
    state.reset();
    return state.settings();
  }

  /** What the emulator has actually posted, so a test can assert on delivery rather than intent. */
  @GetMapping(value = "/callbacks", produces = MediaType.APPLICATION_JSON_VALUE)
  List<SentCallback> callbacks() {
    return state.callbacks();
  }
}
