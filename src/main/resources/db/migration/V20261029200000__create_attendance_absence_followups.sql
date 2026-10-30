-- HDN-375: Follow-up of long-term absentees (장기결석자), one row per member and absence phase.
--
-- The absentee list itself is computed from attendance_logs; this table only stores what a
-- pastor or admin recorded about one phase: contacted yes/no (who, when) and a free-text memo.
-- A phase is keyed by the date of the member's last qualifying Sunday check-in
-- (last_sunday_date). A row exists only after the first change; a missing row reads as
-- "not contacted, no memo".
--
-- memo and contacted_by are encrypted by the application (PII converters). closed_on is the
-- first qualifying return after the anchor; the cleanup job hard-deletes a row 90 days after
-- it (decision #376). Hard delete of the member removes its rows through the cascade.

SET LOCAL lock_timeout = '5s';

CREATE TABLE attendance_absence_followups
(
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID        NOT NULL,
    member_id        BIGINT      NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    last_sunday_date DATE        NOT NULL,
    contacted        BOOLEAN     NOT NULL DEFAULT FALSE,
    contacted_by     TEXT,
    contacted_at     TIMESTAMPTZ,
    memo             TEXT,
    pii_key_id       VARCHAR(50),
    closed_on        DATE,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT uq_attendance_absence_followups_public_id UNIQUE (public_id),
    CONSTRAINT uq_attendance_absence_followup_phase UNIQUE (member_id, last_sunday_date),
    CONSTRAINT ck_attendance_absence_followup_contact CHECK (
        (contacted AND contacted_by IS NOT NULL AND contacted_at IS NOT NULL)
            OR (NOT contacted AND contacted_by IS NULL AND contacted_at IS NULL)
        ),
    CONSTRAINT ck_attendance_absence_followup_closed CHECK (closed_on IS NULL OR closed_on > last_sunday_date)
);

-- Serves the daily retention purge, which only ever looks at closed phases.
CREATE INDEX idx_attendance_absence_followups_closed_on
    ON attendance_absence_followups (closed_on)
    WHERE closed_on IS NOT NULL;

COMMENT ON TABLE attendance_absence_followups IS
    'Contact status and encrypted memo per long-absence phase (HDN-375). Closed phases are hard-deleted 90 days after closed_on.';
