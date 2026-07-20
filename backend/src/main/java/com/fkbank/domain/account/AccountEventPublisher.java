package com.fkbank.domain.account;

/** Announces that an account was opened, keeping the framework's publisher out of the domain. */
public interface AccountEventPublisher {

  void publish(AccountOpened event);
}
