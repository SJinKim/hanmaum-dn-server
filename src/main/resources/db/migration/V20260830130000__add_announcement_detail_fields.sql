-- HDN-112: add announcement image, location, and view count

ALTER TABLE announcements
    ADD COLUMN image_url VARCHAR(2048),
    ADD COLUMN location VARCHAR(255),
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
