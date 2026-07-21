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
 * What the server said when it rejected a submission's contents.
 *
 * The 422 arrives as a flat problem+json document: a stable {@link code} the screen can pin to a
 * field, and a human-readable {@link detail} to fall back on when it cannot. There is no
 * per-field breakdown, so neither is assumed present.
 */
export interface SubmissionProblem {
  readonly code: string | null;
  readonly detail: string | null;
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
  | { readonly kind: 'invalid'; readonly problem: SubmissionProblem }
  | { readonly kind: 'failed' };

/** Status code returned when the CPF or e-mail already belongs to a customer. */
const CONFLICT = 409;

/** Status code returned when the submitted details fail the server's own validation. */
const UNPROCESSABLE = 422;

/**
 * Reads the code and detail out of a problem+json validation response.
 *
 * The server answers a 422 with a flat problem document — a stable `code` and a human-readable
 * `detail`, with no per-field breakdown. Both are read defensively: a response that arrives in
 * an unexpected shape yields nulls, and the screen falls back to a general message rather than
 * surfacing a raw payload or leaving the person with nothing to act on.
 */
function readProblem(payload: unknown): SubmissionProblem {
  if (typeof payload !== 'object' || payload === null) {
    return { code: null, detail: null };
  }

  const container = payload as Record<string, unknown>;
  const code = typeof container['code'] === 'string' ? container['code'] : null;
  const detail = typeof container['detail'] === 'string' ? container['detail'] : null;

  return { code, detail };
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
        return { kind: 'invalid', problem: readProblem(error.error) };
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
