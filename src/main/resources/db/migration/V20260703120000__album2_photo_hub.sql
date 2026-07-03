-- Album 2.0: pCloud retired (no data migration, hub starts empty — spec Q1),
-- albums become one-per-church-group, photos + saves become first-class.

DELETE FROM albums;
ALTER TABLE albums DROP COLUMN pcloud_code;
ALTER TABLE albums ADD COLUMN group_id BIGINT UNIQUE REFERENCES church_groups (id);
ALTER TABLE albums ADD COLUMN cover_photo_id BIGINT;

CREATE TABLE album_photos (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    album_id BIGINT NOT NULL REFERENCES albums (id),
    original_key TEXT NOT NULL,
    thumb_key TEXT,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    taken_at TIMESTAMPTZ,
    uploaded_by_member_id BIGINT NOT NULL REFERENCES members (id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_album_photos_album_status ON album_photos (album_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_album_photos_taken_at ON album_photos (taken_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_album_photos_pending_created ON album_photos (created_at) WHERE status = 'PENDING';

ALTER TABLE albums
    ADD CONSTRAINT fk_albums_cover_photo FOREIGN KEY (cover_photo_id) REFERENCES album_photos (id);

CREATE TABLE photo_saves (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES members (id),
    photo_id BIGINT NOT NULL REFERENCES album_photos (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_photo_saves_member_photo UNIQUE (member_id, photo_id)
);
