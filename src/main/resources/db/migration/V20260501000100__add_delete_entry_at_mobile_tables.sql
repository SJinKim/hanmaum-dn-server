-- HDN-0: Add delete_entry_at to mobile-facing tables for scheduled hard-delete purge
--
-- Applies to: attendance_logs, announcements, ministry_registrations.
-- Members are excluded — member deletion is a separate admin-triggered flow
-- that must also wipe the Keycloak user and is never automated.
-- delete_entry_at = deleted_at + 30 days; set by application on soft-delete.
-- Cron job purges rows where delete_entry_at <= NOW() AND deleted_at IS NOT NULL.

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS delete_entry_at TIMESTAMPTZ;

UPDATE attendance_logs
SET delete_entry_at = deleted_at + INTERVAL '30 days'
WHERE deleted_at IS NOT NULL
  AND delete_entry_at IS NULL;

-- -----------------------------------------------------------------

ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS delete_entry_at TIMESTAMPTZ;

UPDATE announcements
SET delete_entry_at = deleted_at + INTERVAL '30 days'
WHERE deleted_at IS NOT NULL
  AND delete_entry_at IS NULL;

-- -----------------------------------------------------------------

ALTER TABLE ministry_registrations
    ADD COLUMN IF NOT EXISTS delete_entry_at TIMESTAMPTZ;

UPDATE ministry_registrations
SET delete_entry_at = deleted_at + INTERVAL '30 days'
WHERE deleted_at IS NOT NULL
  AND delete_entry_at IS NULL;
