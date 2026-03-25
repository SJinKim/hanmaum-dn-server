-- =================================================================
-- V4: Add ministry tables + backfill missing public_id columns
--
-- Context: ddl-auto=validate requires every BaseEntity table to have
-- public_id. V1 and V3 created several tables before public_id was
-- standardised. This migration closes that gap and adds the two new
-- ministry tables required by Ministry.kt and MinistryRegistration.kt
-- =================================================================

-- Ensure pgcrypto is available (idempotent)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- -----------------------------------------------------------------
-- 1. BACKFILL: church_groups (created in V1, missing BaseEntity cols)
-- -----------------------------------------------------------------
ALTER TABLE church_groups
    ADD COLUMN IF NOT EXISTS public_id  UUID         DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

UPDATE church_groups SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE church_groups
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE church_groups
    DROP CONSTRAINT IF EXISTS uq_church_groups_public_id;
ALTER TABLE church_groups
    ADD CONSTRAINT uq_church_groups_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_church_groups_public_id ON church_groups (public_id);


-- -----------------------------------------------------------------
-- 2. BACKFILL: attendance_logs (created in V1, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE attendance_logs SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE attendance_logs
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE attendance_logs
    DROP CONSTRAINT IF EXISTS uq_attendance_logs_public_id;
ALTER TABLE attendance_logs
    ADD CONSTRAINT uq_attendance_logs_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_public_id ON attendance_logs (public_id);


-- -----------------------------------------------------------------
-- 3. BACKFILL: attendance_definitions (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE attendance_definitions
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE attendance_definitions SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE attendance_definitions
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE attendance_definitions
    DROP CONSTRAINT IF EXISTS uq_attendance_definitions_public_id;
ALTER TABLE attendance_definitions
    ADD CONSTRAINT uq_attendance_definitions_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_attendance_definitions_public_id ON attendance_definitions (public_id);


-- -----------------------------------------------------------------
-- 4. BACKFILL: cars (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE cars
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE cars SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE cars
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE cars
    DROP CONSTRAINT IF EXISTS uq_cars_public_id;
ALTER TABLE cars
    ADD CONSTRAINT uq_cars_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_cars_public_id ON cars (public_id);


-- -----------------------------------------------------------------
-- 5. BACKFILL: car_passengers (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE car_passengers
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE car_passengers SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE car_passengers
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE car_passengers
    DROP CONSTRAINT IF EXISTS uq_car_passengers_public_id;
ALTER TABLE car_passengers
    ADD CONSTRAINT uq_car_passengers_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_car_passengers_public_id ON car_passengers (public_id);


-- -----------------------------------------------------------------
-- 6. BACKFILL: dishwashing_schedule (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE dishwashing_schedule
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE dishwashing_schedule SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE dishwashing_schedule
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE dishwashing_schedule
    DROP CONSTRAINT IF EXISTS uq_dishwashing_schedule_public_id;
ALTER TABLE dishwashing_schedule
    ADD CONSTRAINT uq_dishwashing_schedule_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_dishwashing_schedule_public_id ON dishwashing_schedule (public_id);


-- -----------------------------------------------------------------
-- 7. BACKFILL: group_meetings (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE group_meetings
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE group_meetings SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE group_meetings
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE group_meetings
    DROP CONSTRAINT IF EXISTS uq_group_meetings_public_id;
ALTER TABLE group_meetings
    ADD CONSTRAINT uq_group_meetings_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_group_meetings_public_id ON group_meetings (public_id);


-- -----------------------------------------------------------------
-- 8. BACKFILL: meeting_attendances (created in V3, missing public_id)
-- -----------------------------------------------------------------
ALTER TABLE meeting_attendances
    ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

UPDATE meeting_attendances SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE meeting_attendances
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE meeting_attendances
    DROP CONSTRAINT IF EXISTS uq_meeting_attendances_public_id;
ALTER TABLE meeting_attendances
    ADD CONSTRAINT uq_meeting_attendances_public_id UNIQUE (public_id);

CREATE INDEX IF NOT EXISTS idx_meeting_attendances_public_id ON meeting_attendances (public_id);


-- =================================================================
-- 9. NEW TABLE: ministries
--    Maps to: Ministry.kt
-- =================================================================
CREATE TABLE ministries
(
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    description        TEXT         NOT NULL,
    leader_member_id   BIGINT       REFERENCES members (id) ON DELETE SET NULL,
    image_url          VARCHAR(255),
    is_ministry_active BOOLEAN      NOT NULL        DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL        DEFAULT NOW(),
    updated_at         TIMESTAMP,
    deleted_at         TIMESTAMP
);

CREATE INDEX idx_ministries_public_id     ON ministries (public_id);
CREATE INDEX idx_ministries_leader_member ON ministries (leader_member_id);


-- =================================================================
-- 10. NEW TABLE: ministry_registrations
--     Maps to: MinistryRegistration.kt
--     Unique constraint: (ministry_id, member_id, registration_period)
-- =================================================================
CREATE TABLE ministry_registrations
(
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID      NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ministry_id         BIGINT    NOT NULL REFERENCES ministries (id) ON DELETE CASCADE,
    member_id           BIGINT    NOT NULL,
    note                TEXT,
    registration_period VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP NOT NULL        DEFAULT NOW(),
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,

    CONSTRAINT uq_ministry_member_period UNIQUE (ministry_id, member_id, registration_period)
);

CREATE INDEX idx_ministry_registrations_public_id   ON ministry_registrations (public_id);
CREATE INDEX idx_ministry_registrations_ministry_id ON ministry_registrations (ministry_id);
CREATE INDEX idx_ministry_registrations_member_id   ON ministry_registrations (member_id);
