-- HDN-0: Add separate house number to member addresses
--
-- Keep members.street as the street name and store the house/building number in
-- members.house_number so clients can render and edit both address parts independently.
-- Nullable for existing members and partial addresses.

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS house_number VARCHAR(50);
