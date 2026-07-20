import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SignupRequest, SignupService } from './signup.service';

const A_REQUEST: SignupRequest = {
  fullName: 'Ada Lovelace',
  cpf: '12345678909',
  email: 'ada@example.com',
  password: 'secret123',
  birthDate: '1990-05-20',
  monthlyIncome: '4500.00',
};

const AN_ID = '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0';

describe('SignupService', () => {
  let service: SignupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SignupService);
    http = TestBed.inject(HttpTestingController);
  });

  it('posts the applicant details as given', async () => {
    const outcome = service.submit(A_REQUEST);

    const request = http.expectOne('/api/signup');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(A_REQUEST);

    request.flush({ onboardingId: AN_ID, status: 'APPROVED', reasonCategory: null });
    await outcome;
  });

  it('routes an approved application', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { onboardingId: AN_ID, status: 'APPROVED', reasonCategory: null },
        { status: 201, statusText: 'Created' },
      );

    expect(await outcome).toEqual({
      kind: 'approved',
      onboarding: { onboardingId: AN_ID, status: 'APPROVED', reasonCategory: null },
    });
  });

  it('routes a rejected application and carries the reason category through', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { onboardingId: AN_ID, status: 'REJECTED', reasonCategory: 'DOCUMENT_MISMATCH' },
        { status: 201, statusText: 'Created' },
      );

    const result = await outcome;
    expect(result.kind).toBe('rejected');
    expect(result.kind === 'rejected' && result.onboarding.reasonCategory).toBe(
      'DOCUMENT_MISMATCH',
    );
  });

  it('routes a still-running check accepted as 202', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { onboardingId: AN_ID, status: 'PENDING', reasonCategory: null },
        { status: 202, statusText: 'Accepted' },
      );

    expect(await outcome).toEqual({
      kind: 'pending',
      onboarding: { onboardingId: AN_ID, status: 'PENDING', reasonCategory: null },
    });
  });

  it('routes a resubmission answered as 200 the same way as a fresh 202', async () => {
    // The two differ only in status code; a person waiting on a check must not see two
    // different screens depending on whether this was their first attempt.
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { onboardingId: AN_ID, status: 'PENDING', reasonCategory: null },
        { status: 200, statusText: 'OK' },
      );

    expect(await outcome).toEqual({
      kind: 'pending',
      onboarding: { onboardingId: AN_ID, status: 'PENDING', reasonCategory: null },
    });
  });

  it('routes a conflict to the duplicate outcome', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { code: 'DUPLICATE_CUSTOMER', title: 'Conflict' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(await outcome).toEqual({ kind: 'duplicate' });
  });

  it('reads field errors from a validation failure sent as an array', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { errors: [{ field: 'cpf', message: 'CPF is not valid.' }] },
        { status: 422, statusText: 'Unprocessable Content' },
      );

    expect(await outcome).toEqual({
      kind: 'invalid',
      fieldErrors: { cpf: 'CPF is not valid.' },
    });
  });

  it('reads field errors nested under the problem detail properties', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush(
        { title: 'Unprocessable', properties: { errors: { email: 'E-mail is not valid.' } } },
        { status: 422, statusText: 'Unprocessable Content' },
      );

    expect(await outcome).toEqual({
      kind: 'invalid',
      fieldErrors: { email: 'E-mail is not valid.' },
    });
  });

  it('still reports a validation failure whose shape it cannot read', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush('nothing recognisable', { status: 422, statusText: 'Unprocessable Content' });

    expect(await outcome).toEqual({ kind: 'invalid', fieldErrors: {} });
  });

  it('treats any other failure as a general failure', async () => {
    const outcome = service.submit(A_REQUEST);

    http.expectOne('/api/signup').flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(await outcome).toEqual({ kind: 'failed' });
  });

  it('does not read an unknown status as success', async () => {
    const outcome = service.submit(A_REQUEST);

    http
      .expectOne('/api/signup')
      .flush({ onboardingId: AN_ID, status: 'ESCALATED', reasonCategory: null });

    expect(await outcome).toEqual({ kind: 'failed' });
  });

  it('re-reads an application by its identifier', async () => {
    const outcome = service.check(AN_ID);

    const request = http.expectOne(`/api/signup/${AN_ID}`);
    expect(request.request.method).toBe('GET');

    request.flush({ onboardingId: AN_ID, status: 'APPROVED', reasonCategory: null });

    expect((await outcome).kind).toBe('approved');
  });

  it('escapes an identifier before putting it in the path', async () => {
    const outcome = service.check('a/../b');

    http.expectOne('/api/signup/a%2F..%2Fb').flush({
      onboardingId: 'a/../b',
      status: 'PENDING',
      reasonCategory: null,
    });

    expect((await outcome).kind).toBe('pending');
  });

  it('reports a failed re-read rather than throwing at the screen', async () => {
    const outcome = service.check(AN_ID);

    http.expectOne(`/api/signup/${AN_ID}`).flush({}, { status: 404, statusText: 'Not Found' });

    expect(await outcome).toEqual({ kind: 'failed' });
  });
});
