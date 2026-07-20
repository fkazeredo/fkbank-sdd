-- Opening an account: who the person is, what they sign in with, the application that brought
-- them here, and the current account itself.
--
-- As in the accounting core, the states this flow must never reach are made impossible by the
-- schema rather than merely discouraged in Java. The rule that matters most here is that one
-- CPF can produce at most one customer, and that two applications arriving at the same instant
-- cannot both become one.
--
-- All timestamps are UTC.

-- A person registered with the bank.
CREATE TABLE customer (
    id             UUID           PRIMARY KEY,
    full_name      TEXT           NOT NULL,
    cpf            VARCHAR(11)    NOT NULL UNIQUE,
    email          TEXT           NOT NULL UNIQUE,
    birth_date     DATE           NOT NULL,
    monthly_income NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT customer_cpf_is_eleven_digits CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT customer_monthly_income_not_negative CHECK (monthly_income >= 0)
);

COMMENT ON TABLE customer IS
    'Registration data. CPF is stored normalized to digits so formatting cannot defeat uniqueness.';

COMMENT ON COLUMN customer.monthly_income IS
    'Self-reported figure feeding the limits engine. It is not a balance and never funds a posting.';

-- What a person signs in with. Separate from the customer because authentication has its own
-- lifecycle: a credential can be deactivated, rotated or locked without touching registration.
CREATE TABLE credential (
    id            UUID        PRIMARY KEY,
    customer_id   UUID        NOT NULL UNIQUE REFERENCES customer (id),
    username      TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE credential IS
    'Sign-in credentials. Only the hash is ever stored; the submitted password is never persisted.';

-- Account numbers are handed out by the database rather than computed from a count, so two
-- accounts opened at the same moment cannot be given the same number.
CREATE SEQUENCE current_account_number_seq START WITH 1 INCREMENT BY 1;

-- The customer's current account. Named to distinguish it from a ledger account: this is the
-- product a person holds, whereas an entry in the chart of accounts is where its money is
-- recorded.
CREATE TABLE current_account (
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL UNIQUE REFERENCES customer (id),
    branch      VARCHAR(4)  NOT NULL,
    number      VARCHAR(10) NOT NULL,
    opened_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT current_account_branch_and_number_unique UNIQUE (branch, number)
);

COMMENT ON TABLE current_account IS
    'One current account per customer. Its money lives in the ledger account customer:available:<customer id>.';

-- An application to open an account, from submission to outcome. It keeps its own copy of the
-- submitted data because a customer row only exists once the bureau approves, and it keeps the
-- password already hashed so an application waiting on the bureau never holds a readable secret.
CREATE TABLE onboarding (
    id              UUID           PRIMARY KEY,
    cpf             VARCHAR(11)    NOT NULL,
    full_name       TEXT           NOT NULL,
    email           TEXT           NOT NULL,
    birth_date      DATE           NOT NULL,
    monthly_income  NUMERIC(19, 2) NOT NULL,
    password_hash   TEXT           NOT NULL,
    status          TEXT           NOT NULL,
    reason_category TEXT,
    customer_id     UUID           REFERENCES customer (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT onboarding_cpf_is_eleven_digits CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT onboarding_status_known CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT onboarding_reason_known CHECK (reason_category IS NULL OR reason_category IN (
        'DOCUMENT_MISMATCH',
        'SANCTIONS_LIST',
        'INCOMPLETE_RECORD',
        'UNSPECIFIED'
    )),
    -- A reason explains a refusal and belongs to nothing else; a customer exists only once the
    -- application succeeded. Both are stated here so a row cannot describe an outcome it did
    -- not reach.
    CONSTRAINT onboarding_reason_only_when_rejected CHECK (
        status = 'REJECTED' OR reason_category IS NULL),
    CONSTRAINT onboarding_customer_exactly_when_approved CHECK (
        (status = 'APPROVED') = (customer_id IS NOT NULL))
);

COMMENT ON TABLE onboarding IS
    'Applications to open an account. Resubmitting a CPF that is still pending returns the same row.';

-- At most one application per CPF may be in flight.
--
-- This is what settles two sign-ups for the same CPF arriving together: both check and find
-- nothing, both insert, and the database refuses the second. The loser reads the winner's row
-- and returns it, so the person sees one application rather than an error. The index is
-- partial so that a refused applicant is not barred from ever applying again, and an approved
-- one is already blocked by the unique CPF on the customer.
CREATE UNIQUE INDEX onboarding_one_pending_per_cpf ON onboarding (cpf) WHERE status = 'PENDING';

CREATE INDEX onboarding_cpf_idx ON onboarding (cpf);
