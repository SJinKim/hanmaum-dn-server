-- HDN-223: optional description for attendance definitions (출석 정의 추가 dialog)

ALTER TABLE attendance_definitions
    ADD COLUMN description VARCHAR(500);
