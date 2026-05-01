-- HDN-0: Migrate all TIMESTAMP columns to TIMESTAMPTZ for timezone-aware storage
--
-- Context: App is deployed in Europe/Berlin (CET/CEST). TIMESTAMP without TZ is
-- ambiguous across DST transitions and environment changes. TIMESTAMPTZ stores UTC
-- and lets the application/client render in any timezone correctly.
--
-- USING … AT TIME ZONE 'UTC': existing data is assumed to have been written in UTC
-- (Hibernate default via JDBC). Pre-MVP — no production data at risk.

-- -----------------------------------------------------------------
-- church_groups (created_at was added in V1, updated_at/deleted_at in V4)
-- -----------------------------------------------------------------
ALTER TABLE church_groups
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- members
-- -----------------------------------------------------------------
ALTER TABLE members
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- family_relationships
-- -----------------------------------------------------------------
ALTER TABLE family_relationships
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- attendance_logs
-- -----------------------------------------------------------------
ALTER TABLE attendance_logs
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- announcements (includes business timestamps start_at, end_at)
-- -----------------------------------------------------------------
ALTER TABLE announcements
    ALTER COLUMN start_at    TYPE TIMESTAMPTZ USING start_at    AT TIME ZONE 'UTC',
    ALTER COLUMN end_at      TYPE TIMESTAMPTZ USING end_at      AT TIME ZONE 'UTC',
    ALTER COLUMN created_at  TYPE TIMESTAMPTZ USING created_at  AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at  TYPE TIMESTAMPTZ USING updated_at  AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at  TYPE TIMESTAMPTZ USING deleted_at  AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- attendance_definitions
-- -----------------------------------------------------------------
ALTER TABLE attendance_definitions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- cars
-- -----------------------------------------------------------------
ALTER TABLE cars
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- car_passengers
-- -----------------------------------------------------------------
ALTER TABLE car_passengers
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- dishwashing_schedule
-- -----------------------------------------------------------------
ALTER TABLE dishwashing_schedule
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- group_meetings (includes business timestamp meeting_time)
-- -----------------------------------------------------------------
ALTER TABLE group_meetings
    ALTER COLUMN meeting_time TYPE TIMESTAMPTZ USING meeting_time AT TIME ZONE 'UTC',
    ALTER COLUMN created_at   TYPE TIMESTAMPTZ USING created_at   AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at   TYPE TIMESTAMPTZ USING updated_at   AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at   TYPE TIMESTAMPTZ USING deleted_at   AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- meeting_attendances
-- -----------------------------------------------------------------
ALTER TABLE meeting_attendances
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- ministries
-- -----------------------------------------------------------------
ALTER TABLE ministries
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

-- -----------------------------------------------------------------
-- ministry_registrations
-- -----------------------------------------------------------------
ALTER TABLE ministry_registrations
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
