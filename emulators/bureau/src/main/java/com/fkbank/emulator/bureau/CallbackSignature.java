package com.fkbank.emulator.bureau;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs a callback the way the real bureau does, so the receiver can prove the body came from the
 * bureau and arrived unaltered.
 *
 * <p>The signature covers raw bytes, never an object. Signing a parsed payload and then
 * re-serializing it to send would let the two drift on nothing more than key order or whitespace,
 * and the receiver would reject a body that was never tampered with.
 */
final class CallbackSignature {

  static final String HEADER = "X-Bureau-Signature";

  private static final String ALGORITHM = "HmacSHA256";

  private CallbackSignature() {}

  /** Returns the header value: {@code sha256=} followed by the MAC in lowercase hex. */
  static String of(byte[] body, String secret) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException e) {
      // HmacSHA256 is required of every Java runtime, so reaching this means the JVM itself is
      // broken rather than the input being wrong.
      throw new IllegalStateException("HMAC-SHA256 is unavailable in this runtime", e);
    }
  }
}
