-- HDN-313: mark the 새가족 순 explicitly instead of matching its name.
ALTER TABLE church_groups ADD COLUMN is_newcomer_group BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one live 새가족 순; check-in counts and auto-assignment rely on it.
CREATE UNIQUE INDEX uq_church_groups_newcomer_group
    ON church_groups (is_newcomer_group)
    WHERE is_newcomer_group AND deleted_at IS NULL;

UPDATE church_groups
SET is_newcomer_group = TRUE
WHERE id = (
    SELECT MIN(id) FROM church_groups
    WHERE name LIKE '%새가족%' AND deleted_at IS NULL
);

-- group-counts no longer reports a "no 순" row. Check-ins made without a 순 were
-- 새가족 who joined through the app; file them under the 새가족 순.
UPDATE attendance_logs
SET group_id_at_check_in = (SELECT id FROM church_groups WHERE is_newcomer_group AND deleted_at IS NULL)
WHERE group_id_at_check_in IS NULL
  AND EXISTS (SELECT 1 FROM church_groups WHERE is_newcomer_group AND deleted_at IS NULL);
