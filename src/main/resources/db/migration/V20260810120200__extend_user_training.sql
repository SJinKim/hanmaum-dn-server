-- HDN-YY: Extend user_training for the 양육 마스터 import
--
-- Adds the fields the workbook actually carries: application/start dates, cohort,
-- mentor (양육자), an OT/NT variant, and the record's origin.
--
-- Naming note: completed_at keeps its name even though the new date columns use _on.
-- Renaming it would rename UserTrainingDto.completedAt, which is a breaking change for
-- the web dashboard and the mobile app — a cosmetic gain for a real cost. The mixture
-- is deliberate: completed_at is a DATE despite the _at suffix.

ALTER TABLE user_training
    ADD COLUMN cohort_id        BIGINT,
    ADD COLUMN applied_on       DATE,
    ADD COLUMN started_on       DATE,
    ADD COLUMN variant          VARCHAR(30),
    ADD COLUMN mentor_member_id BIGINT,
    -- Mentor name exactly as written in the source, including honorifics
    -- (집사 / 자매 / 형제). Kept when the mentor cannot be resolved to a member.
    ADD COLUMN mentor_name_raw  TEXT,
    ADD COLUMN note             TEXT;

ALTER TABLE user_training
    ADD CONSTRAINT fk_user_training_cohort
        FOREIGN KEY (cohort_id) REFERENCES training_cohort (id)
        ON DELETE RESTRICT;

-- SET NULL, not CASCADE: purging a mentor must not delete other members' records.
ALTER TABLE user_training
    ADD CONSTRAINT fk_user_training_mentor
        FOREIGN KEY (mentor_member_id) REFERENCES members (id)
        ON DELETE SET NULL;

ALTER TABLE user_training
    ADD CONSTRAINT ck_user_training_variant
        CHECK (variant IS NULL OR variant IN ('OLD_TESTAMENT', 'NEW_TESTAMENT'));

ALTER TABLE user_training
    ADD CONSTRAINT ck_user_training_no_self_mentor
        CHECK (mentor_member_id IS NULL OR mentor_member_id <> user_id);

-- Existing rows are IN_PROGRESS or COMPLETED, both still valid. UNKNOWN covers historical
-- participation an admin records without knowing any dates.
ALTER TABLE user_training
    ADD CONSTRAINT ck_user_training_status
        CHECK (status IN (
            'APPLIED', 'ENROLLED', 'IN_PROGRESS', 'COMPLETED', 'DROPPED', 'UNKNOWN'
        ));

-- Every date is optional; only their relative order is constrained. A hand-entered
-- record with contradictory dates is rejected here rather than stored inconsistently.
ALTER TABLE user_training
    ADD CONSTRAINT ck_user_training_timeline
        CHECK (
            (applied_on IS NULL OR started_on   IS NULL OR started_on   >= applied_on)
            AND (started_on IS NULL OR completed_at IS NULL OR completed_at >= started_on)
            AND (applied_on IS NULL OR completed_at IS NULL OR completed_at >= applied_on)
        );

-- 3.2: replace the plain unique constraint with a variant-aware partial index.
-- 성경개관 is taken twice per person (구약 and 신약), which the old constraint blocked.
-- The COALESCE keeps NULL variants from bypassing uniqueness the way NULLs normally do.
ALTER TABLE user_training
    DROP CONSTRAINT uq_user_training_member_training;

CREATE UNIQUE INDEX uq_user_training_member_training_variant
    ON user_training (user_id, training_id, COALESCE(variant, ''))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_user_training_cohort
    ON user_training (cohort_id)
    WHERE cohort_id IS NOT NULL;

CREATE INDEX idx_user_training_mentor
    ON user_training (mentor_member_id)
    WHERE mentor_member_id IS NOT NULL;

CREATE INDEX idx_user_training_completed
    ON user_training (completed_at)
    WHERE completed_at IS NOT NULL;
