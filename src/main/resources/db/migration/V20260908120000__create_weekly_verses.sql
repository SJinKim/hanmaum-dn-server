-- HDN-115: Admin-chosen weekly memory verse (주간 암송 구절)
--
-- The congregation's bible API has no weekly-verse endpoint: every candidate path answers
-- 404 and app-config carries no such field. verse.php renders coordinates and nothing more,
-- so the *selection* has to be kept here. The text is still fetched upstream from these
-- coordinates; only the choice is ours.
--
-- One row per week, keyed by a Sunday week_start to match 주일 as the start of the week
-- everywhere else in this schema. No seed data: an empty table is a valid state and simply
-- means no verse has been chosen yet, which the API reports as an empty card.

CREATE TABLE weekly_verses (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID        NOT NULL UNIQUE,
    week_start     DATE        NOT NULL,
    book           INTEGER     NOT NULL,
    chapter_start  INTEGER     NOT NULL,
    verse_start    INTEGER     NOT NULL,
    chapter_end    INTEGER     NOT NULL,
    verse_end      INTEGER     NOT NULL,
    translation_id INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT uq_weekly_verse_week UNIQUE (week_start),
    CONSTRAINT ck_weekly_verse_range CHECK (
        book BETWEEN 1 AND 66
        AND chapter_start >= 1 AND verse_start >= 1
        AND chapter_end >= chapter_start
        AND (chapter_end > chapter_start OR verse_end >= verse_start)
    )
);

COMMENT ON TABLE weekly_verses IS
    'Admin-chosen 주간 암송 구절 per week. week_start is a Sunday. Text is rendered upstream from these coordinates.';
