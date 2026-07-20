package com.fkbank.infra.integration.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("HmacBureauCallbackSignature")
class HmacBureauCallbackSignatureTest {

  private static final String SECRET = "the-shared-secret";

  private static final byte[] BODY =
      "{\"onboardingId\":\"a\",\"outcome\":\"APPROVED\"}".getBytes(StandardCharsets.UTF_8);

  private final HmacBureauCallbackSignature signature = signatureWith(SECRET);

  private static HmacBureauCallbackSignature signatureWith(String secret) {
    BureauProperties properties = new BureauProperties();
    properties.setHmacSecret(secret);
    return new HmacBureauCallbackSignature(properties);
  }

  /**
   * Computes the signature the way the sender does, independently of the class under test, so
   * that a mistake in the implementation cannot be mirrored by the expectation it is checked
   * against.
   */
  private static String sign(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  @Nested
  @DisplayName("the bureau's own callback")
  class TheBureausOwnCallback {

    @Test
    @DisplayName("accepts a signature computed over these bytes with the shared secret")
    void acceptsAGenuineSignature() {
      assertThat(signature.isValid(BODY, sign(SECRET, BODY)))
          .as("this is the only thing separating the bureau's answer from anyone else's")
          .isTrue();
    }

    @Test
    @DisplayName("accepts a signature with whitespace around it, as a header may arrive")
    void acceptsSurroundingWhitespace() {
      assertThat(signature.isValid(BODY, "  " + sign(SECRET, BODY) + "  ")).isTrue();
    }

    @Test
    @DisplayName("accepts an empty body that was signed as such")
    void acceptsASignedEmptyBody() {
      byte[] empty = new byte[0];

      assertThat(signature.isValid(empty, sign(SECRET, empty))).isTrue();
    }
  }

  @Nested
  @DisplayName("anything else")
  class AnythingElse {

    @Test
    @DisplayName("rejects a body altered after it was signed")
    void rejectsATamperedBody() {
      String genuine = sign(SECRET, BODY);
      byte[] tampered =
          "{\"onboardingId\":\"b\",\"outcome\":\"APPROVED\"}".getBytes(StandardCharsets.UTF_8);

      assertThat(signature.isValid(tampered, genuine))
          .as("redirecting an approval to another application must not survive the check")
          .isFalse();
    }

    @Test
    @DisplayName("rejects a body altered only in whitespace, because the exact bytes are signed")
    void rejectsAWhitespaceOnlyChange() {
      byte[] respaced =
          "{ \"onboardingId\":\"a\",\"outcome\":\"APPROVED\"}".getBytes(StandardCharsets.UTF_8);

      assertThat(signature.isValid(respaced, sign(SECRET, BODY))).isFalse();
    }

    @Test
    @DisplayName("rejects a signature made with a different secret")
    void rejectsAWrongSecret() {
      assertThat(signature.isValid(BODY, sign("someone-elses-secret", BODY)))
          .as("without this the callback endpoint would be open to anyone who can reach it")
          .isFalse();
    }

    @Test
    @DisplayName("rejects a genuine signature presented to a service holding another secret")
    void rejectsWhenTheServiceHoldsAnotherSecret() {
      HmacBureauCallbackSignature other = signatureWith("a-rotated-secret");

      assertThat(other.isValid(BODY, sign(SECRET, BODY))).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects an absent signature instead of treating its absence as permission")
    void rejectsAnAbsentSignature(String absent) {
      assertThat(signature.isValid(BODY, absent)).isFalse();
    }

    @Test
    @DisplayName("rejects an absent body")
    void rejectsAnAbsentBody() {
      assertThat(signature.isValid(null, sign(SECRET, BODY))).isFalse();
    }

    @Test
    @DisplayName("rejects a digest sent without the scheme prefix")
    void rejectsAMissingPrefix() {
      String withoutPrefix = sign(SECRET, BODY).substring("sha256=".length());

      assertThat(signature.isValid(BODY, withoutPrefix))
          .as("the prefix names the algorithm; accepting a bare digest would accept whatever"
              + " algorithm the sender chose")
          .isFalse();
      assertThat(signature.isValid(BODY, "sha1=" + withoutPrefix)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "sha256=not-hex-at-all",
        "sha256=zzzz",
        "sha256=abc",
        "sha256="
    })
    @DisplayName("rejects text that is not a hex digest without letting the parse failure escape")
    void rejectsNonHexWithoutThrowing(String malformed) {
      assertThat(signature.isValid(BODY, malformed))
          .as("a malformed header is an unauthenticated caller, not a server error - throwing"
              + " here would turn a rejected callback into a 500")
          .isFalse();
    }

    @Test
    @DisplayName("rejects a hex digest of the wrong length")
    void rejectsAShortDigest() {
      String truncated = sign(SECRET, BODY).substring(0, "sha256=".length() + 32);

      assertThat(signature.isValid(BODY, truncated)).isFalse();
    }
  }
}
