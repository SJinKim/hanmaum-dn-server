-- HDN-113: Offering data for the 양육 course list and detail page
--
-- The catalog row today carries only what the admin grid needs (code, name, category,
-- sort_order). The redesigned 양육 screens show the course as something a member can
-- actually sign up for: when it starts, how long it runs, when it meets, where, who
-- leads it, how many seats are left, and until when one can apply.
--
-- These columns describe the *current* run of a course. Courses that ran in numbered
-- intakes historically keep using training_cohort for their archive; this migration
-- deliberately does not touch it. A course that starts running two concurrent intakes
-- will need the offering moved onto its own table — see the note in TrainingService.
--
-- Every column is nullable: the catalog holds twelve courses today and none of them has
-- this data yet. open_for_registration is the one exception and defaults to FALSE, so no
-- course silently becomes bookable the moment this migration runs.

ALTER TABLE training
    ADD COLUMN start_date            DATE,
    ADD COLUMN duration_weeks        INT,
    ADD COLUMN open_for_registration BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN weekday               VARCHAR(20),
    ADD COLUMN start_time            TIME,
    ADD COLUMN duration_minutes      INT,
    ADD COLUMN location              VARCHAR(200),
    -- Display name of the person leading the course, as ministry_contacts stores its
    -- names: plain text, no member FK. The leader is often a guest teacher who has no
    -- member row, and the field is shown to every member anyway.
    ADD COLUMN leader_name           VARCHAR(150),
    ADD COLUMN capacity              INT,
    ADD COLUMN registration_deadline DATE;

-- java.time.DayOfWeek constants; matches attendance_definitions.day_of_week.
ALTER TABLE training
    ADD CONSTRAINT ck_training_weekday
        CHECK (weekday IS NULL OR weekday IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
        ));

ALTER TABLE training
    ADD CONSTRAINT ck_training_duration_weeks
        CHECK (duration_weeks IS NULL OR duration_weeks > 0);

ALTER TABLE training
    ADD CONSTRAINT ck_training_duration_minutes
        CHECK (duration_minutes IS NULL OR duration_minutes > 0);

ALTER TABLE training
    ADD CONSTRAINT ck_training_capacity
        CHECK (capacity IS NULL OR capacity > 0);

-- Applying after the course has already started is not a state the UI can render:
-- the detail page shows the deadline as the cut-off before the start date.
ALTER TABLE training
    ADD CONSTRAINT ck_training_registration_deadline
        CHECK (
            registration_deadline IS NULL
            OR start_date IS NULL
            OR registration_deadline <= start_date
        );

-- The 양육 list asks for the courses a member can sign up for right now. Without data
-- this index costs nothing; it keeps the list query honest once courses open.
CREATE INDEX idx_training_open_for_registration
    ON training (open_for_registration)
    WHERE open_for_registration;
