package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.onboarding.BureauDecision.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BureauDecision")
class BureauDecisionTest {

  @Nested
  @DisplayName("a refusal")
  class ARefusal {

    @Test
    @DisplayName("keeps the category it was refused under")
    void keepsItsCategory() {
      BureauDecision decision = BureauDecision.rejected(RejectionReason.DOCUMENT_MISMATCH);

      assertThat(decision.isRejected()).isTrue();
      assertThat(decision.reason()).isEqualTo(RejectionReason.DOCUMENT_MISMATCH);
    }

    @Test
    @DisplayName("falls back to the unspecified category when it arrives without one")
    void defaultsToUnspecified() {
      BureauDecision decision = BureauDecision.rejected(null);

      assertThat(decision.reason())
          .as("a refusal always has something to tell the applicant, even if only that we were"
              + " not given a category we recognize")
          .isEqualTo(RejectionReason.UNSPECIFIED);
    }
  }

  @Nested
  @DisplayName("anything that is not a refusal")
  class AnythingThatIsNotARefusal {

    @Test
    @DisplayName("carries no reason when approved")
    void approvalCarriesNoReason() {
      assertThat(BureauDecision.approved().reason()).isNull();
    }

    @Test
    @DisplayName("carries no reason when the bureau did not answer")
    void undeterminedCarriesNoReason() {
      assertThat(BureauDecision.undetermined().reason()).isNull();
    }

    @Test
    @DisplayName("drops a reason handed to it by mistake rather than keeping a contradiction")
    void dropsAReasonPassedByMistake() {
      BureauDecision approved =
          new BureauDecision(Outcome.APPROVED, RejectionReason.SANCTIONS_LIST);
      BureauDecision undetermined =
          new BureauDecision(Outcome.UNDETERMINED, RejectionReason.SANCTIONS_LIST);

      assertThat(approved.reason())
          .as("an approval that also carries a refusal category is a state nobody should have to"
              + " interpret downstream")
          .isNull();
      assertThat(undetermined.reason()).isNull();
    }
  }

  @Nested
  @DisplayName("whether the bureau concluded anything")
  class WhetherTheBureauConcludedAnything {

    @Test
    @DisplayName("an approval and a refusal are both conclusions")
    void approvalAndRefusalAreConclusions() {
      assertThat(BureauDecision.approved().isDetermined()).isTrue();
      assertThat(BureauDecision.rejected(RejectionReason.SANCTIONS_LIST).isDetermined()).isTrue();
    }

    @Test
    @DisplayName("no answer in time is the only thing that is not a conclusion")
    void onlySilenceIsNotAConclusion() {
      BureauDecision undetermined = BureauDecision.undetermined();

      assertThat(undetermined.isDetermined())
          .as("silence is not a refusal - the applicant did nothing wrong and stays in the queue")
          .isFalse();
      assertThat(undetermined.isApproved()).isFalse();
      assertThat(undetermined.isRejected()).isFalse();
    }

    @Test
    @DisplayName("an approval is approved and nothing else")
    void anApprovalIsOnlyApproved() {
      BureauDecision approved = BureauDecision.approved();

      assertThat(approved.isApproved()).isTrue();
      assertThat(approved.isRejected()).isFalse();
    }
  }

  @Test
  @DisplayName("refuses to exist without an outcome")
  void refusesToExistWithoutAnOutcome() {
    assertThatThrownBy(() -> new BureauDecision(null, RejectionReason.UNSPECIFIED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome");
  }

  @Test
  @DisplayName("two decisions with the same outcome and category are the same value")
  void comparesByValue() {
    assertThat(BureauDecision.rejected(null))
        .isEqualTo(BureauDecision.rejected(RejectionReason.UNSPECIFIED));
    assertThat(BureauDecision.approved()).isEqualTo(new BureauDecision(Outcome.APPROVED, null));
  }
}
