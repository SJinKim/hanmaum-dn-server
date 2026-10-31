-- HDN-379: manual return and follow-up history of long-term absentees (장기결석자).
--
-- Expand step. A follow-up row becomes a stable absence phase that is never reopened:
--   - absence_anchor_date is the date the phase counts from, the later of the last qualifying
--     check-in and a manual return; existing rows start from last_sunday_date.
--   - phase_generation lets a new phase reuse the same last_sunday_date after a manual return or
--     a log correction; existing rows are generation 0.
--   - end_reason / end_recorded_at / returned_by describe how the phase ended. Phases closed by
--     HDN-375 ended by a check-in; when that was recorded and by whom is unknown, so both stay null.
-- members gets the manual return date of the running absence and a monotonic conflict counter
-- that is the wire `version` of every long-absence write. It starts above every token the old
-- code could have handed out (follow-up version + 1) and is never reset.
-- attendance_absence_changes keeps the contact and memo versions per phase. The legacy snapshot
-- of current values is written by the Kotlin migration that follows, because memo and actor have
-- to be re-encrypted under their new column context.
--
-- returned_by, actor_subject and the memo versions are encrypted by the application. Nothing is
-- deleted here; retention (memo 90 days, phase 10 years after closed_on, decision #380) is
-- applied by the daily cleanup.

SET LOCAL lock_timeout = '5s';

ALTER TABLE members
    ADD COLUMN absence_manual_return_on DATE,
    ADD COLUMN absence_revision         BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_members_absence_revision CHECK (absence_revision >= 0);

ALTER TABLE attendance_absence_followups
    ADD COLUMN absence_anchor_date DATE,
    ADD COLUMN phase_generation    BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN end_reason          VARCHAR(32),
    ADD COLUMN end_recorded_at     TIMESTAMPTZ,
    ADD COLUMN returned_by         TEXT;

UPDATE attendance_absence_followups
SET absence_anchor_date = last_sunday_date,
    end_reason          = CASE WHEN closed_on IS NOT NULL THEN 'CHECK_IN' END;

UPDATE members m
SET absence_revision = s.revision
FROM (SELECT member_id, MAX(version) + 1 AS revision
      FROM attendance_absence_followups
      GROUP BY member_id) s
WHERE m.id = s.member_id;

ALTER TABLE attendance_absence_followups
    ALTER COLUMN absence_anchor_date SET NOT NULL,
    DROP CONSTRAINT uq_attendance_absence_followup_phase,
    DROP CONSTRAINT ck_attendance_absence_followup_closed,
    ADD CONSTRAINT uq_attendance_absence_followup_phase_generation
        UNIQUE (member_id, last_sunday_date, phase_generation),
    ADD CONSTRAINT ck_attendance_absence_followup_generation CHECK (phase_generation >= 0),
    ADD CONSTRAINT ck_attendance_absence_followup_anchor CHECK (absence_anchor_date >= last_sunday_date),
    ADD CONSTRAINT ck_attendance_absence_followup_end CHECK (
        (closed_on IS NULL AND end_reason IS NULL AND end_recorded_at IS NULL AND returned_by IS NULL)
            OR (closed_on > absence_anchor_date AND end_reason = 'CHECK_IN' AND returned_by IS NULL)
            OR (closed_on > absence_anchor_date AND end_reason = 'MANUAL_RETURN'
                AND returned_by IS NOT NULL AND end_recorded_at IS NOT NULL)
        );

-- The closed-phase history pages, globally and per member. The first replaces the single-column
-- retention index; both retention steps filter on closed_on through it.
DROP INDEX idx_attendance_absence_followups_closed_on;

CREATE INDEX idx_attendance_absence_followups_history
    ON attendance_absence_followups (closed_on DESC, public_id DESC)
    WHERE closed_on IS NOT NULL;

CREATE INDEX idx_attendance_absence_followups_member_history
    ON attendance_absence_followups (member_id, closed_on DESC, public_id DESC)
    WHERE closed_on IS NOT NULL;

CREATE TABLE attendance_absence_changes
(
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID        NOT NULL,
    followup_id   BIGINT      NOT NULL REFERENCES attendance_absence_followups (id) ON DELETE CASCADE,
    revision      BIGINT      NOT NULL,
    kind          VARCHAR(32) NOT NULL,
    origin        VARCHAR(32) NOT NULL,
    occurred_at   TIMESTAMPTZ,
    actor_subject TEXT,
    contacted     BOOLEAN,
    memo          TEXT,
    pii_key_id    VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_attendance_absence_changes_public_id UNIQUE (public_id),
    -- One atomic write records at most one contact and one memo entry, both with its revision.
    CONSTRAINT uq_attendance_absence_change_revision UNIQUE (followup_id, revision, kind),
    CONSTRAINT ck_attendance_absence_change_revision CHECK (revision >= 0),
    CONSTRAINT ck_attendance_absence_change_kind CHECK (
        (kind = 'CONTACT_CHANGED' AND contacted IS NOT NULL AND memo IS NULL)
            OR (kind = 'MEMO_CHANGED' AND memo IS NOT NULL AND contacted IS NULL)
        ),
    CONSTRAINT ck_attendance_absence_change_origin CHECK (
        (origin = 'ACTION' AND occurred_at IS NOT NULL AND actor_subject IS NOT NULL)
            OR origin = 'LEGACY_SNAPSHOT'
        )
);

COMMENT ON TABLE attendance_absence_followups IS
    'One long-absence phase per row with contact status and encrypted memo (HDN-375, HDN-379). Never reopened. Memo and memo versions are hard-deleted 90 days after closed_on, the phase 10 years after.';

COMMENT ON TABLE attendance_absence_changes IS
    'Contact and memo versions of a long-absence phase (HDN-379). Actor and memo encrypted. Clearing the memo deletes every memo version of the phase.';
