package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RejectionReason")
class RejectionReasonTest {

  @Nested
  @DisplayName("categories we recognize")
  class CategoriesWeRecognize {

    @ParameterizedTest
    @CsvSource({
        "DOCUMENT_MISMATCH,  DOCUMENT_MISMATCH",
        "document_mismatch,  DOCUMENT_MISMATCH",
        "Document_Mismatch,  DOCUMENT_MISMATCH",
        "sanctions_list,     SANCTIONS_LIST",
        "INCOMPLETE_RECORD,  INCOMPLETE_RECORD",
        "unspecified,        UNSPECIFIED"
    })
    @DisplayName("maps a known category whatever case the bureau used")
    void mapsAKnownCategoryInAnyCase(String bureauCategory, RejectionReason expected) {
      assertThat(RejectionReason.from(bureauCategory)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ignores surrounding whitespace")
    void ignoresSurroundingWhitespace() {
      assertThat(RejectionReason.from("  sanctions_list  "))
          .isEqualTo(RejectionReason.SANCTIONS_LIST);
    }
  }

  @Nested
  @DisplayName("anything else")
  class AnythingElse {

    @ParameterizedTest
    @ValueSource(strings = {
        "FRAUD_SUSPICION",
        "customer is on internal blocklist since 2019",
        "DOCUMENT MISMATCH",
        "0"
    })
    @DisplayName("turns an unrecognized category into the unspecified one instead of showing it")
    void unrecognizedBecomesUnspecified(String unrecognized) {
      assertThat(RejectionReason.from(unrecognized))
          .as("a category invented upstream describes someone's record at a third party, so it"
              + " must not reach an applicant unreviewed")
          .isEqualTo(RejectionReason.UNSPECIFIED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("turns a missing or blank category into the unspecified one")
    void missingOrBlankBecomesUnspecified(String absent) {
      assertThat(RejectionReason.from(absent)).isEqualTo(RejectionReason.UNSPECIFIED);
    }

    @Test
    @DisplayName("never returns null, so no caller has to check for one")
    void neverReturnsNull() {
      assertThat(RejectionReason.from(null)).isNotNull();
      assertThat(RejectionReason.from("whatever the bureau said")).isNotNull();
    }
  }

  @Test
  @DisplayName("stays a small closed set the applicant can be shown")
  void staysASmallClosedSet() {
    assertThat(RejectionReason.values())
        .as("each value is wording we are willing to put in front of an applicant")
        .containsExactly(
            RejectionReason.DOCUMENT_MISMATCH,
            RejectionReason.SANCTIONS_LIST,
            RejectionReason.INCOMPLETE_RECORD,
            RejectionReason.UNSPECIFIED);
  }
}
