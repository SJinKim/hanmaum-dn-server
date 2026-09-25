-- HDN-214: Add role and status to ministry assignments without changing historical rows.
ALTER TABLE ministry_registrations
    ADD COLUMN assignment_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN assignment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE ministry_registrations
    ADD CONSTRAINT ck_ministry_assignment_role
        CHECK (assignment_role IN ('LEADER', 'SUB_LEADER', 'MEMBER')),
    ADD CONSTRAINT ck_ministry_assignment_status
        CHECK (assignment_status IN ('ACTIVE', 'PENDING'));

-- One current leader per ministry; ended and soft-deleted history is unaffected.
CREATE UNIQUE INDEX uq_ministry_current_leader
    ON ministry_registrations (ministry_id)
    WHERE assignment_role = 'LEADER' AND end_date IS NULL AND deleted_at IS NULL;
