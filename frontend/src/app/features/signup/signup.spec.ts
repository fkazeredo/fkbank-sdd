import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Signup } from './signup';
import { t } from '../../shared/i18n/messages';

/** A form that clears every field-level validator, so a test can spoil exactly one thing. */
const VALID_FORM = {
  fullName: 'Ada Lovelace',
  cpf: '52998224725',
  email: 'ada@example.com',
  password: 'secret123',
  birthDate: '1990-05-20',
  monthlyIncome: '4500.00',
};

describe('Signup', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('catches a one-word name inline and never sends the request', async () => {
    // "Bob" is the value F-01 was reported against: it cleared the old minLength(2), reached the
    // server, was refused for having no family name, and left the screen showing a general
    // message that pointed at nothing. The fix must stop it before the round trip AND say why.
    const fixture = TestBed.createComponent(Signup);
    const signup = fixture.componentInstance;
    signup.form.setValue({ ...VALID_FORM, fullName: 'Bob' });

    await signup.submit();

    http.expectNone('/api/signup');
    expect(signup.messageFor('fullName')).toBe(t('signup.error.fullName.incomplete'));
  });

  it('pins a server code it recognises onto that field', async () => {
    const fixture = TestBed.createComponent(Signup);
    const signup = fixture.componentInstance;
    signup.form.setValue(VALID_FORM);

    const submitted = signup.submit();
    http.expectOne('/api/signup').flush(
      { status: 422, detail: 'password must contain a letter and a digit', code: 'WEAK_PASSWORD' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await submitted;

    expect(signup.messageFor('password')).toBe(t('signup.error.password.weak'));
    expect(signup.phase()).toBe('form');
  });

  it('shows the server reason when a rejected submission maps to no field', async () => {
    // The empty dead-end this fix exists to remove: a 422 the screen cannot pin to a field must
    // still tell the person what the server objected to, not leave a blank "review the details".
    const fixture = TestBed.createComponent(Signup);
    const signup = fixture.componentInstance;
    signup.form.setValue(VALID_FORM);

    const submitted = signup.submit();
    http.expectOne('/api/signup').flush(
      { status: 422, detail: 'full name must include a family name', code: 'INVALID_SUBMISSION' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await submitted;

    expect(signup.banner()).toBe('full name must include a family name');
  });
});
