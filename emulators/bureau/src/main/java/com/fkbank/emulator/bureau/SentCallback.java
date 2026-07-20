package com.fkbank.emulator.bureau;

/**
 * A record of one callback this emulator posted, kept so a test can assert on what was actually
 * delivered instead of on what was meant to be.
 *
 * <p>The body is stored as the exact text that went on the wire, because that — not the object it
 * was built from — is what the signature covers.
 *
 * @param statusCode the response the receiver gave, or null when the delivery never got an answer
 * @param error why a delivery failed, or null when it succeeded
 */
public record SentCallback(String body, String signature, Integer statusCode, String error) {}
