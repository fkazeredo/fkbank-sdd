/**
 * Every user-visible string, addressed by key.
 *
 * Keys from day one (docs/ARCHITECTURE.md §Frontend) so that adding a locale later is a new
 * map rather than a hunt through templates. en-US is the only MVP locale.
 */
export const messages = {
  'app.name': 'FKBANK',
  'nav.account': 'Account',
  'nav.pix': 'PIX',
  'nav.pay': 'Pay',
  'nav.boxes': 'Boxes',
  'nav.card': 'Card',
  'nav.credit': 'Credit',
  'shell.signedInAs': 'Signed in as',
  'shell.signOut': 'Sign out',
  'auth.signingIn': 'Signing you in...',
  'auth.failed': 'We could not complete your sign-in. Please try again.',
  'feature.comingSoon': 'This is where {feature} will live.',

  // Sign-in landing: the only screen an unauthenticated visitor sees.
  'login.tagline': 'Banking that stays out of your way.',
  'login.intro': 'Sign in to reach your account, or open a new one in a few minutes.',
  'login.signIn': 'Sign in',
  'login.redirecting': 'Taking you to sign in...',
  'login.noAccount': 'New to FKBANK?',
  'login.createAccount': 'Open your account',

  // Sign-up form.
  'signup.title': 'Open your account',
  'signup.intro': 'A few details and your account is ready.',
  'signup.field.fullName': 'Full name',
  'signup.field.cpf': 'CPF',
  'signup.field.email': 'E-mail',
  'signup.field.password': 'Password',
  'signup.field.birthDate': 'Date of birth',
  'signup.field.monthlyIncome': 'Declared monthly income',
  'signup.hint.cpf': 'Digits only, or formatted as 000.000.000-00.',
  'signup.hint.password': 'At least 8 characters, including one letter and one digit.',
  'signup.hint.birthDate': 'You must be 18 or older to open an account.',
  'signup.hint.monthlyIncome': 'Your gross monthly income, with at most two decimal places.',
  'signup.submit': 'Open my account',
  'signup.submitting': 'Opening your account...',
  'signup.haveAccount': 'Already have an account?',
  'signup.signIn': 'Sign in',

  // Field-level validation messages.
  'signup.error.fullName.required': 'Tell us your full name.',
  'signup.error.fullName.tooShort': 'Your name looks too short.',
  'signup.error.fullName.incomplete': 'Please enter your full name, including your family name.',
  'signup.error.cpf.required': 'Your CPF is required.',
  'signup.error.cpf.invalid': 'That is not a valid CPF.',
  'signup.error.email.required': 'Your e-mail is required.',
  'signup.error.email.invalid': 'That does not look like an e-mail address.',
  'signup.error.password.required': 'Choose a password.',
  'signup.error.password.weak': 'Use at least 8 characters, including one letter and one digit.',
  'signup.error.birthDate.required': 'Your date of birth is required.',
  'signup.error.birthDate.invalid': 'That is not a valid date.',
  'signup.error.birthDate.future': 'Your date of birth cannot be in the future.',
  'signup.error.birthDate.underage': 'You must be 18 or older to open an account.',
  'signup.error.monthlyIncome.required': 'Your declared monthly income is required.',
  'signup.error.monthlyIncome.invalid':
    'Enter an amount with no more than two decimal places, for example 4500.00.',

  // Outcomes of a submission.
  'signup.rejected.title': 'We could not open your account',
  'signup.rejected.retry': 'Back to the form',
  'signup.pending.title': 'We are still checking your details',
  'signup.pending.body':
    'This is taking a little longer than usual. You can check again in a moment; checking again never opens a second account.',
  'signup.pending.recheck': 'Check again',
  'signup.pending.rechecking': 'Checking...',
  'signup.pending.stillPending': 'Still checking. Please try again shortly.',
  'signup.duplicate.title': 'You already have an account',
  'signup.duplicate.body': 'An account already exists for this CPF or e-mail.',
  'signup.failed': 'Something went wrong on our side. Please try again.',
  'signup.invalid': 'Please review the details below and try again.',

  // Reason categories shown after a rejected check. The bureau's own answer is never shown.
  'signup.reason.DOCUMENT_MISMATCH': 'The details you gave us do not match the records we checked.',
  'signup.reason.SANCTIONS_LIST': 'We are not able to open an account for you at this time.',
  'signup.reason.INCOMPLETE_RECORD':
    'The records we checked are incomplete, so we could not confirm who you are.',
  'signup.reason.UNSPECIFIED': 'We could not confirm your details.',

  // Account home.
  'account.title': 'Account',
  'account.balance': 'Available balance',
  'account.branch': 'Branch',
  'account.number': 'Account',
  'account.loading': 'Loading your account...',
  'account.error': 'We could not load your account.',
  'account.retry': 'Try again',
  'account.viewStatement': 'View statement',

  // Statement: every posting, newest first.
  'statement.title': 'Statement',
  'statement.loading': 'Loading your statement...',
  'statement.error': 'We could not load your statement.',
  'statement.retry': 'Try again',
  'statement.empty': 'No movements in this period.',
  'statement.filter.all': 'All',
  'statement.filter.in': 'In',
  'statement.filter.out': 'Out',
  'statement.loadMore': 'Load more',
  'statement.runningBalance': 'Balance after',

  // Receipt: the detail behind a single statement line.
  'receipt.title': 'Receipt',
  'receipt.loading': 'Loading the receipt...',
  'receipt.error': 'We could not load this receipt.',
  'receipt.retry': 'Try again',
  'receipt.amount': 'Amount',
  'receipt.date': 'Date',
  'receipt.rail': 'Method',
  'receipt.status': 'Status',
  'receipt.counterparty': 'To/From',
} as const;

export type MessageKey = keyof typeof messages;

/**
 * Resolves a key, optionally filling `{placeholders}`.
 *
 * Returns the key itself when it is missing, so a gap shows up as an obviously wrong label
 * instead of a blank space nobody notices.
 */
export function t(key: MessageKey, values: Record<string, string> = {}): string {
  const template: string = messages[key] ?? key;
  return Object.entries(values).reduce(
    (text, [name, value]) => text.replace(`{${name}}`, value),
    template,
  );
}
