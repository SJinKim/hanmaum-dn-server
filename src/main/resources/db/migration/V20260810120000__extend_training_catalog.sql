-- HDN-YY: Extend the training catalog for the 양육 마스터 import
--
-- The catalog currently holds three rows (QTBS, 1on1, Discipleship) identified only
-- by their display name. The import needs a stable, language-independent key (code),
-- a Korean display name, a category, and prerequisite links between courses.
--
-- code is added nullable, backfilled for the three existing rows, and only then set
-- NOT NULL — never NOT NULL without a default in a single step.
--
-- Identifiers are English throughout; Korean appears only as data in name_ko.

ALTER TABLE training
    ADD COLUMN code                     VARCHAR(50),
    ADD COLUMN name_ko                  VARCHAR(100),
    ADD COLUMN category                 VARCHAR(20),
    ADD COLUMN has_cohorts              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN prerequisite_training_id BIGINT,
    ADD COLUMN is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN description              TEXT;

-- Lift the three pre-existing rows onto their codes. Matched on name because that is
-- the only stable identifier they have today (uq_training_name guarantees uniqueness).
UPDATE training SET code = 'QT_BASIC_SEMINAR'         WHERE name = 'QTBS'         AND code IS NULL;
UPDATE training SET code = 'ONE_ON_ONE'               WHERE name = '1on1'         AND code IS NULL;
UPDATE training SET code = 'YOUTH_POWER_DISCIPLESHIP' WHERE name = 'Discipleship' AND code IS NULL;

-- Any row that still lacks a code means the catalog held something this migration did
-- not anticipate. Fail loudly rather than silently violating NOT NULL below.
DO $$
DECLARE
    uncoded_count INT;
BEGIN
    SELECT count(*) INTO uncoded_count FROM training WHERE code IS NULL;
    IF uncoded_count > 0 THEN
        RAISE EXCEPTION
            'Cannot set training.code NOT NULL: % row(s) have no code mapping. '
            'Add an UPDATE for them before re-running.', uncoded_count;
    END IF;
END $$;

ALTER TABLE training
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE training
    ADD CONSTRAINT uq_training_code UNIQUE (code);

ALTER TABLE training
    ADD CONSTRAINT fk_training_prerequisite
        FOREIGN KEY (prerequisite_training_id) REFERENCES training (id)
        ON DELETE RESTRICT;

ALTER TABLE training
    ADD CONSTRAINT ck_training_category
        CHECK (category IS NULL OR category IN (
            'FOUNDATION', 'DISCIPLESHIP', 'BIBLE', 'MINISTRY', 'MISSION'
        ));

-- A course cannot require itself. Longer cycles are not preventable by a CHECK; the
-- catalog is admin-managed and tiny, so they are caught in review instead.
ALTER TABLE training
    ADD CONSTRAINT ck_training_no_self_prerequisite
        CHECK (prerequisite_training_id IS NULL OR prerequisite_training_id <> id);

CREATE INDEX idx_training_prerequisite
    ON training (prerequisite_training_id)
    WHERE prerequisite_training_id IS NOT NULL;

CREATE INDEX idx_training_category
    ON training (category);
