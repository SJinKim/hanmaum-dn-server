-- HDN-218: Add an optional weekday to each ministry schedule.
-- java.time.DayOfWeek constants; matches training.weekday and attendance_definitions.day_of_week.
ALTER TABLE ministry_schedules ADD COLUMN day_of_week VARCHAR(20);

ALTER TABLE ministry_schedules
    ADD CONSTRAINT ck_ministry_schedules_day_of_week
        CHECK (day_of_week IS NULL OR day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
        ));
