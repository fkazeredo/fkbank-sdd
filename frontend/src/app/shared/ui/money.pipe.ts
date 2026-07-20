import { Pipe, PipeTransform } from '@angular/core';

/**
 * The locale every amount is presented in.
 *
 * Deliberately not the locale of the interface text. Amounts here are in Brazilian reais and are
 * read by people who hold Brazilian accounts, for whom a comma is the decimal separator; showing
 * `R$1,234.56` to that reader misplaces both marks at once and invites a balance being misread
 * by a factor of a thousand. Wording stays in English while money is written the way its
 * currency is written.
 */
const DISPLAY_LOCALE = 'pt-BR';

/** Currency used when a caller has no currency to offer, matching the product's home market. */
const DEFAULT_CURRENCY = 'BRL';

/** A plain decimal figure: optional sign, digits, and an optional fractional part. */
const DECIMAL = /^-?\d+(\.\d+)?$/;

/**
 * `Intl` formats a decimal string exactly as given at runtime, but the type declarations
 * bundled with this TypeScript target only describe the literal-typed form of that overload.
 * Narrowing the formatter to this shape keeps the string on its path to `Intl` instead of
 * routing it through a floating-point number, which is what would quietly lose precision.
 */
interface DecimalStringFormatter {
  format(value: string): string;
}

const formatters = new Map<string, DecimalStringFormatter>();

function formatterFor(currency: string): DecimalStringFormatter {
  const cached = formatters.get(currency);
  if (cached) {
    return cached;
  }

  const formatter = new Intl.NumberFormat(DISPLAY_LOCALE, {
    style: 'currency',
    currency,
  }) as unknown as DecimalStringFormatter;

  formatters.set(currency, formatter);
  return formatter;
}

/**
 * Presents an amount the backend sent as a decimal string.
 *
 * This pipe only formats. It never adds, subtracts, converts or rounds an amount into a new
 * value: the figure it receives is already the figure of record, and the only transformation
 * applied is the locale's presentation of it. Anything that changes an amount belongs on the
 * server, where the arithmetic is authoritative and auditable.
 *
 * An input that is not a decimal figure is returned untouched rather than rendered as `NaN`,
 * so a contract that drifts shows up as visibly wrong text instead of a plausible number.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: string | null | undefined, currency: string = DEFAULT_CURRENCY): string {
    if (amount === null || amount === undefined) {
      return '';
    }

    const trimmed = amount.trim();
    if (!DECIMAL.test(trimmed)) {
      return amount;
    }

    try {
      return formatterFor(currency || DEFAULT_CURRENCY).format(trimmed);
    } catch {
      // An unknown currency code makes `Intl` throw. The amount itself is still true, so it is
      // shown beside its raw code rather than replaced by an error the person cannot act on.
      return `${trimmed} ${currency}`.trim();
    }
  }
}
