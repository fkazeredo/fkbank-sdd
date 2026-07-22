package com.fkbank.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rail")
class RailTest {

  @Test
  @DisplayName("classifies each named internal settlement account, whichever leg carries it")
  void classifiesSettlementRails() {
    assertThat(Rail.of("internal:settlement:boleto", "customer:available:x")).isEqualTo(Rail.BOLETO);
    assertThat(Rail.of("customer:available:x", "internal:settlement:pix")).isEqualTo(Rail.PIX);
    assertThat(Rail.of("internal:settlement:card", "customer:available:x")).isEqualTo(Rail.CARD);
  }

  @Test
  @DisplayName("classifies the expense account credited by yield")
  void classifiesYield() {
    assertThat(Rail.of("internal:expense:yield", "customer:box:x:1")).isEqualTo(Rail.YIELD);
  }

  @Test
  @DisplayName("classifies any credit account by its prefix, disbursement or a future receivable alike")
  void classifiesCreditByPrefix() {
    assertThat(Rail.of("internal:credit:disbursement", "customer:available:x")).isEqualTo(Rail.CREDIT);
    assertThat(Rail.of("customer:available:x", "internal:credit:receivable:loan-1"))
        .isEqualTo(Rail.CREDIT);
  }

  @Test
  @DisplayName("falls back to transfer between two customer accounts, available or box")
  void fallsBackToTransfer() {
    assertThat(Rail.of("customer:available:a", "customer:available:b")).isEqualTo(Rail.TRANSFER);
    assertThat(Rail.of("customer:available:a", "customer:box:a:1")).isEqualTo(Rail.TRANSFER);
  }
}
