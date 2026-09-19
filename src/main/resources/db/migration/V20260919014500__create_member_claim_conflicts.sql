-- HDN-185: retain member-claim conflicts for staff review
CREATE TABLE member_claim_conflicts (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    registration_member_id BIGINT NOT NULL REFERENCES members(id),
    candidate_member_id BIGINT NOT NULL REFERENCES members(id),
    reason VARCHAR(64) NOT NULL,
    conflict_fields VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_member_claim_conflicts_open_registration
    ON member_claim_conflicts (registration_member_id)
    WHERE deleted_at IS NULL;
