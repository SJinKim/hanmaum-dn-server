-- HDN-0: structure ministry detail content for backend-driven clients

ALTER TABLE ministries
    ADD COLUMN leader_name VARCHAR(150),
    ADD COLUMN coordinator_name VARCHAR(150);

UPDATE ministries
SET long_description = short_description
WHERE long_description IS NULL;

ALTER TABLE ministries
    ALTER COLUMN long_description SET NOT NULL;

UPDATE ministries AS ministry
SET leader_name = CONCAT(member.last_name, member.first_name)
FROM members AS member
WHERE ministry.leader_member_id = member.id
  AND ministry.leader_name IS NULL;

UPDATE ministries
SET leader_name = '미정'
WHERE leader_name IS NULL;

ALTER TABLE ministries
    ALTER COLUMN leader_name SET NOT NULL;

DROP INDEX IF EXISTS idx_ministries_leader_member;

ALTER TABLE ministries
    DROP COLUMN leader_member_id;

CREATE TABLE ministry_requirements
(
    ministry_id   BIGINT  NOT NULL REFERENCES ministries (id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    description   TEXT    NOT NULL,
    PRIMARY KEY (ministry_id, display_order)
);

CREATE TABLE ministry_schedules
(
    ministry_id   BIGINT       NOT NULL REFERENCES ministries (id) ON DELETE CASCADE,
    display_order INTEGER      NOT NULL,
    description   VARCHAR(200) NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    PRIMARY KEY (ministry_id, display_order)
);
