CREATE TABLE device_tokens (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES members (id),
    token VARCHAR(512) NOT NULL UNIQUE,
    platform VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_device_tokens_member ON device_tokens (member_id);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES members (id),
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    reference_type VARCHAR(32),
    reference_public_id UUID,
    seen_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_notifications_member_created ON notifications (member_id, created_at DESC);
CREATE INDEX idx_notifications_member_unseen ON notifications (member_id) WHERE seen_at IS NULL;

ALTER TABLE members ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
