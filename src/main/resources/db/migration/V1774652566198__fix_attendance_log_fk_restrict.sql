-- Fix: change attendance_logs.definition_id FK from CASCADE to RESTRICT
-- Project rule: soft-delete only — hard-deletes must be prevented, not silently cascaded.

ALTER TABLE attendance_logs
    DROP CONSTRAINT IF EXISTS attendance_logs_definition_id_fkey;

ALTER TABLE attendance_logs
    ADD CONSTRAINT attendance_logs_definition_id_fkey
        FOREIGN KEY (definition_id) REFERENCES attendance_definitions(id) ON DELETE RESTRICT;
