-- HDN-289 / HDN-291: initial 주보 services and section titles.
--
-- The start times are placeholders; the admin sets the real ones (HDN-291).
-- 3부 is preselected for new editions.

INSERT INTO bulletin_service (public_id, name, start_time, sort_order, is_bulletin_default)
VALUES (gen_random_uuid(), '1부 예배', '09:00', 1, FALSE),
       (gen_random_uuid(), '2부 예배', '11:00', 2, FALSE),
       (gen_random_uuid(), '3부 예배', '14:00', 3, TRUE);

INSERT INTO bulletin_section_title (key, title, default_title)
VALUES ('SECTION_WORSHIP', '경배와 찬양', '경배와 찬양'),
       ('SECTION_OFFERING', '봉헌', '봉헌'),
       ('SECTION_SENDING', '축복과 파송', '축복과 파송'),
       ('FIXED_BLESSING_PRAYER', '봉헌 및 축복기도', '봉헌 및 축복기도');
