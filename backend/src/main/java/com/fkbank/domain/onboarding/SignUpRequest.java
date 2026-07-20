package com.fkbank.domain.onboarding;

/**
 * What someone filled in on the sign-up form, exactly as they typed it.
 *
 * <p>Plain text rather than parsed values, so that every judgement about what the input means —
 * how a CPF is normalized, what counts as a valid e-mail, whether a password is strong enough —
 * is made in one place instead of partly at the edge and partly here. The edge's job is to
 * deliver what was typed.
 */
public record SignUpRequest(
    String fullName,
    String cpf,
    String email,
    String password,
    String birthDate,
    String monthlyIncome) {}
