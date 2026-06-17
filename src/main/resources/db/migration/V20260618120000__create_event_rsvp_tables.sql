-- HDN-0: event RSVP tables

CREATE TABLE event_rsvps (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    announcement_id BIGINT      REFERENCES announcements(id) ON DELETE SET NULL,
    title           VARCHAR(100) NOT NULL,
    window_start    TIMESTAMPTZ NOT NULL,
    window_end      TIMESTAMPTZ NOT NULL,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE event_rsvp_logs (
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    event_rsvp_id    BIGINT      NOT NULL REFERENCES event_rsvps(id) ON DELETE RESTRICT,
    member_id        BIGINT      NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    group_id_at_rsvp BIGINT      REFERENCES church_groups(id) ON DELETE RESTRICT,
    checked_in_at    TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT uq_event_rsvp_log UNIQUE (event_rsvp_id, member_id)
);

CREATE INDEX idx_event_rsvp_logs_rsvp_member ON event_rsvp_logs (event_rsvp_id, member_id);
CREATE INDEX idx_event_rsvp_logs_attendees   ON event_rsvp_logs (event_rsvp_id, checked_in_at);
CREATE INDEX idx_event_rsvps_window
    ON event_rsvps (window_start, window_end, is_active)
    WHERE deleted_at IS NULL;
