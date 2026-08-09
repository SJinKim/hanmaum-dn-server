-- HDN-0: track church-group leader tenures (one active leader per group, with history)
CREATE TABLE group_leaders
(
    id         BIGSERIAL PRIMARY KEY,
    public_id  UUID        NOT NULL UNIQUE,
    group_id   BIGINT      NOT NULL REFERENCES church_groups (id),
    member_id  BIGINT      NOT NULL REFERENCES members (id),
    start_date DATE        NOT NULL,
    end_date   DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

-- A group has at most one *current* leader. Enforced in the database rather than only in
-- service code, so a concurrent double-assign cannot leave two open tenures behind.
-- Closed tenures (end_date set) are excluded, which is what makes history possible.
CREATE UNIQUE INDEX uq_group_leaders_active_per_group
    ON group_leaders (group_id)
    WHERE end_date IS NULL AND deleted_at IS NULL;

-- Backs the batched lookups that enrich the members grid and member detail.
CREATE INDEX idx_group_leaders_member ON group_leaders (member_id);
