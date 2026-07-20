package com.fkbank.infra.integration.bureau;

import com.fkbank.domain.onboarding.BureauCallbackSignature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Recognizes the bureau's callbacks by a shared-secret signature over the request body.
 *
 * <p>The signature covers the exact bytes received, before any parsing. Signing a re-serialized
 * body instead would compare our idea of the message with the sender's, and those differ over
 * whitespace, key order and number formatting — differences that are invisible until a valid
 * callback is rejected in production.
 */
@Component
class HmacBureauCallbackSignature implements BureauCallbackSignature {

  private static final String ALGORITHM = "HmacSHA256";

  /** The scheme prefix the sender puts in front of the hex digest. */
  private static final String PREFIX = "sha256=";

  private final byte[] secret;

  HmacBureauCallbackSignature(BureauProperties properties) {
    this.secret = properties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public boolean isValid(byte[] body, String signature) {
    if (body == null || signature == null) {
      return false;
    }
    String presented = signature.trim();
    if (!presented.startsWith(PREFIX)) {
      return false;
    }
    byte[] presentedDigest;
    try {
      presentedDigest = HexFormat.of().parseHex(presented.substring(PREFIX.length()));
    } catch (IllegalArgumentException notHex) {
      return false;
    }
    // Compared in constant time. A comparison that stops at the first differing byte takes
    // measurably longer the more of the prefix is right, which is enough for a caller to
    // discover a valid signature one byte at a time.
    return MessageDigest.isEqual(presentedDigest, digestOf(body));
  }

  private byte[] digestOf(byte[] body) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return mac.doFinal(body);
    } catch (java.security.GeneralSecurityException unusableKey) {
      throw new IllegalStateException("the callback signing key is unusable", unusableKey);
    }
  }
}
