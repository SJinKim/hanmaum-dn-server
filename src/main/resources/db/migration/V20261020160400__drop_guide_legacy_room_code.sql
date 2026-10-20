-- HDN-331: report_area_id and immutable snapshots now own area identity.
-- Keep the published migration/backfill chain intact; remove the unused legacy
-- column only after V20261020160200 has preserved all existing detail rows.
ALTER TABLE guide_worship_details DROP COLUMN room_code;
