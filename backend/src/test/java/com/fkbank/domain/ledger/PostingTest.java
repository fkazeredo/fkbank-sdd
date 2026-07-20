package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Posting")
class PostingTest {

  private static final AccountId FROM = AccountId.of(1);
  private static final AccountId TO = AccountId.of(2);
  private static final Instant AT = Instant.parse("2026-07-20T12:00:00Z");

  private static Posting tenFromOneToTwo() {
    return Posting.of(PostingId.next(), FROM, TO, Money.of("10.00"), AT);
  }

  @Test
  @DisplayName("records money leaving one account and arriving in another")
  void recordsAMovement() {
    Posting posting = tenFromOneToTwo();

    assertThat(posting.debitAccountId()).isEqualTo(FROM);
    assertThat(posting.creditAccountId()).isEqualTo(TO);
    assertThat(posting.amount()).isEqualTo(Money.of("10.00"));
    assertThat(posting.occurredAt()).isEqualTo(AT);
    assertThat(posting.isReversal()).isFalse();
    assertThat(posting.reverses()).isEmpty();
  }

  @Test
  @DisplayName("refuses a zero or negative amount")
  void refusesNonPositiveAmounts() {
    PostingId id = PostingId.next();

    assertThatThrownBy(() -> Posting.of(id, FROM, TO, Money.zero(), AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Posting.of(id, FROM, TO, Money.of("-1.00"), AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("refuses an account posting against itself")
  void refusesSelfPosting() {
    PostingId id = PostingId.next();

    assertThatThrownBy(() -> Posting.of(id, FROM, FROM, Money.of("10.00"), AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("itself");
  }

  @Test
  @DisplayName("a reversal swaps the accounts, keeps the amount and points at the original")
  void reversalSwapsTheAccounts() {
    Posting original = tenFromOneToTwo();

    Posting contra = Posting.reverseOf(PostingId.next(), original, AT.plusSeconds(60));

    assertThat(contra.debitAccountId()).isEqualTo(TO);
    assertThat(contra.creditAccountId()).isEqualTo(FROM);
    assertThat(contra.amount()).isEqualTo(original.amount());
    assertThat(contra.isReversal()).isTrue();
    assertThat(contra.reverses()).contains(original.id());
  }

  @Test
  @DisplayName("refuses to reverse a reversal")
  void refusesToReverseAReversal() {
    Posting original = tenFromOneToTwo();
    Posting contra = Posting.reverseOf(PostingId.next(), original, AT);
    PostingId id = PostingId.next();

    assertThatThrownBy(() -> Posting.reverseOf(id, contra, AT))
        .isInstanceOf(ReversalNotAllowedException.class)
        .satisfies(thrown ->
            assertThat(((ReversalNotAllowedException) thrown).code())
                .isEqualTo("REVERSAL_NOT_ALLOWED"));
  }

  @Test
  @DisplayName("two postings are the same only when they carry the same identity")
  void identityIsTheIdentifier() {
    PostingId shared = PostingId.next();
    Posting one = Posting.of(shared, FROM, TO, Money.of("10.00"), AT);
    Posting same = Posting.of(shared, FROM, TO, Money.of("99.00"), AT.plusSeconds(1));

    assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    assertThat(one).isNotEqualTo(tenFromOneToTwo());
  }
}
