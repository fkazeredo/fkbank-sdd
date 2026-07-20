package com.fkbank.emulator.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BureauState")
class BureauStateTest {

  private static final String CPF = "12345678909";

  private static BureauState stateSeededWith(long seed) {
    BureauProperties properties = new BureauProperties();
    properties.setSeed(seed);
    return new BureauState(properties);
  }

  private static BureauState state() {
    return stateSeededWith(1L);
  }

  @Test
  @DisplayName("serves the configured default to an applicant nobody has said anything about")
  void fallsBackToTheConfiguredDefault() {
    BureauProperties properties = new BureauProperties();
    properties.setDefaultScenario(Scenario.DECLINE);

    assertThat(new BureauState(properties).scenarioFor(CPF))
        .as("scenario for an applicant with no assignment of their own")
        .isEqualTo(Scenario.DECLINE);
  }

  @Test
  @DisplayName("lets an applicant's own scenario beat the global default")
  void perCpfBeatsTheDefault() {
    BureauState state = state();
    state.assign(new ScenarioAssignment(Scenario.APPROVE, null));
    state.assign(new ScenarioAssignment(Scenario.DECLINE, CPF));

    assertThat(state.scenarioFor(CPF)).as("scenario for the named applicant").isEqualTo(Scenario.DECLINE);
    assertThat(state.scenarioFor("98765432100"))
        .as("scenario for everyone else")
        .isEqualTo(Scenario.APPROVE);
  }

  @Test
  @DisplayName("treats a formatted CPF and a bare one as the same applicant")
  void normalizesTheCpf() {
    BureauState state = state();
    state.assign(new ScenarioAssignment(Scenario.DECLINE, "123.456.789-09"));

    assertThat(state.scenarioFor(CPF)).as("scenario looked up without the formatting").isEqualTo(Scenario.DECLINE);
  }

  @Test
  @DisplayName("echoes the assignment as stored, with the CPF reduced to digits")
  void echoesWhatWasStored() {
    ScenarioAssignment stored =
        state().assign(new ScenarioAssignment(Scenario.DELAY, "123.456.789-09"));

    assertThat(stored).as("stored assignment").isEqualTo(new ScenarioAssignment(Scenario.DELAY, CPF));
  }

  @Test
  @DisplayName("reports the default and every override it is holding")
  void reportsItsSettings() {
    BureauState state = state();
    state.assign(new ScenarioAssignment(Scenario.DELAY, null));
    state.assign(new ScenarioAssignment(Scenario.DECLINE, CPF));

    ScenarioSettings settings = state.settings();

    assertThat(settings.defaultScenario()).as("default scenario").isEqualTo(Scenario.DELAY);
    assertThat(settings.perCpf())
        .as("per-applicant overrides")
        .containsExactly(Map.entry(CPF, Scenario.DECLINE));
  }

  @Test
  @DisplayName("forgets overrides, callbacks and the changed default when it is reset")
  void resetClearsEverything() {
    BureauProperties properties = new BureauProperties();
    properties.setDefaultScenario(Scenario.APPROVE);
    BureauState state = new BureauState(properties);
    state.assign(new ScenarioAssignment(Scenario.DECLINE, CPF));
    state.assign(new ScenarioAssignment(Scenario.DELAY, null));
    state.record(new SentCallback("{}", "sha256=abc", 200, null));

    state.reset();

    assertThat(state.settings().defaultScenario()).as("default after reset").isEqualTo(Scenario.APPROVE);
    assertThat(state.settings().perCpf()).as("overrides after reset").isEmpty();
    assertThat(state.callbacks()).as("callbacks after reset").isEmpty();
  }

  @Test
  @DisplayName("produces the same inquiry ids for the same seed, so a run can be replayed")
  void theSameSeedProducesTheSameSequence() {
    assertThat(tenIdsFrom(stateSeededWith(4242L)))
        .as("ids from a second run with the same seed")
        .isEqualTo(tenIdsFrom(stateSeededWith(4242L)));
  }

  @Test
  @DisplayName("produces a different sequence for a different seed")
  void aDifferentSeedProducesADifferentSequence() {
    assertThat(tenIdsFrom(stateSeededWith(4242L)))
        .as("ids from a run with another seed")
        .isNotEqualTo(tenIdsFrom(stateSeededWith(9999L)));
  }

  @Test
  @DisplayName("hands out distinct ids within a run")
  void idsAreDistinctWithinARun() {
    assertThat(tenIdsFrom(state())).as("ten consecutive ids").doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("restarts the id sequence on reset, so the replay covers the whole run")
  void resetRestartsTheIdSequence() {
    BureauState state = stateSeededWith(7L);
    List<UUID> before = tenIdsFrom(state);

    state.reset();

    assertThat(tenIdsFrom(state)).as("ids after reset").isEqualTo(before);
  }

  private static List<UUID> tenIdsFrom(BureauState state) {
    List<UUID> ids = new ArrayList<>();
    IntStream.range(0, 10).forEach(i -> ids.add(state.nextInquiryId()));
    return ids;
  }
}
