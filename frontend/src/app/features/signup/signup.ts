import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageKey, t } from '../../shared/i18n/messages';
import { RejectionReason, SignupOutcome, SignupService } from './signup.service';
import {
  adultBirthDateValidator,
  cpfValidator,
  monthlyIncomeValidator,
  normalizeCpf,
  passwordValidator,
  withTwoDecimals,
} from './signup-validators';

/** What the screen is currently showing. */
type Phase = 'form' | 'submitting' | 'pending' | 'rechecking' | 'rejected' | 'duplicate';

/** The message shown for each category a check can come back with. */
const REASON_MESSAGES: Record<RejectionReason, MessageKey> = {
  DOCUMENT_MISMATCH: 'signup.reason.DOCUMENT_MISMATCH',
  SANCTIONS_LIST: 'signup.reason.SANCTIONS_LIST',
  INCOMPLETE_RECORD: 'signup.reason.INCOMPLETE_RECORD',
  UNSPECIFIED: 'signup.reason.UNSPECIFIED',
};

/**
 * The message each field shows for each way it can be wrong.
 *
 * Keyed by the validator's own error name, so adding a rule means adding its message here
 * rather than growing a chain of conditions in the template.
 */
const FIELD_MESSAGES: Record<string, Record<string, MessageKey>> = {
  fullName: {
    required: 'signup.error.fullName.required',
    minlength: 'signup.error.fullName.tooShort',
  },
  cpf: { required: 'signup.error.cpf.required', cpf: 'signup.error.cpf.invalid' },
  email: { required: 'signup.error.email.required', email: 'signup.error.email.invalid' },
  password: {
    required: 'signup.error.password.required',
    weakPassword: 'signup.error.password.weak',
  },
  birthDate: {
    required: 'signup.error.birthDate.required',
    invalidDate: 'signup.error.birthDate.invalid',
    futureDate: 'signup.error.birthDate.future',
    underage: 'signup.error.birthDate.underage',
  },
  monthlyIncome: {
    required: 'signup.error.monthlyIncome.required',
    monthlyIncome: 'signup.error.monthlyIncome.invalid',
  },
};

/** Fields that carry a permanent hint the input should be described by. */
const HINTED_FIELDS = new Set(['cpf', 'password', 'birthDate', 'monthlyIncome']);

/**
 * The screen where a person applies to open an account.
 *
 * Every rule enforced here is also enforced on the server; checking twice is not redundancy
 * but courtesy — it tells the person what is wrong before a round trip, while the answer that
 * actually decides anything still comes from the server. When the two disagree the server
 * wins, which is why a rejected submission can still paint messages onto these fields.
 */
@Component({
  selector: 'fk-signup',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signup.html',
})
export class Signup {
  private readonly formBuilder = inject(FormBuilder);
  private readonly signupService = inject(SignupService);
  private readonly router = inject(Router);

  readonly t = t;

  readonly form = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    cpf: ['', [Validators.required, cpfValidator]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, passwordValidator]],
    birthDate: ['', [Validators.required, adultBirthDateValidator()]],
    monthlyIncome: ['', [Validators.required, monthlyIncomeValidator]],
  });

  readonly phase = signal<Phase>('form');
  readonly banner = signal<string | null>(null);
  readonly rejectionReason = signal<string | null>(null);

  private readonly submitted = signal(false);
  private readonly serverErrors = signal<Readonly<Record<string, string>>>({});
  private onboardingId: string | null = null;

  /**
   * The message to show under a field, or null when it has nothing to say.
   *
   * A message the server sent takes precedence: it knows things this browser cannot, such as
   * whether an e-mail is already taken.
   */
  messageFor(field: string): string | null {
    const fromServer = this.serverErrors()[field];
    if (fromServer) {
      return fromServer;
    }

    const control = this.form.get(field);
    if (!control || control.valid || !(control.touched || this.submitted())) {
      return null;
    }

    const messages = FIELD_MESSAGES[field] ?? {};
    for (const name of Object.keys(control.errors ?? {})) {
      const key = messages[name];
      if (key) {
        return t(key);
      }
    }

    return null;
  }

  /** Whether a field is currently showing an error, for `aria-invalid`. */
  isInvalid(field: string): boolean {
    return this.messageFor(field) !== null;
  }

  /**
   * The elements that describe a field: its hint, and its error while one is showing.
   *
   * Returns null rather than an empty string so the attribute is omitted entirely instead of
   * pointing a screen reader at nothing.
   */
  describedBy(field: string): string | null {
    const parts: string[] = [];

    if (HINTED_FIELDS.has(field)) {
      parts.push(`${field}-hint`);
    }
    if (this.isInvalid(field)) {
      parts.push(`${field}-error`);
    }

    return parts.length ? parts.join(' ') : null;
  }

  /** Drops a server message for a field as soon as the person edits that field. */
  clearServerError(field: string): void {
    const current = this.serverErrors();
    if (!(field in current)) {
      return;
    }

    const remaining = { ...current };
    delete remaining[field];
    this.serverErrors.set(remaining);
  }

  async submit(): Promise<void> {
    this.submitted.set(true);
    this.serverErrors.set({});
    this.banner.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.banner.set(t('signup.invalid'));
      return;
    }

    this.phase.set('submitting');

    const value = this.form.getRawValue();
    const outcome = await this.signupService.submit({
      fullName: value.fullName.trim(),
      cpf: normalizeCpf(value.cpf),
      email: value.email.trim(),
      password: value.password,
      birthDate: value.birthDate,
      monthlyIncome: withTwoDecimals(value.monthlyIncome),
    });

    await this.apply(outcome);
  }

  /** Asks again about an application whose check had not finished. */
  async recheck(): Promise<void> {
    if (!this.onboardingId) {
      return;
    }

    this.phase.set('rechecking');
    this.banner.set(null);

    const outcome = await this.signupService.check(this.onboardingId);

    if (outcome.kind === 'pending') {
      this.phase.set('pending');
      this.banner.set(t('signup.pending.stillPending'));
      return;
    }

    await this.apply(outcome);
  }

  /** Returns a rejected applicant to the form so they can correct what they entered. */
  backToForm(): void {
    this.phase.set('form');
    this.banner.set(null);
    this.rejectionReason.set(null);
  }

  private async apply(outcome: SignupOutcome): Promise<void> {
    switch (outcome.kind) {
      case 'approved':
        await this.router.navigate(['/signin']);
        return;

      case 'pending':
        this.onboardingId = outcome.onboarding.onboardingId;
        this.phase.set('pending');
        return;

      case 'rejected':
        this.rejectionReason.set(
          t(REASON_MESSAGES[outcome.onboarding.reasonCategory ?? 'UNSPECIFIED']),
        );
        this.phase.set('rejected');
        return;

      case 'duplicate':
        this.phase.set('duplicate');
        return;

      case 'invalid':
        this.serverErrors.set(outcome.fieldErrors);
        this.phase.set('form');
        this.banner.set(t('signup.invalid'));
        return;

      default:
        this.phase.set('form');
        this.banner.set(t('signup.failed'));
    }
  }
}
