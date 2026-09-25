-- HDN-170: Keep self-applications in the team assignment table with separate encrypted messages.
ALTER TABLE ministry_registrations
    ADD COLUMN self_introduction TEXT,
    ADD COLUMN rejection_message TEXT,
    ADD COLUMN leader_notified_at TIMESTAMP;

ALTER TABLE ministry_registrations DROP CONSTRAINT ck_ministry_assignment_status;
ALTER TABLE ministry_registrations
    ADD CONSTRAINT ck_ministry_assignment_status
        CHECK (assignment_status IN ('ACTIVE', 'PENDING', 'REJECTED'));

CREATE UNIQUE INDEX uq_ministry_pending_self_registration
    ON ministry_registrations (ministry_id, member_id)
    WHERE assignment_status = 'PENDING' AND end_date IS NULL AND deleted_at IS NULL;
