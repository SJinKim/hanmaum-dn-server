-- V1774647410890: Align attendance schema with F3 spec
--
-- attendance_definitions:
--   Add public_id UUID, rename start_time→window_start, end_time→window_end,
--   make title NOT NULL
--
-- attendance_logs:
--   TRUNCATE (pre-production, empty), add definition_id FK NOT NULL,
--   rename date→attendance_date, drop category + status,
--   add attended BOOLEAN, add unique constraint

-- ── attendance_definitions ──────────────────────────────────────────────────

ALTER TABLE attendance_definitions
    ADD COLUMN IF NOT EXISTS public_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE attendance_definitions
    DROP CONSTRAINT IF EXISTS uq_attendance_definitions_public_id;

ALTER TABLE attendance_definitions
    ADD CONSTRAINT uq_attendance_definitions_public_id UNIQUE (public_id);

ALTER TABLE attendance_definitions
    RENAME COLUMN start_time TO window_start;

ALTER TABLE attendance_definitions
    RENAME COLUMN end_time TO window_end;

UPDATE attendance_definitions SET title = 'Unnamed' WHERE title IS NULL;

ALTER TABLE attendance_definitions
    ALTER COLUMN title SET NOT NULL;

-- ── attendance_logs ──────────────────────────────────────────────────────────

-- Pre-production: truncate to allow schema changes without data migration pain
TRUNCATE TABLE attendance_logs;

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS definition_id BIGINT
        REFERENCES attendance_definitions(id) ON DELETE CASCADE;

-- Make definition_id NOT NULL now that table is empty
ALTER TABLE attendance_logs
    ALTER COLUMN definition_id SET NOT NULL;

ALTER TABLE attendance_logs
    RENAME COLUMN date TO attendance_date;

ALTER TABLE attendance_logs
    DROP COLUMN IF EXISTS category;

ALTER TABLE attendance_logs
    DROP COLUMN IF EXISTS status;

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS attended BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE attendance_logs
    DROP CONSTRAINT IF EXISTS uq_attendance_log;

ALTER TABLE attendance_logs
    ADD CONSTRAINT uq_attendance_log
        UNIQUE (member_id, definition_id, attendance_date);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_definition_id ON attendance_logs (definition_id);
CREATE INDEX IF NOT EXISTS idx_attendance_definitions_public_id ON attendance_definitions (public_id);
