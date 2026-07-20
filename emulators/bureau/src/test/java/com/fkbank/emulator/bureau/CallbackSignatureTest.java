package com.fkbank.emulator.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CallbackSignature")
class CallbackSignatureTest {

  private static final String SECRET = "a-shared-secret";
  private static final byte[] BODY =
      "{\"inquiryId\":\"1\",\"outcome\":\"APPROVED\"}".getBytes(StandardCharsets.UTF_8);

  @Test
  @DisplayName("produces an HMAC-SHA256 a receiver can recompute from the same bytes and secret")
  void verifiesAgainstAnIndependentComputation() throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(BODY));

    assertThat(CallbackSignature.of(BODY, SECRET))
        .as("the receiver's own HMAC over the same bytes")
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("is announced as sha256= followed by 64 lowercase hex characters")
  void hasTheAdvertisedShape() {
    assertThat(CallbackSignature.of(BODY, SECRET))
        .as("signature header value")
        .matches("sha256=[0-9a-f]{64}");
  }

  @Test
  @DisplayName("changes when a single byte of the body changes, so tampering cannot pass")
  void rejectsATamperedBody() {
    byte[] tampered = "{\"inquiryId\":\"1\",\"outcome\":\"REJECTED\"}".getBytes(StandardCharsets.UTF_8);

    assertThat(CallbackSignature.of(tampered, SECRET))
        .as("signature over an altered body")
        .isNotEqualTo(CallbackSignature.of(BODY, SECRET));
  }

  @Test
  @DisplayName("changes with the secret, so a signature cannot be forged without it")
  void dependsOnTheSecret() {
    assertThat(CallbackSignature.of(BODY, "another-secret"))
        .as("signature under a different secret")
        .isNotEqualTo(CallbackSignature.of(BODY, SECRET));
  }

  @Test
  @DisplayName("is stable for the same body and secret")
  void isDeterministic() {
    assertThat(CallbackSignature.of(BODY, SECRET))
        .as("second signature over identical input")
        .isEqualTo(CallbackSignature.of(BODY, SECRET));
  }
}
