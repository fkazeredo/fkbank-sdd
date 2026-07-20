package com.fkbank.emulator.bureau;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Everything the emulator remembers: which answer to give whom, the callbacks it has posted, and
 * the seeded source of its inquiry ids.
 *
 * <p>It is all in memory and it all dies with the process. That is a feature — a test suite starts
 * from a known state without anyone having to clean up after the last one — and it is why nothing
 * here is worth persisting.
 *
 * <p>Inquiry ids come from a seeded generator rather than {@link UUID#randomUUID()}, so a failing
 * run can be replayed with the same ids it had the first time. The generator is not thread-safe,
 * hence the lock; the cost is irrelevant next to the HTTP call that surrounds it.
 */
@Component
public class BureauState {

  private final BureauProperties properties;
  private final Map<String, Scenario> perCpf = new ConcurrentHashMap<>();
  private final List<SentCallback> sentCallbacks = new CopyOnWriteArrayList<>();
  private final Object idLock = new Object();

  private volatile Scenario defaultScenario;
  private Random ids;

  BureauState(BureauProperties properties) {
    this.properties = properties;
    this.defaultScenario = properties.getDefaultScenario();
    this.ids = new Random(properties.getSeed());
  }

  /**
   * The scenario that applies to this applicant: their own if one was set for them, the fallback
   * otherwise.
   */
  public Scenario scenarioFor(String cpf) {
    return perCpf.getOrDefault(normalizeCpf(cpf), defaultScenario);
  }

  /** Records an assignment, and answers with what was actually stored. */
  public ScenarioAssignment assign(ScenarioAssignment assignment) {
    if (assignment.cpf() == null || assignment.cpf().isBlank()) {
      defaultScenario = assignment.scenario();
      return new ScenarioAssignment(assignment.scenario(), null);
    }
    String digits = normalizeCpf(assignment.cpf());
    perCpf.put(digits, assignment.scenario());
    return new ScenarioAssignment(assignment.scenario(), digits);
  }

  public ScenarioSettings settings() {
    return new ScenarioSettings(defaultScenario, Map.copyOf(perCpf));
  }

  public List<SentCallback> callbacks() {
    return List.copyOf(sentCallbacks);
  }

  void record(SentCallback callback) {
    sentCallbacks.add(callback);
  }

  /**
   * Puts the emulator back exactly where it started, including the id sequence — a reset that left
   * the ids where they were would make the second half of a run unreproducible.
   */
  public void reset() {
    perCpf.clear();
    sentCallbacks.clear();
    defaultScenario = properties.getDefaultScenario();
    synchronized (idLock) {
      ids = new Random(properties.getSeed());
    }
  }

  UUID nextInquiryId() {
    synchronized (idLock) {
      return new UUID(ids.nextLong(), ids.nextLong());
    }
  }

  /**
   * Reduces a CPF to its digits, so an applicant identified as {@code 123.456.789-09} in one call
   * and {@code 12345678909} in the next is recognised as the same person both times.
   */
  static String normalizeCpf(String cpf) {
    return cpf == null ? "" : cpf.replaceAll("\\D", "");
  }
}
