package com.fkbank.domain.ledger;

/**
 * Announces ledger facts to the rest of the application.
 *
 * <p>A port rather than a direct call to the framework's publisher, so the domain states what
 * happened without knowing how anyone hears about it.
 */
public interface LedgerEventPublisher {

  void publish(PostingRecorded event);
}
