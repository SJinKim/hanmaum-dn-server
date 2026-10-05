-- HDN-272: how a member record was created. Backfill approximates old rows: a newcomer
-- profile means the 새가족 intake; otherwise a Keycloak subject means app registration.
-- A manual member that has already claimed an account is therefore reported as APP.
ALTER TABLE members ADD COLUMN origin VARCHAR(20);

UPDATE members m
SET origin = CASE
    WHEN EXISTS (SELECT 1 FROM newcomer_profiles p WHERE p.member_id = m.id) THEN 'NEWCOMER_FORM'
    WHEN m.keycloak_id IS NOT NULL THEN 'APP'
    ELSE 'MANUAL'
END;

ALTER TABLE members ALTER COLUMN origin SET NOT NULL;
ALTER TABLE members ALTER COLUMN origin SET DEFAULT 'MANUAL';
ALTER TABLE members ADD CONSTRAINT ck_members_origin CHECK (origin IN ('MANUAL', 'NEWCOMER_FORM', 'APP'));
