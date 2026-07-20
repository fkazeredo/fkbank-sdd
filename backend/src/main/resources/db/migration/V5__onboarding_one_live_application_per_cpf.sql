-- One live application per CPF, where an approved one counts as live.
--
-- The previous index covered only PENDING, which left a window a real race walks straight
-- through: the first application is approved and stops being pending, a second submission that
-- checked for an existing customer a moment earlier finds nothing pending, and its insert is
-- allowed. That second application then fails at the customer's own unique CPF - correctly,
-- and the person is told the account already exists - but the row it inserted was written in its
-- own transaction and survives the failure. The result is a stray pending application for a CPF
-- that already belongs to a customer.
--
-- Nothing is lost when that happens and no money is involved, but it is exactly the class of
-- state this schema exists to make impossible rather than merely unlikely: an application-level
-- check cannot settle a race it is on the wrong side of, and only the database can.
--
-- REJECTED stays outside the index, so a refused applicant may still apply again.

DROP INDEX onboarding_one_pending_per_cpf;

CREATE UNIQUE INDEX onboarding_one_live_per_cpf
    ON onboarding (cpf)
    WHERE status IN ('PENDING', 'APPROVED');

COMMENT ON INDEX onboarding_one_live_per_cpf IS
    'At most one pending or approved application per CPF. A refused one does not bar re-applying.';
