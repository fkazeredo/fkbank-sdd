package com.fkbank.emulator.bureau;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * What a caller asks the bureau to check.
 *
 * <p>The reference is opaque here: the bureau echoes it back on the callback and never interprets
 * it, which is what lets the caller correlate a late answer with the application that is waiting
 * for it.
 */
public record InquiryRequest(
    @NotBlank String reference,
    @NotBlank String cpf,
    @NotBlank String fullName,
    LocalDate birthDate) {}
