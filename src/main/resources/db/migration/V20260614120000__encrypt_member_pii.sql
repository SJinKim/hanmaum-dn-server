-- HDN-0: Prepare members for application-level PII encryption
--
-- Existing values are intentionally preserved as text. The application backfill
-- encrypts them with AES-256-GCM before production traffic is accepted.

DROP INDEX IF EXISTS uq_members_keycloak_id;

ALTER TABLE members
    ALTER COLUMN last_name TYPE TEXT,
    ALTER COLUMN first_name TYPE TEXT,
    ALTER COLUMN discriminator TYPE TEXT,
    ALTER COLUMN gender TYPE TEXT,
    ALTER COLUMN birth_date TYPE TEXT USING birth_date::TEXT,
    ALTER COLUMN phone_number TYPE TEXT,
    ALTER COLUMN email TYPE TEXT,
    ALTER COLUMN street TYPE TEXT,
    ALTER COLUMN house_number TYPE TEXT,
    ALTER COLUMN zip_code TYPE TEXT,
    ALTER COLUMN city TYPE TEXT,
    ALTER COLUMN registration_date TYPE TEXT USING registration_date::TEXT,
    ALTER COLUMN role TYPE TEXT,
    ALTER COLUMN baptism TYPE TEXT,
    ALTER COLUMN keycloak_id TYPE TEXT,
    ALTER COLUMN profile_image_url TYPE TEXT;

ALTER TABLE members
    ADD COLUMN email_lookup_hash VARCHAR(64),
    ADD COLUMN keycloak_lookup_hash VARCHAR(64),
    ADD COLUMN pii_key_id VARCHAR(50);

CREATE UNIQUE INDEX uq_members_email_lookup_hash
    ON members (email_lookup_hash)
    WHERE email_lookup_hash IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_members_keycloak_lookup_hash
    ON members (keycloak_lookup_hash)
    WHERE keycloak_lookup_hash IS NOT NULL AND deleted_at IS NULL;
