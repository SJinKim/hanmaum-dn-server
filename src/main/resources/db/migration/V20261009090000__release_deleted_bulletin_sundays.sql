-- HDN-295: retain deleted drafts for audit while freeing their Sunday.
-- VOL uniqueness remains unchanged, including historical rows.
ALTER TABLE bulletin_edition DROP CONSTRAINT uq_bulletin_edition_service_date;

CREATE UNIQUE INDEX uq_bulletin_edition_service_date
    ON bulletin_edition (service_date)
    WHERE deleted_at IS NULL;
