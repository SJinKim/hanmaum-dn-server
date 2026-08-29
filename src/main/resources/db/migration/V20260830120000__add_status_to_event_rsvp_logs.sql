-- HDN-121: support explicit RSVP responses and reminder tracking

ALTER TABLE event_rsvp_logs
    ADD COLUMN status           VARCHAR(16) NOT NULL DEFAULT 'GOING',
    ADD COLUMN reminder_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN last_reminded_at TIMESTAMPTZ;

ALTER TABLE event_rsvp_logs
    ADD CONSTRAINT ck_event_rsvp_log_status
    CHECK (status IN ('GOING', 'NOT_GOING', 'MAYBE'));

CREATE INDEX idx_event_rsvp_logs_reminder
    ON event_rsvp_logs (status, reminder_count)
    WHERE deleted_at IS NULL;
