-- HDN-0: preserve the member's church-group assignment at attendance check-in

ALTER TABLE attendance_logs
    ADD COLUMN group_id_at_check_in BIGINT;

UPDATE attendance_logs AS attendance_log
SET group_id_at_check_in = member.group_id
FROM members AS member
WHERE attendance_log.member_id = member.id
  AND attendance_log.group_id_at_check_in IS NULL;

ALTER TABLE attendance_logs
    ADD CONSTRAINT attendance_logs_group_id_at_check_in_fkey
        FOREIGN KEY (group_id_at_check_in) REFERENCES church_groups (id) ON DELETE RESTRICT;

CREATE INDEX idx_attendance_logs_group_counts
    ON attendance_logs (definition_id, attendance_date, group_id_at_check_in)
    WHERE deleted_at IS NULL AND attended = TRUE;
