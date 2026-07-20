import { describe, expect, it } from 'vitest';
import { messages, t } from './messages';

describe('messages', () => {
  it('resolves a key to its en-US text', () => {
    expect(t('nav.account')).toBe('Account');
  });

  it('fills placeholders', () => {
    expect(t('feature.comingSoon', { feature: 'PIX' })).toBe('This is where PIX will live.');
  });

  it('leaves an unfilled placeholder visible rather than printing "undefined"', () => {
    expect(t('feature.comingSoon')).toContain('{feature}');
  });

  it('carries a label for every product navigation entry', () => {
    for (const key of ['nav.account', 'nav.pix', 'nav.pay', 'nav.boxes', 'nav.card', 'nav.credit'] as const) {
      expect(messages[key]).toBeTruthy();
    }
  });
});
