-- HDN-YY: Cohorts (기수) for courses that run in numbered intakes
--
-- Only YOUTH_POWER_DISCIPLESHIP uses cohorts today (training.has_cohorts).
--
-- The unique key is (training_id, series, ordinal), deliberately NOT the label:
-- the same real cohort is written '파워3기' in the historical matrix sheet and
-- '청년파워제자반 3기 (2020)' in the detailed sheet. Keying on the label would create
-- two cohort rows for one intake and split its members across both.
--
-- series exists because two numberings run in parallel: the pre-파워 제자반 series and
-- the 파워 series both start at 1기, so ordinal alone is not unique.

CREATE TABLE training_cohort (
    id          BIGSERIAL    PRIMARY KEY,
    public_id   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    training_id BIGINT       NOT NULL REFERENCES training (id) ON DELETE RESTRICT,
    series      VARCHAR(20)  NOT NULL DEFAULT 'POWER',
    ordinal     INT          NOT NULL,
    -- Raw label as written in the source sheet; display only, never a key.
    label       VARCHAR(100),
    cohort_year INT,
    term        VARCHAR(20),
    started_on  DATE,
    ended_on    DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT ck_training_cohort_series
        CHECK (series IN ('LEGACY', 'POWER')),
    CONSTRAINT ck_training_cohort_term
        CHECK (term IS NULL OR term IN ('FIRST_HALF', 'SECOND_HALF')),
    CONSTRAINT ck_training_cohort_year
        CHECK (cohort_year IS NULL OR cohort_year BETWEEN 1970 AND 2100),
    CONSTRAINT ck_training_cohort_ordinal
        CHECK (ordinal > 0),
    CONSTRAINT ck_training_cohort_period
        CHECK (started_on IS NULL OR ended_on IS NULL OR ended_on >= started_on)
);

CREATE UNIQUE INDEX uq_training_cohort_ordinal
    ON training_cohort (training_id, series, ordinal)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_training_cohort_training
    ON training_cohort (training_id);
