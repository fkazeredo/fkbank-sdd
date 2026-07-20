package com.fkbank.domain.account;

import java.time.Instant;
import java.util.UUID;

/**
 * An account was opened and can receive money.
 *
 * <p>Carries plain values rather than domain types: the fact is stored in the event registry and
 * read by consumers in other contexts, and a stored event that depends on a class keeps working
 * only for as long as nobody changes that class.
 */
public record AccountOpened(
    UUID accountId, UUID customerId, String branch, String number, Instant openedAt) {

  static AccountOpened from(CurrentAccount account) {
    return new AccountOpened(
        account.id().value(),
        account.customerId().value(),
        account.number().branch(),
        account.number().number(),
        account.openedAt());
  }
}
