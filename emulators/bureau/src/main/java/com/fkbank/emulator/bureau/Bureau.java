package com.fkbank.emulator.bureau;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The emulated bureau itself: it decides an inquiry according to the scenario chosen for the
 * applicant, and takes however long that scenario says it takes.
 *
 * <p>The slow scenarios post their callback before answering, rather than handing the work to a
 * scheduler. By then the caller's own timeout has long expired, so nothing is delayed by it that
 * was still being waited on; what it buys is determinism — once the response is in a test's hands,
 * the callbacks it triggered have already been delivered and can be asserted on without polling.
 */
@Component
class Bureau {

  private final BureauProperties properties;
  private final BureauState state;
  private final CallbackDispatcher dispatcher;

  Bureau(BureauProperties properties, BureauState state, CallbackDispatcher dispatcher) {
    this.properties = properties;
    this.state = state;
    this.dispatcher = dispatcher;
  }

  InquiryResponse inquire(InquiryRequest request) {
    Scenario scenario = state.scenarioFor(request.cpf());
    UUID inquiryId = state.nextInquiryId();

    return switch (scenario) {
      case APPROVE -> InquiryResponse.approved(inquiryId);
      case DECLINE -> InquiryResponse.rejected(inquiryId, ReasonCategory.DOCUMENT_MISMATCH);
      case DELAY -> answerLate(request, inquiryId, 1);
      case DUPLICATE_WEBHOOK -> answerLate(request, inquiryId, 2);
    };
  }

  private InquiryResponse answerLate(InquiryRequest request, UUID inquiryId, int copies) {
    sleepThroughTheCallersTimeout();

    InquiryResponse response = InquiryResponse.approved(inquiryId);
    dispatcher.deliver(CallbackPayload.of(request.reference(), response), copies);
    return response;
  }

  private void sleepThroughTheCallersTimeout() {
    try {
      Thread.sleep(properties.getDelay());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while emulating a slow bureau", e);
    }
  }
}
