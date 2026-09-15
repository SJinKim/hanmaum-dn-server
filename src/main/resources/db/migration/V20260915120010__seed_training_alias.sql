-- HDN-167: Seed the aliases confirmed against the external course list of 2026-09-14
--
--   큐베세            QT_BASIC_SEMINAR          큐베세 여자반, 큐베세 직장인/청년 반
--   청년부 파워제자반  YOUTH_POWER_DISCIPLESHIP  제4기 청년부 파워제자반, 제5기 …
--   세례교육          BAPTISM_MEMBERSHIP        2025 상반기 세례교육 신청, …
--
-- Idempotent: rows are looked up by code and inserted only when missing. A code that does
-- not exist inserts nothing rather than failing, the same way the catalog seed tolerates a
-- partially seeded database.

INSERT INTO training_alias (training_id, alias)
SELECT t.id, v.alias
FROM (VALUES ('QT_BASIC_SEMINAR', '큐베세'),
             ('YOUTH_POWER_DISCIPLESHIP', '청년부 파워제자반'),
             ('BAPTISM_MEMBERSHIP', '세례교육')) AS v (code, alias)
         JOIN training t ON t.code = v.code
ON CONFLICT (training_id, alias) DO NOTHING;
