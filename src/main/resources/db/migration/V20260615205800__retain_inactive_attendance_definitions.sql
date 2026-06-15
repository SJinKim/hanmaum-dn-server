-- HDN-0: retain deactivated attendance definitions for historical reporting

UPDATE attendance_definitions
SET is_active = FALSE,
    deleted_at = NULL
WHERE deleted_at IS NOT NULL;
