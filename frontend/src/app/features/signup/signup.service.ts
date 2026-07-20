import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/** Where an application to open an account can stand. */
export type OnboardingStatus = 'APPROVED' | 'REJECTED' | 'PENDING';

/** Why an application was turned down, in categories rather than the checker's own words. */
export type RejectionReason =
  'DOCUMENT_MISMATCH' | 'SANCTIONS_LIST' | 'INCOMPLETE_RECORD' | 'UNSPECIFIED';

/** The details a person supplies to open an account. CPF is sent as digits only. */
export interface SignupRequest {
  readonly fullName: string;
  readonly cpf: string;
  readonly email: string;
  readonly password: string;
  readonly birthDate: string;
  readonly monthlyIncome: string;
}

/** An application, as the backend reports it. */
export interface Onboarding {
  readonly onboardingId: string;
  readonly status: OnboardingStatus;
  readonly reasonCategory: RejectionReason | null;
}

/**
 * What a submission amounted to, in the terms the screen actually reacts to.
 *
 * The screen branches on this and never on a status code, which keeps the transport shape in
 * one place: an application still being checked reaches the person the same way whether the
 * check has only just started or a previous attempt is still running.
 */
export type SignupOutcome =
  | { readonly kind: 'approved'; readonly onboarding: Onboarding }
  | { readonly kind: 'rejected'; readonly onboarding: Onboarding }
  | { readonly kind: 'pending'; readonly onboarding: Onboarding }
  | { readonly kind: 'duplicate' }
  | { readonly kind: 'invalid'; readonly fieldErrors: Readonly<Record<string, string>> }
  | { readonly kind: 'failed' };

/** Status code returned when the CPF or e-mail already belongs to a customer. */
const CONFLICT = 409;

/** Status code returned when the submitted details fail the server's own validation. */
const UNPROCESSABLE = 422;

/**
 * Reads field-level messages out of an error payload.
 *
 * The shape is probed rather than assumed. A validation response that arrives in an
 * unrecognised form yields no field errors, and the screen falls back to a general message —
 * far better than surfacing a raw payload or dropping the response on the floor.
 */
function readFieldErrors(payload: unknown): Record<string, string> {
  if (typeof payload !== 'object' || payload === null) {
    return {};
  }

  const container = payload as Record<string, unknown>;
  const properties = container['properties'];
  const source =
    container['errors'] ??
    (typeof properties === 'object' && properties !== null
      ? (properties as Record<string, unknown>)['errors']
      : undefined);

  if (Array.isArray(source)) {
    const collected: Record<string, string> = {};
    for (const entry of source) {
      if (typeof entry !== 'object' || entry === null) {
        continue;
      }
      const item = entry as Record<string, unknown>;
      const field = item['field'] ?? item['name'] ?? item['pointer'];
      const message = item['message'] ?? item['detail'] ?? item['reason'];
      if (typeof field === 'string' && typeof message === 'string') {
        collected[field] = message;
      }
    }
    return collected;
  }

  if (typeof source === 'object' && source !== null) {
    const collected: Record<string, string> = {};
    for (const [field, message] of Object.entries(source as Record<string, unknown>)) {
      if (typeof message === 'string') {
        collected[field] = message;
      }
    }
    return collected;
  }

  return {};
}

/** Talks to the account-opening endpoints. */
@Injectable({ providedIn: 'root' })
export class SignupService {
  private readonly http = inject(HttpClient);

  /**
   * Submits an application to open an account.
   *
   * Resolves rather than rejects: a refusal is an answer the person needs to see, not an
   * exception the screen has to catch. Only an unrecognised failure becomes `failed`.
   *
   * @param request the applicant's details, with the CPF already reduced to digits
   */
  async submit(request: SignupRequest): Promise<SignupOutcome> {
    try {
      const onboarding = await firstValueFrom(this.http.post<Onboarding>('/api/signup', request));

      return this.classify(onboarding);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        return { kind: 'failed' };
      }

      if (error.status === CONFLICT) {
        return { kind: 'duplicate' };
      }

      if (error.status === UNPROCESSABLE) {
        return { kind: 'invalid', fieldErrors: readFieldErrors(error.error) };
      }

      return { kind: 'failed' };
    }
  }

  /**
   * Re-reads an application that was still being checked.
   *
   * Reading a status opens nothing and changes nothing, so a person may ask as often as they
   * like while a slow check completes.
   *
   * @param onboardingId the opaque identifier the submission returned
   */
  async check(onboardingId: string): Promise<SignupOutcome> {
    try {
      const onboarding = await firstValueFrom(
        this.http.get<Onboarding>(`/api/signup/${encodeURIComponent(onboardingId)}`),
      );

      return this.classify(onboarding);
    } catch {
      return { kind: 'failed' };
    }
  }

  private classify(onboarding: Onboarding): SignupOutcome {
    switch (onboarding.status) {
      case 'APPROVED':
        return { kind: 'approved', onboarding };
      case 'REJECTED':
        return { kind: 'rejected', onboarding };
      case 'PENDING':
        return { kind: 'pending', onboarding };
      default:
        // A status this build does not know about must not be read as success.
        return { kind: 'failed' };
    }
  }
}
