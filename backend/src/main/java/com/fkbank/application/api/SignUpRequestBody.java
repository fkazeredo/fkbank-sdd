package com.fkbank.application.api;

/**
 * The sign-up form as it arrives over HTTP.
 *
 * <p>Every field is text, matching what was typed. Deciding what the values mean — and refusing
 * the ones that mean nothing — belongs to the domain, so that the same judgement applies however
 * a submission reaches the bank.
 */
public record SignUpRequestBody(
    String fullName,
    String cpf,
    String email,
    String password,
    String birthDate,
    String monthlyIncome) {}
