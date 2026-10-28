-- HDN-140: bucket objects that may have lost their member row reference (failed commit after an
-- upload, failed delete after a replace or purge). A sweeper deletes each key that no member
-- references any more and then removes its row, so a storage outage never leaves an orphan behind.
CREATE TABLE IF NOT EXISTS profile_image_cleanup (
    id         BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
