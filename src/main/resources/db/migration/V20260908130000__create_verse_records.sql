-- HDN-151: Per-member streaks for the two Home verse cards
--
-- One row per member, day and kind. Insert-only: there is no delete path in the API, so
-- "no taking it back" is enforced by the operation not existing rather than by trusting the
-- client. The unique constraint is the concurrency-safe duplicate guard, the same pattern
-- attendance_logs already uses — a second POST on the same day conflicts in the database,
-- not in a read-then-write race.
--
-- The date is stamped by the server, never sent by the client, so the "today only" rule
-- cannot be defeated by changing a device clock.
--
-- No index beyond the constraint: every query is "one member, one kind", and the unique
-- index leads with member_id, so it already serves them.

CREATE TABLE verse_records (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID        NOT NULL UNIQUE,
    member_id   BIGINT      NOT NULL REFERENCES members (id),
    record_date DATE        NOT NULL,
    kind        VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_verse_record UNIQUE (member_id, record_date, kind),
    CONSTRAINT ck_verse_record_kind CHECK (kind IN ('QUIET_TIME', 'RECITATION'))
);

COMMENT ON TABLE verse_records IS
    'Insert-only marks for the Home verse cards. One row per member, day and kind; the server stamps the date.';
