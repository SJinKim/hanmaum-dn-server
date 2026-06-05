-- HDN-0: repurpose ministry_registrations into date-based assignments
-- Replaces the year-based registration model (registration_period + registration_status)
-- with start_date / end_date. end_date IS NULL means the assignment is currently active.
-- Small, pre-MVP table; runtime negligible.

ALTER TABLE ministry_registrations ADD COLUMN start_date DATE;
ALTER TABLE ministry_registrations ADD COLUMN end_date   DATE;

-- Backfill before NOT NULL: treat the legacy registration year as a Jan-1 start, still ongoing.
UPDATE ministry_registrations
SET start_date = (registration_period || '-01-01')::date
WHERE start_date IS NULL AND registration_period IS NOT NULL;

-- Defensive fallback for any row without a usable period (expected 0 rows).
UPDATE ministry_registrations
SET start_date = CURRENT_DATE
WHERE start_date IS NULL;

ALTER TABLE ministry_registrations ALTER COLUMN start_date SET NOT NULL;

-- The year-based uniqueness no longer applies; dropping registration_period also
-- cascades any unique constraint that depends on it.
ALTER TABLE ministry_registrations DROP CONSTRAINT IF EXISTS uq_ministry_member_period;
ALTER TABLE ministry_registrations DROP COLUMN registration_period;
ALTER TABLE ministry_registrations DROP COLUMN registration_status;
