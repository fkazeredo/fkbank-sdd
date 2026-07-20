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
