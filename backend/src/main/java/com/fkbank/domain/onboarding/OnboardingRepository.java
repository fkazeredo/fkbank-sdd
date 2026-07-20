package com.fkbank.domain.onboarding;

import com.fkbank.domain.customer.Cpf;
import java.util.Optional;

/** Stores and retrieves applications to open an account. */
public interface OnboardingRepository {

  Optional<Onboarding> findById(OnboardingId id);

  /** The application for this CPF that is still waiting on the bureau, if there is one. */
  Optional<Onboarding> findPendingByCpf(Cpf cpf);

  /**
   * The most recent application for this CPF, whatever state it reached.
   *
   * <p>Needed by the loser of a race for the same CPF: by the time it looks, the winner may
   * already have been decided, and looking only for a pending one would find nothing and turn a
   * resubmission into an error.
   */
  Optional<Onboarding> findLatestByCpf(Cpf cpf);

  /**
   * The application the bureau is answering.
   *
   * <p>Looked up by the reference the bank generated and gave only to the bureau, never by the
   * identifier the applicant holds.
   */
  Optional<Onboarding> findByBureauReference(BureauReference reference);

  /**
   * Persists a newly submitted application.
   *
   * @throws OnboardingAlreadyPendingException if one for the same CPF is already waiting — the
   *     check a caller makes beforehand cannot see an application committing at the same
   *     instant, so the store has the final say
   */
  Onboarding save(Onboarding onboarding);

  /** Persists a change to an application that already exists. */
  Onboarding update(Onboarding onboarding);
}
