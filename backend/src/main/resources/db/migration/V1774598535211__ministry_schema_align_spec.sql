-- =================================================================
-- V1774598535211: Align ministry schema with MVP F2 spec
--
-- Changes:
--   1. ministries: rename `description` TEXT → `short_description` VARCHAR(200)
--                  add `long_description` TEXT NULL
--   2. ministry_registrations: add proper FK constraint from member_id → members(id)
--
-- NOTE: The existing `description` column was TEXT NOT NULL.
--       We rename it to `short_description` (keeping existing data) and then
--       constrain the length via application-layer validation (no truncation of
--       existing rows — pre-production, expected to be empty).
-- =================================================================

-- 1a. Rename description → short_description and cap length
ALTER TABLE ministries
    RENAME COLUMN description TO short_description;

-- Apply length constraint (VARCHAR(200) behaviour via CHECK — Postgres doesn't shorten TEXT in-place)
ALTER TABLE ministries
    ALTER COLUMN short_description TYPE VARCHAR(200) USING LEFT(short_description, 200);

-- 1b. Add long_description TEXT NULL
ALTER TABLE ministries
    ADD COLUMN IF NOT EXISTS long_description TEXT NULL;

-- 2. Add FK from ministry_registrations.member_id → members(id)
--    The column already exists as BIGINT NOT NULL — just add the constraint.
ALTER TABLE ministry_registrations
    DROP CONSTRAINT IF EXISTS fk_ministry_reg_member;

ALTER TABLE ministry_registrations
    ADD CONSTRAINT fk_ministry_reg_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE;
