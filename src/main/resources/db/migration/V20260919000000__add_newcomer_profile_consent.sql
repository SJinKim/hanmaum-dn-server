-- HDN-181: add newcomer consent fields after the initial profile migration.
ALTER TABLE newcomer_profiles
    ADD COLUMN consent_version VARCHAR(50),
    ADD COLUMN consented_at TIMESTAMPTZ;
