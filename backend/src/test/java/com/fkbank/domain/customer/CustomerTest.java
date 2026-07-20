package com.fkbank.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Customer")
class CustomerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);
  private static final LocalDate EIGHTEENTH_BIRTHDAY_IS_TODAY = LocalDate.of(2008, 7, 20);

  private static Customer register(LocalDate birthDate, LocalDate today) {
    return Customer.register(
        CustomerId.next(),
        FullName.of("Ana Souza"),
        Cpf.of("123.456.789-09"),
        Email.of("ana@example.com"),
        birthDate,
        MonthlyIncome.of("3500.00"),
        today);
  }

  @Nested
  @DisplayName("the age boundary")
  class TheAgeBoundary {

    @Test
    @DisplayName("refuses someone the day before their eighteenth birthday")
    void refusesTheDayBefore() {
      LocalDate dayBefore = EIGHTEENTH_BIRTHDAY_IS_TODAY.plusYears(18).minusDays(1);

      assertThatThrownBy(() -> register(EIGHTEENTH_BIRTHDAY_IS_TODAY, dayBefore))
          .as("one day short of eighteen is still a minor, and a minor may not hold an account")
          .isInstanceOf(UnderageCustomerException.class);
    }

    @Test
    @DisplayName("accepts someone on the very day they turn eighteen")
    void acceptsOnTheBirthday() {
      Customer customer = register(EIGHTEENTH_BIRTHDAY_IS_TODAY, TODAY);

      assertThat(customer.birthDate())
          .as("someone turning eighteen today is eighteen today, not tomorrow")
          .isEqualTo(EIGHTEENTH_BIRTHDAY_IS_TODAY);
    }

    @Test
    @DisplayName("brings someone born on 29 February of age on 28 February in a non-leap year")
    void leapDayApplicantComesOfAgeOnTheLastDayOfFebruary() {
      // This is the only case there is: eighteen years after a leap day never lands on another
      // one, because eighteen is not a multiple of four. Somebody born on 29 February therefore
      // always comes of age on a date that does not exist in that year, and where it is rounded
      // to decides whether they wait an extra day.
      LocalDate bornOnALeapDay = LocalDate.of(2008, 2, 29);

      assertThat(Customer.isAdult(bornOnALeapDay, LocalDate.of(2026, 2, 27)))
          .as("still a minor two days before the last day of the month")
          .isFalse();
      assertThat(Customer.isAdult(bornOnALeapDay, LocalDate.of(2026, 2, 28)))
          .as(
              "with no 29 February to reach, the anniversary falls on the last day of the month"
                  + " rather than spilling into March and costing them a day")
          .isTrue();
      assertThat(Customer.isAdult(bornOnALeapDay, LocalDate.of(2026, 3, 1)))
          .as("and they remain an adult afterwards")
          .isTrue();
    }

    @Test
    @DisplayName("accepts someone the day after their eighteenth birthday")
    void acceptsTheDayAfter() {
      LocalDate dayAfter = TODAY.plusDays(1);

      assertThat(register(EIGHTEENTH_BIRTHDAY_IS_TODAY, dayAfter).monthlyIncome())
          .isEqualTo(MonthlyIncome.of("3500.00"));
    }

    @Test
    @DisplayName("judges the age against the supplied day, not the machine clock")
    void judgesAgainstTheSuppliedDay() {
      LocalDate bornToday = LocalDate.of(2026, 7, 20);

      assertThatThrownBy(() -> register(bornToday, TODAY))
          .as("a rule about turning eighteen today must be testable on the day it matters")
          .isInstanceOf(UnderageCustomerException.class);
    }
  }

  @Nested
  @DisplayName("isAdult")
  class IsAdult {

    @Test
    @DisplayName("is false the day before the eighteenth birthday and true from it onwards")
    void flipsOnTheBirthday() {
      LocalDate birth = LocalDate.of(2008, 7, 20);

      assertThat(Customer.isAdult(birth, LocalDate.of(2026, 7, 19))).isFalse();
      assertThat(Customer.isAdult(birth, LocalDate.of(2026, 7, 20))).isTrue();
      assertThat(Customer.isAdult(birth, LocalDate.of(2026, 7, 21))).isTrue();
    }

    @Test
    @DisplayName("counts whole years elapsed, so a much older person is an adult")
    void countsWholeYears() {
      assertThat(Customer.isAdult(LocalDate.of(1980, 1, 1), TODAY)).isTrue();
    }

    @Test
    @DisplayName("states the minimum age it enforces")
    void statesTheMinimumAge() {
      assertThat(Customer.MINIMUM_AGE).isEqualTo(18);
    }
  }

  @Nested
  @DisplayName("registration")
  class Registration {

    @Test
    @DisplayName("refuses a birth date in the future before it ever reaches the age rule")
    void refusesAFutureBirthDate() {
      assertThatThrownBy(() -> register(TODAY.plusDays(1), TODAY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("future");
    }

    @Test
    @DisplayName("keeps everything it was registered with")
    void keepsWhatItWasGiven() {
      CustomerId id = CustomerId.next();

      Customer customer = Customer.register(
          id,
          FullName.of("Ana Souza"),
          Cpf.of("123.456.789-09"),
          Email.of("ana@example.com"),
          EIGHTEENTH_BIRTHDAY_IS_TODAY,
          MonthlyIncome.of("3500.00"),
          TODAY);

      assertThat(customer.id()).isEqualTo(id);
      assertThat(customer.fullName()).isEqualTo(FullName.of("Ana Souza"));
      assertThat(customer.cpf()).isEqualTo(Cpf.of("12345678909"));
      assertThat(customer.email()).isEqualTo(Email.of("ana@example.com"));
    }

    @Test
    @DisplayName("rebuilds an existing customer without re-applying the age rule")
    void existingSkipsTheAgeRule() {
      Customer child = Customer.existing(
          CustomerId.next(),
          FullName.of("Ana Souza"),
          Cpf.of("123.456.789-09"),
          Email.of("ana@example.com"),
          LocalDate.of(2020, 1, 1),
          MonthlyIncome.zero());

      assertThat(child.birthDate())
          .as("reconstitution replays what was already accepted; it does not re-judge it")
          .isEqualTo(LocalDate.of(2020, 1, 1));
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("two customers are the same when their identifiers are")
    void identityIsTheIdentifier() {
      CustomerId id = CustomerId.next();
      Customer one = Customer.existing(
          id,
          FullName.of("Ana Souza"),
          Cpf.of("123.456.789-09"),
          Email.of("ana@example.com"),
          EIGHTEENTH_BIRTHDAY_IS_TODAY,
          MonthlyIncome.zero());
      Customer sameIdDifferentDetails = Customer.existing(
          id,
          FullName.of("Ana Maria Souza"),
          Cpf.of("111.444.777-35"),
          Email.of("ana.maria@example.com"),
          EIGHTEENTH_BIRTHDAY_IS_TODAY,
          MonthlyIncome.of("100.00"));

      assertThat(one)
          .as("a customer who corrects their details is still the same customer")
          .isEqualTo(sameIdDifferentDetails)
          .hasSameHashCodeAs(sameIdDifferentDetails);
    }

    @Test
    @DisplayName("prints with the identifying number masked")
    void printsMasked() {
      Customer customer = register(EIGHTEENTH_BIRTHDAY_IS_TODAY, TODAY);

      assertThat(customer.toString())
          .as("printing a customer must not put their tax number in a log line")
          .doesNotContain("12345678909")
          .contains("***.456.789-**");
    }
  }
}
