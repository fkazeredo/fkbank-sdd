package com.fkbank.emulator.bureau;

import java.util.Map;

/**
 * The whole of what the emulator has been told to do: the fallback answer, and the per-applicant
 * exceptions to it, keyed by CPF digits.
 */
public record ScenarioSettings(Scenario defaultScenario, Map<String, Scenario> perCpf) {}
