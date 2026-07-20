package com.fkbank.emulator.bureau;

import jakarta.validation.constraints.NotNull;

/**
 * Which scenario to serve, and to whom.
 *
 * <p>A null CPF means the assignment is the fallback used by every applicant who has no one of
 * their own.
 */
public record ScenarioAssignment(@NotNull Scenario scenario, String cpf) {}
