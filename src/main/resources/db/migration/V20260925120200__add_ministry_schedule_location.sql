-- HDN-216: Add an optional location to each ministry schedule.
ALTER TABLE ministry_schedules ADD COLUMN location VARCHAR(100);
