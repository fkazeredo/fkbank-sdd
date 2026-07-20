package com.fkbank.emulator.bureau;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bureau's business API — the whole of what the bank is allowed to know about this service.
 *
 * <p>Everything that makes it an emulator lives behind {@code /control} and is deliberately not
 * reachable from here. Swapping this emulator for the real bureau should be a change of URL and
 * nothing else, which only stays true while this surface holds no trace of the other one.
 */
@RestController
class InquiryController {

  private final Bureau bureau;

  InquiryController(Bureau bureau) {
    this.bureau = bureau;
  }

  @PostMapping(
      value = "/inquiries",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  InquiryResponse inquire(@Valid @RequestBody InquiryRequest request) {
    return bureau.inquire(request);
  }
}
