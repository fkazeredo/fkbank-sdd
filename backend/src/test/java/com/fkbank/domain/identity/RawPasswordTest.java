package com.fkbank.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RawPassword")
class RawPasswordTest {

  private static final String STRONG = "correct1horse";

  @Nested
  @DisplayName("strength")
  class Strength {

    @Test
    @DisplayName("accepts a password that has a letter, a digit and the minimum length")
    void acceptsAStrongPassword() {
      assertThat(RawPassword.of(STRONG).value()).isEqualTo(STRONG);
    }

    @Test
    @DisplayName("accepts a password of exactly the minimum length")
    void acceptsExactlyTheMinimum() {
      String atTheBoundary = "abcdefg1";

      assertThat(atTheBoundary).hasSize(RawPassword.MINIMUM_LENGTH);
      assertThat(RawPassword.of(atTheBoundary).value()).isEqualTo(atTheBoundary);
    }

    @Test
    @DisplayName("rejects a password one character short of the minimum")
    void rejectsOneCharacterShort() {
      assertThatThrownBy(() -> RawPassword.of("abcdef1"))
          .isInstanceOf(WeakPasswordException.class)
          .hasMessageContaining("at least 8");
    }

    @Test
    @DisplayName("rejects a long password that is only letters")
    void rejectsLettersOnly() {
      assertThatThrownBy(() -> RawPassword.of("correcthorse"))
          .as("length alone is not strength when the alphabet is one class of character")
          .isInstanceOf(WeakPasswordException.class)
          .hasMessageContaining("letter and one digit");
    }

    @Test
    @DisplayName("rejects a long password that is only digits")
    void rejectsDigitsOnly() {
      assertThatThrownBy(() -> RawPassword.of("019283746555"))
          .isInstanceOf(WeakPasswordException.class)
          .hasMessageContaining("letter and one digit");
    }

    @Test
    @DisplayName("rejects a password with neither a letter nor a digit")
    void rejectsSymbolsOnly() {
      assertThatThrownBy(() -> RawPassword.of("!@#$%^&*()"))
          .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    @DisplayName("carries the stable code the edge reports")
    void carriesTheStableCode() {
      assertThatThrownBy(() -> RawPassword.of("short1"))
          .isInstanceOfSatisfying(
              WeakPasswordException.class,
              thrown -> assertThat(thrown.code()).isEqualTo("WEAK_PASSWORD"));
    }
  }

  @Nested
  @DisplayName("bounds")
  class Bounds {

    @Test
    @DisplayName("accepts a password at the longest allowed length")
    void acceptsTheLongestAllowed() {
      String atTheBound = "a".repeat(199) + "1";

      assertThat(RawPassword.of(atTheBound).value()).hasSize(200);
    }

    @Test
    @DisplayName("rejects a password one character past the bound - hashing cost is caller-driven")
    void rejectsOnePastTheBound() {
      String pastTheBound = "a".repeat(200) + "1";

      assertThatThrownBy(() -> RawPassword.of(pastTheBound))
          .as("an unbounded password lets a caller choose how much work the server does")
          .isInstanceOf(WeakPasswordException.class)
          .hasMessageContaining("200");
    }

    @Test
    @DisplayName("rejects null as an absent password rather than failing later")
    void rejectsNull() {
      assertThatThrownBy(() -> RawPassword.of(null))
          .isInstanceOf(WeakPasswordException.class)
          .hasMessageContaining("required");
    }
  }

  @Nested
  @DisplayName("disclosure")
  class Disclosure {

    @Test
    @DisplayName("never prints the secret it holds")
    void toStringHidesTheSecret() {
      RawPassword password = RawPassword.of(STRONG);

      assertThat(password.toString())
          .as("an exception, a debug statement or a structured log line must not spill it")
          .doesNotContain(STRONG)
          .isEqualTo("RawPassword[protected]");
    }

    @Test
    @DisplayName("hides the secret even when it is embedded in a larger message")
    void staysHiddenWhenInterpolated() {
      RawPassword password = RawPassword.of(STRONG);

      assertThat("submitted " + password).doesNotContain(STRONG);
    }
  }
}
