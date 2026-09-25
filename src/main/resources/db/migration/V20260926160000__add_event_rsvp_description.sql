-- HDN-226: optional description for events (새 이벤트 추가 dialog)

ALTER TABLE event_rsvps
    ADD COLUMN description VARCHAR(500);
