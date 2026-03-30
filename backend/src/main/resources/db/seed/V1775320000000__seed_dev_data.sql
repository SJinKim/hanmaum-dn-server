-- =================================================================
-- V1775320000000: Dev seed data
--
-- IMPORTANT: This file lives in classpath:db/seed, which is only
-- included in application-dev.yml. It MUST NOT run in production.
--
-- Provides:
--   • 17 members  (10 ACTIVE · 3 INACTIVE · 3 PENDING · 1 DELETED)
--   • 5 ministries (4 active · 1 inactive)
--   • Ministry registrations across periods "2025" and "2026"
--   • 3 attendance definitions (2 active · 1 inactive)
--   • Attendance logs for 4 Sundays + 3 Wednesdays in 2026-03
--
-- NOTE: App-level ADMIN role (ROLE_ADMIN) is granted via Keycloak,
-- not stored in this table. Grant it in the Keycloak admin console
-- for the test user you log in with.
-- =================================================================

-- ── MEMBERS ──────────────────────────────────────────────────────────────────
-- 'role' column = church title/position (직분), NOT the Keycloak access role.

INSERT INTO members (last_name, first_name, gender, baptism, member_status, role,
                     email, phone_number, city, registration_date, created_at, updated_at)
VALUES
  ('김', '민준', 'M', 'GENERAL_BAPTIZED', 'ACTIVE',   '집사', 'kim.minjun@hanmaum.dev',    '010-1111-0001', '서울', '2020-03-15', NOW(), NOW()),
  ('이', '서연', 'F', 'GENERAL_BAPTIZED', 'ACTIVE',   '권사', 'lee.seoyeon@hanmaum.dev',   '010-1111-0002', '서울', '2018-06-01', NOW(), NOW()),
  ('박', '준호', 'M', 'CONFIRMATION',     'ACTIVE',   NULL,   'park.junho@hanmaum.dev',    '010-1111-0003', '부산', '2021-09-10', NOW(), NOW()),
  ('최', '지은', 'F', 'UNBAPTIZED',       'ACTIVE',   '청년', 'choi.jieun@hanmaum.dev',    '010-1111-0004', '서울', '2023-01-20', NOW(), NOW()),
  ('정', '현우', 'M', 'GENERAL_BAPTIZED', 'ACTIVE',   '집사', 'jung.hyunwoo@hanmaum.dev',  '010-1111-0005', '인천', '2019-11-05', NOW(), NOW()),
  ('강', '민서', 'F', 'INFANT_BAPTIZED',  'ACTIVE',   NULL,   'kang.minseo@hanmaum.dev',   '010-1111-0006', '서울', '2022-04-17', NOW(), NOW()),
  ('조', '성훈', 'M', 'GENERAL_BAPTIZED', 'ACTIVE',   '장로', 'cho.sunghun@hanmaum.dev',   '010-1111-0007', '서울', '2015-02-28', NOW(), NOW()),
  ('윤', '지원', 'F', 'GENERAL_BAPTIZED', 'ACTIVE',   '집사', 'yoon.jiwon@hanmaum.dev',    '010-1111-0008', '대구', '2017-07-14', NOW(), NOW()),
  ('장', '도현', 'M', 'CONFIRMATION',     'ACTIVE',   NULL,   'jang.dohyun@hanmaum.dev',   '010-1111-0009', '서울', '2024-02-01', NOW(), NOW()),
  ('임', '수아', 'F', 'UNBAPTIZED',       'ACTIVE',   '청년', 'lim.sua@hanmaum.dev',        '010-1111-0010', '광주', '2024-06-15', NOW(), NOW()),
  ('한', '태양', 'M', 'GENERAL_BAPTIZED', 'INACTIVE', NULL,   'han.taeyang@hanmaum.dev',   '010-1111-0011', '서울', '2016-08-20', NOW(), NOW()),
  ('오', '나은', 'F', 'INFANT_BAPTIZED',  'INACTIVE', NULL,   'oh.naeun@hanmaum.dev',      '010-1111-0012', '서울', '2019-03-07', NOW(), NOW()),
  ('서', '준서', 'M', 'GENERAL_BAPTIZED', 'INACTIVE', '집사', 'seo.junseo@hanmaum.dev',    '010-1111-0013', '경기', '2018-10-22', NOW(), NOW()),
  ('권', '하은', 'F', 'UNBAPTIZED',       'PENDING',  NULL,   'kwon.haeun@hanmaum.dev',    '010-1111-0014', '서울', '2026-01-10', NOW(), NOW()),
  ('신', '지호', 'M', 'UNBAPTIZED',       'PENDING',  NULL,   'shin.jiho@hanmaum.dev',     '010-1111-0015', '서울', '2026-02-14', NOW(), NOW()),
  ('황', '유나', 'F', 'UNBAPTIZED',       'PENDING',  NULL,   'hwang.yuna@hanmaum.dev',    '010-1111-0016', '서울', '2026-03-01', NOW(), NOW()),
  ('안', '민혁', 'M', 'GENERAL_BAPTIZED', 'DELETED',  NULL,   'an.minhyuk@hanmaum.dev',    '010-1111-0017', '서울', '2017-05-30', NOW(), NOW());

-- Mark the DELETED member's deleted_at
UPDATE members
SET deleted_at = '2025-12-01 00:00:00'
WHERE email = 'an.minhyuk@hanmaum.dev';

-- ── MINISTRIES ────────────────────────────────────────────────────────────────

INSERT INTO ministries (name, short_description, long_description, image_url,
                        is_ministry_active, leader_member_id, created_at, updated_at)
VALUES
  ('청년부',
   '청년들의 신앙 성장과 공동체 생활을 지원합니다.',
   '청년부는 20-30대 청년들이 함께 모여 예배하고 교제하며 하나님의 말씀을 배우는 공동체입니다. 매주 금요일 저녁에 정기 모임이 있습니다.',
   NULL, TRUE,
   (SELECT id FROM members WHERE email = 'cho.sunghun@hanmaum.dev'),
   NOW(), NOW()),

  ('찬양팀',
   '예배를 위한 음악 사역 팀입니다.',
   '찬양팀은 주일 예배와 각종 행사에서 음악으로 하나님을 찬양합니다. 악기 연주자와 보컬 팀으로 구성되어 있습니다.',
   NULL, TRUE,
   (SELECT id FROM members WHERE email = 'kim.minjun@hanmaum.dev'),
   NOW(), NOW()),

  ('봉사팀',
   '교회 내외의 다양한 봉사 활동을 담당합니다.',
   '봉사팀은 주일 예배 안내, 주차 관리, 음식 준비 등 교회 운영에 필요한 다양한 봉사 활동을 담당합니다.',
   NULL, TRUE,
   (SELECT id FROM members WHERE email = 'yoon.jiwon@hanmaum.dev'),
   NOW(), NOW()),

  ('교육부',
   '어린이와 청소년 성경 교육을 담당합니다.',
   '교육부는 어린이부터 청소년까지 나이별로 성경을 가르치고 신앙을 양육합니다. 주일학교와 여름성경학교를 운영합니다.',
   NULL, TRUE,
   (SELECT id FROM members WHERE email = 'lee.seoyeon@hanmaum.dev'),
   NOW(), NOW()),

  ('기도팀',
   '중보기도와 새벽기도 모임을 이끕니다.',
   '기도팀은 교회와 성도들을 위해 중보기도하며, 새벽 기도회를 진행합니다.',
   NULL, FALSE,
   NULL,
   NOW(), NOW());

-- ── MINISTRY REGISTRATIONS — period "2025" ───────────────────────────────────

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '청년부'), id, '2025', NOW()
FROM members WHERE email IN (
  'park.junho@hanmaum.dev', 'choi.jieun@hanmaum.dev',
  'kang.minseo@hanmaum.dev', 'jang.dohyun@hanmaum.dev', 'lim.sua@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '찬양팀'), id, '2025', NOW()
FROM members WHERE email IN (
  'kim.minjun@hanmaum.dev', 'jung.hyunwoo@hanmaum.dev', 'park.junho@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '봉사팀'), id, '2025', NOW()
FROM members WHERE email IN (
  'yoon.jiwon@hanmaum.dev', 'kang.minseo@hanmaum.dev', 'jung.hyunwoo@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '교육부'), id, '2025', NOW()
FROM members WHERE email IN (
  'lee.seoyeon@hanmaum.dev', 'choi.jieun@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

-- ── MINISTRY REGISTRATIONS — period "2026" ───────────────────────────────────

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '청년부'), id, '2026', NOW()
FROM members WHERE email IN (
  'park.junho@hanmaum.dev', 'choi.jieun@hanmaum.dev',
  'jang.dohyun@hanmaum.dev', 'lim.sua@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '찬양팀'), id, '2026', NOW()
FROM members WHERE email IN (
  'kim.minjun@hanmaum.dev', 'jung.hyunwoo@hanmaum.dev', 'lim.sua@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '봉사팀'), id, '2026', NOW()
FROM members WHERE email IN (
  'yoon.jiwon@hanmaum.dev', 'jung.hyunwoo@hanmaum.dev', 'jang.dohyun@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

INSERT INTO ministry_registrations (ministry_id, member_id, registration_period, created_at)
SELECT (SELECT id FROM ministries WHERE name = '교육부'), id, '2026', NOW()
FROM members WHERE email IN (
  'lee.seoyeon@hanmaum.dev', 'park.junho@hanmaum.dev'
) ON CONFLICT ON CONSTRAINT uq_ministry_member_period DO NOTHING;

-- ── ATTENDANCE DEFINITIONS ────────────────────────────────────────────────────

INSERT INTO attendance_definitions (title, day_of_week, window_start, window_end, is_active, created_at, updated_at)
VALUES
  ('주일 예배', 'SUNDAY',    '10:30:00', '12:30:00', TRUE,  NOW(), NOW()),
  ('수요 예배', 'WEDNESDAY', '19:30:00', '21:00:00', TRUE,  NOW(), NOW()),
  ('새벽 기도', 'MONDAY',    '05:00:00', '06:00:00', FALSE, NOW(), NOW());

-- ── ATTENDANCE LOGS ───────────────────────────────────────────────────────────
-- 4 Sundays in March 2026 — 8 active members

INSERT INTO attendance_logs (definition_id, member_id, attendance_date, attended, created_at)
SELECT
    (SELECT id FROM attendance_definitions WHERE title = '주일 예배'),
    mem.id,
    log_date,
    TRUE,
    NOW()
FROM members mem
CROSS JOIN (
    VALUES
        ('2026-03-01'::date),
        ('2026-03-08'::date),
        ('2026-03-15'::date),
        ('2026-03-22'::date)
) AS dates(log_date)
WHERE mem.email IN (
    'kim.minjun@hanmaum.dev',   'lee.seoyeon@hanmaum.dev',
    'park.junho@hanmaum.dev',   'choi.jieun@hanmaum.dev',
    'jung.hyunwoo@hanmaum.dev', 'kang.minseo@hanmaum.dev',
    'cho.sunghun@hanmaum.dev',  'yoon.jiwon@hanmaum.dev'
)
ON CONFLICT ON CONSTRAINT uq_attendance_log DO NOTHING;

-- 3 Wednesdays in March 2026 — 5 active members

INSERT INTO attendance_logs (definition_id, member_id, attendance_date, attended, created_at)
SELECT
    (SELECT id FROM attendance_definitions WHERE title = '수요 예배'),
    mem.id,
    log_date,
    TRUE,
    NOW()
FROM members mem
CROSS JOIN (
    VALUES
        ('2026-03-04'::date),
        ('2026-03-11'::date),
        ('2026-03-18'::date)
) AS dates(log_date)
WHERE mem.email IN (
    'kim.minjun@hanmaum.dev', 'jung.hyunwoo@hanmaum.dev',
    'cho.sunghun@hanmaum.dev', 'yoon.jiwon@hanmaum.dev',
    'lee.seoyeon@hanmaum.dev'
)
ON CONFLICT ON CONSTRAINT uq_attendance_log DO NOTHING;
