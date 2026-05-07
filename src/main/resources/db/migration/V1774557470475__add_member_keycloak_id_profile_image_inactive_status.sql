-- =================================================================
-- V1774557470475: Member entity additions for MVP F1
--
-- Changes:
--   1. Add keycloak_id column (links member to Keycloak subject UUID)
--   2. Add profile_image_url column
--   3. Normalise member_status: map PENDING/REJECTED → ACTIVE/INACTIVE
--      so existing data stays valid against the updated MemberStatus enum
-- =================================================================

-- -----------------------------------------------------------------
-- 1. keycloak_id — Keycloak subject UUID stored after self-registration
-- -----------------------------------------------------------------
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(255);

-- Unique index: one Keycloak user per member row (nullable for admin-created members)
CREATE UNIQUE INDEX IF NOT EXISTS uq_members_keycloak_id
    ON members (keycloak_id)
    WHERE keycloak_id IS NOT NULL;


-- -----------------------------------------------------------------
-- 2. profile_image_url — optional profile picture URL
-- -----------------------------------------------------------------
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);


-- -----------------------------------------------------------------
-- 3. Normalise member_status values
--    Old values not in the new enum (ACTIVE | INACTIVE | DELETED):
--      PENDING  → ACTIVE   (awaiting approval = treat as active for now)
--      REJECTED → INACTIVE (was rejected = treat as inactive)
-- -----------------------------------------------------------------
UPDATE members
SET member_status = 'ACTIVE'
WHERE member_status = 'PENDING';

UPDATE members
SET member_status = 'INACTIVE'
WHERE member_status = 'REJECTED';
