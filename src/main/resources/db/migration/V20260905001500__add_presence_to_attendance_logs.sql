-- HDN-142: Record whether a check-in happened at the church
--
-- POST /api/v1/attendance/check-in gains an optional device position. The server compares
-- it against the configured geofence and stores what it could establish. The check-in
-- itself stays gated by the time window alone — the position is evidence attached to the
-- record, never a gate in front of it.
--
-- Three values, not a boolean. The church building has no WiFi, so an indoor fix falls
-- back to GPS through walls or to cell towers, whose accuracy runs wider than the radius
-- itself. Folding "declined to share" and "the fix was too vague" into OUTSIDE would put a
-- claim in the record the data does not support: an admin would read "was not there" where
-- the truth is "we do not know".
--
-- Existing rows become UNCONFIRMED, which is accurate — their position was never measured.
-- The DEFAULT carries that for the backfill and for any writer that does not yet set the
-- column, so the NOT NULL is safe to add in one step.

ALTER TABLE attendance_logs
    ADD COLUMN presence VARCHAR(16) NOT NULL DEFAULT 'UNCONFIRMED';

ALTER TABLE attendance_logs
    ADD CONSTRAINT ck_attendance_log_presence
        CHECK (presence IN ('IN_PLACE', 'OUTSIDE', 'UNCONFIRMED'));

COMMENT ON COLUMN attendance_logs.presence IS
    'What the server could establish about the member''s position at check-in: IN_PLACE, OUTSIDE, or UNCONFIRMED. Evidence, not a gate.';
