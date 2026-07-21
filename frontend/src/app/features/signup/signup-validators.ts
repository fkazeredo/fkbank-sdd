import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** The number of digits a CPF has once punctuation is removed. */
const CPF_LENGTH = 11;

/** An amount with at most two decimal places and no sign. */
const NON_NEGATIVE_TWO_DECIMALS = /^\d{1,12}(\.\d{1,2})?$/;

/** A calendar date as the date input and the backend both express it. */
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** The age at which a person may open an account on their own. */
const MINIMUM_AGE = 18;

/** The fewest characters a full name may have once incidental whitespace is collapsed. */
const FULL_NAME_MIN_LENGTH = 3;

/** Strips punctuation so a CPF typed either way becomes the digits the backend expects. */
export function normalizeCpf(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * Checks a CPF's two verification digits.
 *
 * Each is the weighted sum of the digits before it, reduced modulo 11, with a remainder of 10
 * collapsing to zero. Sequences of a single repeated digit satisfy that arithmetic by accident,
 * so they are excluded outright.
 */
export function isValidCpf(value: string): boolean {
  const digits = normalizeCpf(value);

  if (digits.length !== CPF_LENGTH || /^(\d)\1{10}$/.test(digits)) {
    return false;
  }

  for (const position of [9, 10]) {
    let sum = 0;
    for (let index = 0; index < position; index += 1) {
      sum += Number(digits[index]) * (position + 1 - index);
    }

    const remainder = (sum * 10) % 11;
    const expected = remainder === 10 ? 0 : remainder;

    if (expected !== Number(digits[position])) {
      return false;
    }
  }

  return true;
}

/** Whether a `YYYY-MM-DD` string names a day that actually exists. */
export function isRealDate(value: string): boolean {
  const match = ISO_DATE.exec(value);
  if (!match) {
    return false;
  }

  const [, year, month, day] = match.map(Number);
  const candidate = new Date(Date.UTC(year, month - 1, day));

  return (
    candidate.getUTCFullYear() === year &&
    candidate.getUTCMonth() === month - 1 &&
    candidate.getUTCDate() === day
  );
}

/**
 * Completed years between a birth date and a reference day.
 *
 * Compares calendar fields rather than elapsed milliseconds, so the answer does not depend on
 * leap days or on the hour at which the question is asked. Both dates are read as `YYYY-MM-DD`.
 *
 * @param birthDate the day the person was born
 * @param today the day to measure against
 * @returns completed years, which is negative when the birth date has not arrived yet
 */
export function completedYears(birthDate: string, today: string): number {
  const [birthYear, birthMonth, birthDay] = birthDate.split('-').map(Number);
  const [year, month, day] = today.split('-').map(Number);

  const hadBirthdayThisYear = month > birthMonth || (month === birthMonth && day >= birthDay);

  return year - birthYear - (hadBirthdayThisYear ? 0 : 1);
}

/** Today, as `YYYY-MM-DD` in the browser's own calendar. */
export function todayAsIsoDate(now: Date = new Date()): string {
  const year = String(now.getFullYear()).padStart(4, '0');
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

/**
 * Rejects a name the server would reject: fewer than three characters once incidental
 * whitespace is collapsed, or a single word with no family name.
 *
 * The server matches the whole legal name against the bureau, so a single word is an incomplete
 * form rather than an identity. Mirroring that rule here turns a round trip that would otherwise
 * dead-end — the browser accepts the value, the server refuses it, and nothing on screen says
 * why — into an inline hint before anything is sent. `required` covers the empty field, so an
 * empty value passes to stay out of its way.
 */
export const fullNameValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = String(control.value ?? '')
    .trim()
    .replace(/\s+/g, ' ');

  if (!value) {
    return null;
  }

  if (value.length < FULL_NAME_MIN_LENGTH) {
    return { nameTooShort: true };
  }

  if (!value.includes(' ')) {
    return { nameIncomplete: true };
  }

  return null;
};

/** Rejects anything that is not a CPF whose verification digits check out. */
export const cpfValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();

  if (!value) {
    return null;
  }

  return isValidCpf(value) ? null : { cpf: true };
};

/** Rejects a password shorter than eight characters or missing a letter or a digit. */
export const passwordValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = String(control.value ?? '');

  if (!value) {
    return null;
  }

  const strong = value.length >= 8 && /[a-zA-Z]/.test(value) && /\d/.test(value);

  return strong ? null : { weakPassword: true };
};

/**
 * Rejects an impossible date, a date in the future, and anyone under eighteen.
 *
 * The three are reported as distinct errors because a person who mistyped a year needs a
 * different message from one who is simply too young.
 *
 * @param now injectable clock so the boundary case — someone whose eighteenth birthday is
 *     today — can be exercised without waiting for the calendar
 */
export function adultBirthDateValidator(now: () => Date = () => new Date()): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '').trim();

    if (!value) {
      return null;
    }

    if (!isRealDate(value)) {
      return { invalidDate: true };
    }

    const today = todayAsIsoDate(now());

    if (value > today) {
      return { futureDate: true };
    }

    return completedYears(value, today) >= MINIMUM_AGE ? null : { underage: true };
  };
}

/** Rejects a declared income that is negative or carries more than two decimal places. */
export const monthlyIncomeValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();

  if (!value) {
    return null;
  }

  return NON_NEGATIVE_TWO_DECIMALS.test(value) ? null : { monthlyIncome: true };
};

/**
 * Writes an already-valid amount with exactly two decimal places.
 *
 * Padding is done on the text, never by parsing the amount into a number and formatting it
 * back: the digits the person typed are the digits that get sent.
 *
 * @param value an amount that has passed {@link monthlyIncomeValidator}
 */
export function withTwoDecimals(value: string): string {
  const trimmed = value.trim();
  const [whole, fraction = ''] = trimmed.split('.');

  return `${whole}.${fraction.padEnd(2, '0')}`;
}
