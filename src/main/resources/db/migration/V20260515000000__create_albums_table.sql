-- HDN-0: Create albums table for the multi-album pCloud feature.
-- Albums are managed directly in DB; no admin API in v1.
-- pcloud_code is unique — one album per pCloud public share.

CREATE TABLE albums (
    id             BIGSERIAL    PRIMARY KEY,
    public_id      UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name           VARCHAR(200) NOT NULL,
    pcloud_code    VARCHAR(100) NOT NULL UNIQUE,
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ
);

CREATE INDEX idx_albums_display_order ON albums(display_order) WHERE deleted_at IS NULL;
