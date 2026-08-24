-- HDN-YY: Seed the full 양육 training catalog (12 courses)
--
-- Mirrors the 12 non-matrix sheets of the 양육 마스터 workbook. 역대제자반 is not a
-- course of its own — it is the historical cohort matrix of YOUTH_POWER_DISCIPLESHIP.
--
-- Idempotent: ON CONFLICT (code) DO UPDATE, so re-running converges on the same state.
-- The three pre-existing rows were mapped onto their codes in the previous migration
-- and are updated in place here (QTBS -> Quiet Time Basic Seminar, and so on).
--
-- sort_order follows the progression in steps of 10, leaving room to insert courses.
-- Korean names live in name_ko as data; every identifier stays English.

INSERT INTO training (code, name, name_ko, category, has_cohorts, is_active, sort_order)
VALUES
    ('BAPTISM_MEMBERSHIP',       'Baptism & Church Membership Class',       '세례입교',        'FOUNDATION',   FALSE, TRUE,   10),
    ('QT_BASIC_SEMINAR',         'Quiet Time Basic Seminar',                '큐티베이직세미나', 'FOUNDATION',   FALSE, TRUE,   20),
    ('QT_ADVANCED_SEMINAR',      'Quiet Time Advanced Seminar',             '큐심세',          'FOUNDATION',   FALSE, TRUE,   30),
    ('ONE_ON_ONE',               'One-to-One Discipleship Training',        '일대일제자양육',   'DISCIPLESHIP', FALSE, TRUE,   40),
    ('YOUTH_POWER_DISCIPLESHIP', 'Youth Power Discipleship Class',          '청년 파워제자반',  'DISCIPLESHIP', TRUE,  TRUE,   50),
    ('ONE_ON_ONE_SCHOOL',        'One-to-One Disciple-Maker School',        '일대일양육자스쿨', 'DISCIPLESHIP', FALSE, TRUE,   60),
    ('MINISTRY_CLASS',           'Ministry Training Class',                 '사역반',          'MINISTRY',     FALSE, TRUE,   70),
    ('PROSPECTIVE_LEADER',       'Prospective Small Group Leader Training', '예비순교육',       'MINISTRY',     FALSE, TRUE,   80),
    ('BIBLE_PANORAMA',           'Bible Panorama',                          '성경파노라마',     'BIBLE',        FALSE, TRUE,   90),
    ('BIBLE_OVERVIEW',           'Bible Overview Class',                    '성경개관',         'BIBLE',        FALSE, TRUE,  100),
    -- KAIROS and KAIROS_FT are discontinued. They stay in the catalog because archived
    -- user_training rows need a valid FK target; is_active = FALSE keeps them out of
    -- selection lists.
    ('KAIROS',                   'Kairos',                                  '카이로스',         'MISSION',      FALSE, FALSE, 110),
    ('KAIROS_FT',                'Kairos Facilitator Training',             '카이로스 FT',      'MISSION',      FALSE, FALSE, 120)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    name_ko     = EXCLUDED.name_ko,
    category    = EXCLUDED.category,
    has_cohorts = EXCLUDED.has_cohorts,
    is_active   = EXCLUDED.is_active,
    sort_order  = EXCLUDED.sort_order,
    updated_at  = now();

-- Prerequisites, applied after the insert so every target row exists.
-- Both chains are evidenced by columns in the workbook: 사역반 carries a
-- '제자반 수료일' column, 일대일양육자스쿨 carries a '일대일 수료일' column.
--
-- KAIROS_FT deliberately has NO prerequisite on KAIROS — confirmed 2026-08-16.
UPDATE training
SET prerequisite_training_id = (SELECT id FROM training WHERE code = 'YOUTH_POWER_DISCIPLESHIP'),
    updated_at               = now()
WHERE code = 'MINISTRY_CLASS';

UPDATE training
SET prerequisite_training_id = (SELECT id FROM training WHERE code = 'ONE_ON_ONE'),
    updated_at               = now()
WHERE code = 'ONE_ON_ONE_SCHOOL';
