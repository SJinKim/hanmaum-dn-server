-- HDN-181: Store the newcomer first-visit date through the PII encryption converter.

ALTER TABLE newcomer_profiles
    ALTER COLUMN first_visit_date TYPE TEXT USING first_visit_date::TEXT;
