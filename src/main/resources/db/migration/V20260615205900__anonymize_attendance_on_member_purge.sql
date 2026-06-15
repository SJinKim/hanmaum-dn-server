-- HDN-0: anonymize attendance records instead of deleting historical group counts

ALTER TABLE attendance_logs
    DROP CONSTRAINT attendance_logs_member_id_fkey;

ALTER TABLE attendance_logs
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE attendance_logs
    ADD CONSTRAINT attendance_logs_member_id_fkey
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE SET NULL;
