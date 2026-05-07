-- =================================================================
-- V1775320000001: Dev seed — announcement data
--
-- Provides 4 sample announcements covering all 3 categories.
-- All have start_at in the past so findActiveAnnouncements returns them.
-- =================================================================

INSERT INTO announcements (title, body, category, start_at, end_at, is_pinned, created_at)
VALUES
  ('4월 주일 예배 안내',
   '이번 주 주일 예배는 오전 11시에 시작합니다. 성찬식이 있을 예정이오니 준비하여 주시기 바랍니다.',
   'NOTICE', NOW() - INTERVAL '7 days', NULL, TRUE, NOW()),

  ('청년부 하계 수련회 신청',
   '7월 18~20일 강원도 수련회를 진행합니다. 5월 31일까지 청년부 리더에게 신청해 주세요. 비용은 1인 5만원입니다.',
   'EVENT', NOW() - INTERVAL '3 days', NOW() + INTERVAL '30 days', FALSE, NOW()),

  ('찬양팀 단원 모집',
   '찬양팀에서 새 단원을 모집합니다. 보컬 및 악기 연주자 모두 환영합니다. 관심 있으신 분은 담당자에게 문의 주세요.',
   'MINISTRY', NOW() - INTERVAL '14 days', NOW() + INTERVAL '14 days', FALSE, NOW()),

  ('교회 주차 안내',
   '주일 예배 시 주차 공간이 협소하오니 가능하면 대중교통을 이용해 주시기 바랍니다.',
   'NOTICE', NOW() - INTERVAL '30 days', NULL, FALSE, NOW());
