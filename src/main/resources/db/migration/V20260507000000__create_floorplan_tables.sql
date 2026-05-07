-- HDN-0: Create floor and room tables for the church floor plan feature
--
-- floors are admin-managed at the DB level (no admin API in v1).
-- points stores normalized polygon vertices as [[x1,y1],[x2,y2],...] where
-- each coordinate is 0.0–1.0 relative to the canvas dimensions.

CREATE TABLE floor (
    id           BIGSERIAL    PRIMARY KEY,
    public_id    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    floor_number INT          NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE room (
    id          BIGSERIAL    PRIMARY KEY,
    public_id   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    floor_id    BIGINT       NOT NULL REFERENCES floor(id),
    name        VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    points      JSONB        NOT NULL DEFAULT '[]',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_room_floor_id ON room(floor_id);
