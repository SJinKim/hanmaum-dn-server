CREATE TABLE member_reconciliations (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    registration_member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reasons VARCHAR(500) NOT NULL,
    conflict_fields VARCHAR(500),
    selected_member_id BIGINT,
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_member_reconciliations_registration FOREIGN KEY (registration_member_id) REFERENCES members(id),
    CONSTRAINT fk_member_reconciliations_selected FOREIGN KEY (selected_member_id) REFERENCES members(id),
    CONSTRAINT ck_member_reconciliations_status CHECK (status IN ('OPEN', 'LINKED', 'DISMISSED'))
);

CREATE TABLE member_reconciliation_candidates (
    reconciliation_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    PRIMARY KEY (reconciliation_id, member_id),
    CONSTRAINT fk_reconciliation_candidates_case FOREIGN KEY (reconciliation_id) REFERENCES member_reconciliations(id) ON DELETE CASCADE,
    CONSTRAINT fk_reconciliation_candidates_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE UNIQUE INDEX uq_member_reconciliations_open_registration
    ON member_reconciliations (registration_member_id)
    WHERE status = 'OPEN' AND deleted_at IS NULL;
CREATE INDEX idx_member_reconciliations_status ON member_reconciliations (status) WHERE deleted_at IS NULL;
