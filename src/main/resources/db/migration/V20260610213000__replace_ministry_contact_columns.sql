-- HDN-0: replace fixed ministry contact columns with ordered contacts

CREATE TABLE ministry_contacts
(
    ministry_id   BIGINT       NOT NULL REFERENCES ministries (id) ON DELETE CASCADE,
    display_order INTEGER      NOT NULL,
    role           VARCHAR(50)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    PRIMARY KEY (ministry_id, display_order)
);

INSERT INTO ministry_contacts (ministry_id, display_order, role, name)
SELECT id, 0, '팀장', leader_name
FROM ministries
WHERE leader_name IS NOT NULL;

INSERT INTO ministry_contacts (ministry_id, display_order, role, name)
SELECT id, 1, '간사', coordinator_name
FROM ministries
WHERE coordinator_name IS NOT NULL;

ALTER TABLE ministries
    DROP COLUMN leader_name,
    DROP COLUMN coordinator_name;
