-- HDN-167: Alternative names a 양육 course goes by in the external application system
--
-- application.hanmaum.de names its courses freely: "큐베세 직장인/청년 반" is the
-- Quiet Time Basic Seminar, "제5기 청년부 파워제자반" is YOUTH_POWER_DISCIPLESHIP. name_ko
-- alone cannot find them — an abbreviation has no word boundaries to derive it from — so
-- each course gets the extra names it is published under.
--
-- Stored as written. The matcher strips whitespace and punctuation from both sides, so
-- "청년부 파워제자반" and "청년부파워제자반" are the same alias.
--
-- ON DELETE CASCADE, like training_target_audience: an alias carries no member data and
-- means nothing without its course.

CREATE TABLE training_alias
(
    training_id BIGINT       NOT NULL REFERENCES training (id) ON DELETE CASCADE,
    alias       VARCHAR(100) NOT NULL,
    PRIMARY KEY (training_id, alias)
);

COMMENT ON TABLE training_alias IS
    'Extra names a training is published under in application.hanmaum.de, used to match external courses to trainings.';
