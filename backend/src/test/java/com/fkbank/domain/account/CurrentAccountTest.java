package com.fkbank.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.customer.CustomerId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CurrentAccount")
class CurrentAccountTest {

  private static final Instant OPENED_AT = Instant.parse("2026-07-20T12:00:00Z");

  private static CurrentAccount openFor(CustomerId customerId) {
    return CurrentAccount.openFor(
        CurrentAccountId.next(), customerId, AccountNumber.of(42), OPENED_AT);
  }

  @Nested
  @DisplayName("where its money is recorded")
  class WhereItsMoneyIsRecorded {

    @Test
    @DisplayName("derives the chart-of-accounts code from the customer")
    void derivesTheCodeFromTheCustomer() {
      CustomerId customerId = CustomerId.of(UUID.fromString("11111111-2222-3333-4444-555555555555"));

      assertThat(CurrentAccount.ledgerAccountCodeFor(customerId))
          .isEqualTo("customer:available:11111111-2222-3333-4444-555555555555");
    }

    @Test
    @DisplayName("gives the same customer the same code every time it is asked")
    void theCodeIsStable() {
      CustomerId customerId = CustomerId.next();

      assertThat(CurrentAccount.ledgerAccountCodeFor(customerId))
          .as("the code is recomputed on every use, so it has to be a pure function of the"
              + " customer or a balance would go missing between calls")
          .isEqualTo(CurrentAccount.ledgerAccountCodeFor(customerId));
    }

    @Test
    @DisplayName("gives two customers two different codes")
    void differentCustomersGetDifferentCodes() {
      assertThat(CurrentAccount.ledgerAccountCodeFor(CustomerId.next()))
          .isNotEqualTo(CurrentAccount.ledgerAccountCodeFor(CustomerId.next()));
    }

    @Test
    @DisplayName("the account agrees with the derivation rule about where its balance is")
    void theInstanceAgreesWithTheRule() {
      CustomerId customerId = CustomerId.next();
      CurrentAccount account = openFor(customerId);

      assertThat(account.ledgerAccountCode())
          .as("nothing points at the balance, so the account and the rule can never disagree"
              + " about whose money it is")
          .isEqualTo(CurrentAccount.ledgerAccountCodeFor(customerId));
    }

    @Test
    @DisplayName("the code does not depend on the account, only on its holder")
    void theCodeIgnoresTheAccountItself() {
      CustomerId customerId = CustomerId.next();
      CurrentAccount one = openFor(customerId);
      CurrentAccount reopenedUnderAnotherNumber = CurrentAccount.existing(
          CurrentAccountId.next(), customerId, AccountNumber.of(99), OPENED_AT);

      assertThat(one.ledgerAccountCode())
          .isEqualTo(reopenedUnderAnotherNumber.ledgerAccountCode());
    }

    @Test
    @DisplayName("refuses to derive a code for nobody")
    void refusesANullCustomer() {
      assertThatThrownBy(() -> CurrentAccount.ledgerAccountCodeFor(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("customer id");
    }
  }

  @Nested
  @DisplayName("opening")
  class Opening {

    @Test
    @DisplayName("keeps the holder, the number and the moment it was opened")
    void keepsWhatItWasGiven() {
      CustomerId customerId = CustomerId.next();
      CurrentAccountId id = CurrentAccountId.next();

      CurrentAccount account =
          CurrentAccount.openFor(id, customerId, AccountNumber.of(42), OPENED_AT);

      assertThat(account.id()).isEqualTo(id);
      assertThat(account.customerId()).isEqualTo(customerId);
      assertThat(account.number()).isEqualTo(AccountNumber.of(42));
      assertThat(account.openedAt()).isEqualTo(OPENED_AT);
    }

    @Test
    @DisplayName("refuses to open without a holder, a number or a moment")
    void refusesMissingParts() {
      CurrentAccountId id = CurrentAccountId.next();
      CustomerId customerId = CustomerId.next();
      AccountNumber number = AccountNumber.of(42);

      assertThatThrownBy(() -> CurrentAccount.openFor(id, null, number, OPENED_AT))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> CurrentAccount.openFor(id, customerId, null, OPENED_AT))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> CurrentAccount.openFor(id, customerId, number, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("two accounts are the same when their identifiers are")
    void identityIsTheIdentifier() {
      CurrentAccountId id = CurrentAccountId.next();
      CurrentAccount one =
          CurrentAccount.openFor(id, CustomerId.next(), AccountNumber.of(1), OPENED_AT);
      CurrentAccount sameIdDifferentDetails =
          CurrentAccount.existing(id, CustomerId.next(), AccountNumber.of(2), OPENED_AT);

      assertThat(one).isEqualTo(sameIdDifferentDetails).hasSameHashCodeAs(sameIdDifferentDetails);
    }

    @Test
    @DisplayName("prints the number a person would quote, and not the holder")
    void printsTheNumber() {
      CustomerId customerId = CustomerId.next();

      assertThat(openFor(customerId).toString())
          .contains("0001-00000042")
          .doesNotContain(customerId.value().toString());
    }
  }
}
