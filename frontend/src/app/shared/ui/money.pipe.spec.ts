import { describe, expect, it } from 'vitest';
import { MoneyPipe } from './money.pipe';

/**
 * `Intl` separates the currency symbol from the digits with a non-breaking space in this locale.
 * Writing that character into every expectation makes the tests unreadable and invites someone
 * to "fix" it into a normal space, so it is normalized once here instead.
 */
function formatted(value: string): string {
  return value.replace(/ /g, ' ');
}

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats a zero balance with its currency', () => {
    expect(formatted(pipe.transform('0.00', 'BRL'))).toBe('R$ 0,00');
  });

  it('groups thousands and keeps two decimal places', () => {
    expect(formatted(pipe.transform('1234567.89', 'BRL'))).toBe('R$ 1.234.567,89');
  });

  it('separates thousands with a dot and the decimals with a comma', () => {
    // The two marks are swapped relative to English. Getting them the wrong way round turns
    // R$ 1.000,00 into a thousandth of itself, which is the reason this locale is pinned.
    const oneThousand = formatted(pipe.transform('1000.00', 'BRL'));

    expect(oneThousand).toBe('R$ 1.000,00');
    expect(oneThousand).not.toBe('R$ 1,000.00');
  });

  it('pads an amount that arrived with fewer decimals than the currency shows', () => {
    expect(formatted(pipe.transform('12.5', 'BRL'))).toBe('R$ 12,50');
  });

  it('formats other currencies with their own symbol', () => {
    expect(formatted(pipe.transform('10.00', 'USD'))).toBe('US$ 10,00');
  });

  it('defaults to the home currency when none is given', () => {
    expect(formatted(pipe.transform('10.00'))).toBe('R$ 10,00');
  });

  it('formats a negative amount', () => {
    expect(formatted(pipe.transform('-25.30', 'BRL'))).toBe('-R$ 25,30');
  });

  it('keeps every digit of an amount too large for a float to hold exactly', () => {
    // 9007199254740993 is the first integer a double cannot represent; parsing it would round
    // it down to ...992. The string must reach `Intl` untouched.
    expect(formatted(pipe.transform('9007199254740993.00', 'BRL'))).toBe(
      'R$ 9.007.199.254.740.993,00',
    );
  });

  it('renders nothing for a missing amount rather than the word "null"', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });

  it('returns a non-numeric input untouched instead of rendering NaN', () => {
    expect(pipe.transform('not-an-amount')).toBe('not-an-amount');
    expect(pipe.transform('')).toBe('');
  });

  it('falls back to the raw amount and code when the currency is not a real one', () => {
    expect(pipe.transform('10.00', 'NOT-A-CURRENCY')).toBe('10.00 NOT-A-CURRENCY');
  });

  it('tolerates surrounding whitespace', () => {
    expect(formatted(pipe.transform('  42.00  ', 'BRL'))).toBe('R$ 42,00');
  });
});
