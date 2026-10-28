-- HDN-140: profile pictures live in S3. The row keeps only the object key and a version
-- that bumps on every change (clients use it to bust caches); never bytes or a signed URL.
-- The legacy client-supplied profile_image_url stays untouched here and is no longer exposed.
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS profile_image_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS profile_image_version BIGINT NOT NULL DEFAULT 0;
