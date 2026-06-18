-- =================================================================
-- V20260616120000: Dev seed — attendance test data
--
-- IMPORTANT: This file lives in classpath:db/seed, which is only
-- included in application-dev.yml. It MUST NOT run in production.
--
-- Provides:
--   • 3 test church groups (다니엘/사랑, 다니엘/은혜, 느헤미야/온유)
--   • Existing unassigned ACTIVE members round-robin assigned across them,
--     keycloak-linked members first so each lands in a different group —
--     useful for testing POST /attendance/check-in with a real JWT
--   • 15 explicit seed members (씨드 일–십오), 5 per test group,
--     so attendance history is deterministic on a fresh DB
--   • 50 historical attendance log entries spread evenly across the 5
--     pre-existing definitions (10 per def, 2 past dates × 5 members);
--     each group has ≥ 4 entries. No new definitions are created.
-- =================================================================

-- ── Test church groups ───────────────────────────────────────────────────────
-- Advance the sequence in case it drifted behind the actual max id
-- (can happen when rows were inserted with explicit ids)
SELECT setval('church_groups_id_seq', GREATEST((SELECT MAX(id) FROM church_groups), (SELECT last_value FROM church_groups_id_seq)));

INSERT INTO church_groups (division, name)
VALUES
    ('다니엘', '사랑'),
    ('다니엘', '은혜'),
    ('느헤미야', '온유')
ON CONFLICT (division, name) DO NOTHING;

-- ── Assign unassigned ACTIVE members to the test groups ──────────────────────
WITH candidates AS (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY (keycloak_id IS NOT NULL) DESC, id) AS rn
    FROM members
    WHERE deleted_at IS NULL
      AND group_id IS NULL
      AND member_status = 'ACTIVE'
),
groups AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY name) AS grp_rn
    FROM church_groups
    WHERE (division, name) IN (('다니엘', '사랑'), ('다니엘', '은혜'), ('느헤미야', '온유'))
)
UPDATE members m
SET group_id = g.id
FROM candidates c
JOIN groups g ON g.grp_rn = ((c.rn - 1) % 3) + 1
WHERE m.id = c.id;

-- ── 15 explicit seed members (씨드 일–십오), 5 per test group ────────────────
--    last_name='씨드', first_name=Korean ordinal; idempotent via WHERE NOT EXISTS
INSERT INTO members (last_name, first_name, member_status, group_id)
SELECT v.last_name, v.first_name, 'ACTIVE', g.id
FROM (VALUES
    ('씨드', '일',   1),
    ('씨드', '이',   1),
    ('씨드', '삼',   1),
    ('씨드', '사',   1),
    ('씨드', '오',   1),
    ('씨드', '육',   2),
    ('씨드', '칠',   2),
    ('씨드', '팔',   2),
    ('씨드', '구',   2),
    ('씨드', '십',   2),
    ('씨드', '십일', 3),
    ('씨드', '십이', 3),
    ('씨드', '십삼', 3),
    ('씨드', '십사', 3),
    ('씨드', '십오', 3)
) AS v(last_name, first_name, grp_n)
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY name) AS grp_n
    FROM church_groups
    WHERE (division, name) IN (('다니엘', '사랑'), ('다니엘', '은혜'), ('느헤미야', '온유'))
) g ON g.grp_n = v.grp_n
WHERE NOT EXISTS (
    SELECT 1 FROM members ex
    WHERE ex.last_name = v.last_name
      AND ex.first_name = v.first_name
      AND ex.deleted_at IS NULL
);

-- ── 50 historical attendance logs across the 5 existing definitions ───────────
--
--  10 entries per definition, spread over 2 past dates that match each
--  definition's day_of_week (seeded relative to 2026-06-16, Tuesday):
--
--    주일 예배   (SUNDAY)    → 2026-06-14, 2026-06-07
--    수요 예배   (WEDNESDAY) → 2026-06-10, 2026-06-03
--    새벽 기도   (MONDAY)    → 2026-06-15, 2026-06-08
--    금요 예배   (FRIDAY)    → 2026-06-12, 2026-06-05
--    화요 기도모임 (TUESDAY)  → 2026-06-09, 2026-06-02
--
--  date-1 members (5): 씨드-일(사랑), 씨드-육(은혜), 씨드-십일(온유), 씨드-이(사랑), 씨드-칠(은혜)
--  date-2 members (5): 씨드-십이(온유), 씨드-삼(사랑), 씨드-팔(은혜), 씨드-십삼(온유), 씨드-사(사랑)
--  Group totals: 사랑 20, 은혜 15, 온유 15 — all well above the ≥4 requirement.
--
INSERT INTO attendance_logs (definition_id, member_id, attendance_date, group_id_at_check_in, attended)
SELECT d.id, m.id, v.attendance_date::date, m.group_id, TRUE
FROM (VALUES
    -- 주일 예배 (10)
    ('주일 예배', '2026-06-14', '씨드', '일'),
    ('주일 예배', '2026-06-14', '씨드', '육'),
    ('주일 예배', '2026-06-14', '씨드', '십일'),
    ('주일 예배', '2026-06-14', '씨드', '이'),
    ('주일 예배', '2026-06-14', '씨드', '칠'),
    ('주일 예배', '2026-06-07', '씨드', '십이'),
    ('주일 예배', '2026-06-07', '씨드', '삼'),
    ('주일 예배', '2026-06-07', '씨드', '팔'),
    ('주일 예배', '2026-06-07', '씨드', '십삼'),
    ('주일 예배', '2026-06-07', '씨드', '사'),
    -- 수요 예배 (10)
    ('수요 예배', '2026-06-10', '씨드', '일'),
    ('수요 예배', '2026-06-10', '씨드', '육'),
    ('수요 예배', '2026-06-10', '씨드', '십일'),
    ('수요 예배', '2026-06-10', '씨드', '이'),
    ('수요 예배', '2026-06-10', '씨드', '칠'),
    ('수요 예배', '2026-06-03', '씨드', '십이'),
    ('수요 예배', '2026-06-03', '씨드', '삼'),
    ('수요 예배', '2026-06-03', '씨드', '팔'),
    ('수요 예배', '2026-06-03', '씨드', '십삼'),
    ('수요 예배', '2026-06-03', '씨드', '사'),
    -- 새벽 기도 (10)
    ('새벽 기도', '2026-06-15', '씨드', '일'),
    ('새벽 기도', '2026-06-15', '씨드', '육'),
    ('새벽 기도', '2026-06-15', '씨드', '십일'),
    ('새벽 기도', '2026-06-15', '씨드', '이'),
    ('새벽 기도', '2026-06-15', '씨드', '칠'),
    ('새벽 기도', '2026-06-08', '씨드', '십이'),
    ('새벽 기도', '2026-06-08', '씨드', '삼'),
    ('새벽 기도', '2026-06-08', '씨드', '팔'),
    ('새벽 기도', '2026-06-08', '씨드', '십삼'),
    ('새벽 기도', '2026-06-08', '씨드', '사'),
    -- 금요 예배 (10)
    ('금요 예배', '2026-06-12', '씨드', '일'),
    ('금요 예배', '2026-06-12', '씨드', '육'),
    ('금요 예배', '2026-06-12', '씨드', '십일'),
    ('금요 예배', '2026-06-12', '씨드', '이'),
    ('금요 예배', '2026-06-12', '씨드', '칠'),
    ('금요 예배', '2026-06-05', '씨드', '십이'),
    ('금요 예배', '2026-06-05', '씨드', '삼'),
    ('금요 예배', '2026-06-05', '씨드', '팔'),
    ('금요 예배', '2026-06-05', '씨드', '십삼'),
    ('금요 예배', '2026-06-05', '씨드', '사'),
    -- 화요 기도모임 (10)
    ('화요 기도모임', '2026-06-09', '씨드', '일'),
    ('화요 기도모임', '2026-06-09', '씨드', '육'),
    ('화요 기도모임', '2026-06-09', '씨드', '십일'),
    ('화요 기도모임', '2026-06-09', '씨드', '이'),
    ('화요 기도모임', '2026-06-09', '씨드', '칠'),
    ('화요 기도모임', '2026-06-02', '씨드', '십이'),
    ('화요 기도모임', '2026-06-02', '씨드', '삼'),
    ('화요 기도모임', '2026-06-02', '씨드', '팔'),
    ('화요 기도모임', '2026-06-02', '씨드', '십삼'),
    ('화요 기도모임', '2026-06-02', '씨드', '사')
) AS v(def_title, attendance_date, m_last, m_first)
JOIN attendance_definitions d ON d.title = v.def_title
JOIN members m ON m.last_name = v.m_last AND m.first_name = v.m_first AND m.deleted_at IS NULL
ON CONFLICT ON CONSTRAINT uq_attendance_log DO NOTHING;
