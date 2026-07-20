-- The bureau correlates its callback with a reference the bank chooses, and that reference must
-- not be something the applicant knows.
--
-- It used to be the onboarding's own id, which is returned to the applicant and appears in the
-- public status URL. Anyone holding a callback signing key could therefore name a real
-- application to decide, using nothing but a value that application had already handed out. The
-- signature is still what authenticates the bureau; this removes the second ingredient.
--
-- The bureau's own inquiry id cannot serve this purpose: on the path where the callback matters,
-- the bank timed out waiting for the response that would have carried it.

ALTER TABLE onboarding
    ADD COLUMN bureau_reference UUID NOT NULL DEFAULT gen_random_uuid();

-- The default exists only to fill rows written before this column did. New applications supply
-- their own reference, and a generated default would quietly hide a bug that stopped them doing
-- so.
ALTER TABLE onboarding
    ALTER COLUMN bureau_reference DROP DEFAULT;

ALTER TABLE onboarding
    ADD CONSTRAINT onboarding_bureau_reference_unique UNIQUE (bureau_reference);

COMMENT ON COLUMN onboarding.bureau_reference IS
    'What the bureau echoes back to name this application. Never disclosed to the applicant.';
