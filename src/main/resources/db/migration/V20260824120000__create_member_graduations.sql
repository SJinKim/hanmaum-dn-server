-- HDN-YY: Track members who have left the DN community.
--
-- Modelled as an event rather than a column on members: an audit trail of who marked
-- whom and when is the point, and a mistaken graduation has to stay visible after it is
-- corrected. A member may hold many historical graduations but at most one open.

CREATE TABLE member_graduations (
    id        BIGSERIAL PRIMARY KEY,
    public_id UUID      NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    member_id BIGINT    NOT NULL,

    graduated_on DATE        NOT NULL,
    reason       VARCHAR(30) NOT NULL,
    -- Free text written by an admin; may name a spouse or another member, so it is
    -- encrypted by the application like every other personal field.
    note         TEXT,

    -- Keycloak subject of the acting admin. Pseudonymous, matching the convention used
    -- for actor columns elsewhere.
    graduated_by VARCHAR(64) NOT NULL,

    -- The member's status immediately before graduation. Reinstating restores this
    -- rather than assuming ACTIVE: a PENDING member graduated by mistake must come back
    -- as PENDING.
    --
    -- Deliberately not constrained to the MemberStatus values. The enum already carries
    -- a deprecated REJECTED, and this column is a snapshot of history — a future enum
    -- change must not invalidate rows written before it.
    previous_member_status VARCHAR(20) NOT NULL,

    reverted_at TIMESTAMPTZ,
    reverted_by VARCHAR(64),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT ck_member_graduations_reason
        CHECK (reason IN ('MARRIAGE', 'RELOCATION', 'AGE_OUT', 'OTHER')),
    -- Both revert columns are written together or not at all.
    CONSTRAINT ck_member_graduations_revert
        CHECK ((reverted_at IS NULL) = (reverted_by IS NULL)),
    CONSTRAINT fk_member_graduations_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
);

-- The core invariant, enforced by the database rather than by service code so that a
-- concurrent double-POST cannot produce two open rows.
CREATE UNIQUE INDEX uq_member_graduations_open
    ON member_graduations (member_id)
    WHERE reverted_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_member_graduations_member
    ON member_graduations (member_id);
