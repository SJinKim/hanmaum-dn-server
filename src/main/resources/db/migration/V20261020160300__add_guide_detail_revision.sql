-- HDN-331: explicit parent dirtiness independent of auditing timestamp precision.
ALTER TABLE guide_worship_reports ADD COLUMN detail_revision BIGINT NOT NULL DEFAULT 0;
