import { describe, expect, it } from 'vitest';
import { FormControl } from '@angular/forms';
import {
  adultBirthDateValidator,
  completedYears,
  cpfValidator,
  isRealDate,
  isValidCpf,
  monthlyIncomeValidator,
  normalizeCpf,
  passwordValidator,
  todayAsIsoDate,
  withTwoDecimals,
} from './signup-validators';

const control = (value: unknown) => new FormControl(value);

/** Freezes the clock at local noon so a date-only comparison cannot straddle midnight. */
const clockAt = (isoDate: string) => () => new Date(`${isoDate}T12:00:00`);

describe('normalizeCpf', () => {
  it('reduces a formatted CPF to its digits', () => {
    expect(normalizeCpf('123.456.789-09')).toBe('12345678909');
  });

  it('leaves a digits-only CPF alone', () => {
    expect(normalizeCpf('12345678909')).toBe('12345678909');
  });

  it('drops every kind of punctuation and spacing', () => {
    expect(normalizeCpf(' 123 456 789 09 ')).toBe('12345678909');
  });
});

describe('isValidCpf', () => {
  it.each(['12345678909', '11144477735', '52998224725'])('accepts %s', (cpf) => {
    expect(isValidCpf(cpf)).toBe(true);
  });

  it('accepts a valid CPF that arrives formatted', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
  });

  it('rejects a wrong first verification digit', () => {
    expect(isValidCpf('12345678919')).toBe(false);
  });

  it('rejects a wrong second verification digit', () => {
    expect(isValidCpf('12345678900')).toBe(false);
  });

  it('rejects a repeated-digit sequence even though the arithmetic accepts it', () => {
    for (const digit of '0123456789') {
      expect(isValidCpf(digit.repeat(11))).toBe(false);
    }
  });

  it('rejects the wrong number of digits', () => {
    expect(isValidCpf('1234567890')).toBe(false);
    expect(isValidCpf('123456789090')).toBe(false);
    expect(isValidCpf('')).toBe(false);
  });

  it('rejects letters', () => {
    expect(isValidCpf('abcdefghijk')).toBe(false);
  });
});

describe('cpfValidator', () => {
  it('passes a valid CPF, formatted or not', () => {
    expect(cpfValidator(control('12345678909'))).toBeNull();
    expect(cpfValidator(control('123.456.789-09'))).toBeNull();
  });

  it('flags an invalid CPF', () => {
    expect(cpfValidator(control('12345678900'))).toEqual({ cpf: true });
  });

  it('says nothing about an empty field, which is the required rule to report', () => {
    expect(cpfValidator(control(''))).toBeNull();
    expect(cpfValidator(control(null))).toBeNull();
  });
});

describe('passwordValidator', () => {
  it.each(['secret123', 'a1234567', 'Passw0rd!'])('accepts %s', (password) => {
    expect(passwordValidator(control(password))).toBeNull();
  });

  it('rejects a password shorter than eight characters', () => {
    expect(passwordValidator(control('abc1234'))).toEqual({ weakPassword: true });
  });

  it('rejects a password with no digit', () => {
    expect(passwordValidator(control('abcdefghij'))).toEqual({ weakPassword: true });
  });

  it('rejects a password with no letter', () => {
    expect(passwordValidator(control('1234567890'))).toEqual({ weakPassword: true });
  });

  it('does not treat whitespace as the missing letter', () => {
    expect(passwordValidator(control('1234 5678'))).toEqual({ weakPassword: true });
  });

  it('says nothing about an empty field', () => {
    expect(passwordValidator(control(''))).toBeNull();
  });
});

describe('isRealDate', () => {
  it('accepts a day that exists', () => {
    expect(isRealDate('1990-05-20')).toBe(true);
  });

  it('accepts the 29th of February in a leap year', () => {
    expect(isRealDate('2000-02-29')).toBe(true);
  });

  it('rejects the 29th of February in a common year', () => {
    expect(isRealDate('1999-02-29')).toBe(false);
  });

  it('rejects a day that no month has', () => {
    expect(isRealDate('2020-04-31')).toBe(false);
  });

  it('rejects anything that is not YYYY-MM-DD', () => {
    expect(isRealDate('20/05/1990')).toBe(false);
    expect(isRealDate('1990-5-20')).toBe(false);
    expect(isRealDate('')).toBe(false);
  });
});

describe('completedYears', () => {
  it('counts a birthday that has already passed this year', () => {
    expect(completedYears('2000-01-10', '2026-07-20')).toBe(26);
  });

  it('does not count a birthday still ahead this year', () => {
    expect(completedYears('2000-12-10', '2026-07-20')).toBe(25);
  });

  it('counts the birthday on the day itself', () => {
    expect(completedYears('2000-07-20', '2026-07-20')).toBe(26);
  });

  it('does not count the day before the birthday', () => {
    expect(completedYears('2000-07-21', '2026-07-20')).toBe(25);
  });
});

describe('todayAsIsoDate', () => {
  it('pads month and day to two digits', () => {
    expect(todayAsIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});

describe('adultBirthDateValidator', () => {
  const validate = adultBirthDateValidator(clockAt('2026-07-20'));

  it('accepts someone comfortably over eighteen', () => {
    expect(validate(control('1990-05-20'))).toBeNull();
  });

  it('accepts someone whose eighteenth birthday is today', () => {
    expect(validate(control('2008-07-20'))).toBeNull();
  });

  it('rejects someone whose eighteenth birthday is tomorrow', () => {
    expect(validate(control('2008-07-21'))).toEqual({ underage: true });
  });

  it('rejects a birth date in the future', () => {
    expect(validate(control('2027-01-01'))).toEqual({ futureDate: true });
  });

  it('rejects a date that does not exist', () => {
    expect(validate(control('1999-02-29'))).toEqual({ invalidDate: true });
  });

  it('says nothing about an empty field', () => {
    expect(validate(control(''))).toBeNull();
  });

  it('uses the real clock when none is supplied', () => {
    const today = todayAsIsoDate();
    expect(adultBirthDateValidator()(control(today))).toEqual({ underage: true });
  });
});

describe('monthlyIncomeValidator', () => {
  it.each(['0', '0.00', '4500', '4500.5', '4500.00', '999999999999.99'])('accepts %s', (amount) => {
    expect(monthlyIncomeValidator(control(amount))).toBeNull();
  });

  it('rejects a negative amount', () => {
    expect(monthlyIncomeValidator(control('-1.00'))).toEqual({ monthlyIncome: true });
  });

  it('rejects more than two decimal places', () => {
    expect(monthlyIncomeValidator(control('4500.005'))).toEqual({ monthlyIncome: true });
  });

  it('rejects a thousands separator, which would be ambiguous on the wire', () => {
    expect(monthlyIncomeValidator(control('4,500.00'))).toEqual({ monthlyIncome: true });
  });

  it('rejects text', () => {
    expect(monthlyIncomeValidator(control('a lot'))).toEqual({ monthlyIncome: true });
  });

  it('says nothing about an empty field', () => {
    expect(monthlyIncomeValidator(control(''))).toBeNull();
  });
});

describe('withTwoDecimals', () => {
  it('adds the decimal part when there is none', () => {
    expect(withTwoDecimals('4500')).toBe('4500.00');
  });

  it('pads a single decimal place', () => {
    expect(withTwoDecimals('4500.5')).toBe('4500.50');
  });

  it('leaves two decimal places alone', () => {
    expect(withTwoDecimals('4500.00')).toBe('4500.00');
  });

  it('keeps a figure a float could not hold exactly', () => {
    expect(withTwoDecimals('9007199254740993')).toBe('9007199254740993.00');
  });

  it('trims surrounding whitespace', () => {
    expect(withTwoDecimals('  10.5 ')).toBe('10.50');
  });
});
