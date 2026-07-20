package com.fkbank.domain.onboarding;

import com.fkbank.domain.customer.Cpf;
import java.util.Optional;

/** Stores and retrieves applications to open an account. */
public interface OnboardingRepository {

  Optional<Onboarding> findById(OnboardingId id);

  /** The application for this CPF that is still waiting on the bureau, if there is one. */
  Optional<Onboarding> findPendingByCpf(Cpf cpf);

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
