-- Session 2 backend prereq: PENDING as the default member status for new registrations.
-- Existing members are untouched — they are already approved.

ALTER TABLE members
    ALTER COLUMN member_status SET DEFAULT 'PENDING';
