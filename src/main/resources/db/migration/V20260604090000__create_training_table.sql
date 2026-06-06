-- HDN-0: Create training lookup table (discipleship training catalog)
--
-- A small admin-managed catalog of trainings (QTBS, 1on1, Discipleship, ...).
-- sort_order defines the progression order; the members grid surfaces a
-- member's latest completed training by the highest sort_order.

CREATE TABLE training (
    id         BIGSERIAL    PRIMARY KEY,
    public_id  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_training_name UNIQUE (name)
);

CREATE INDEX idx_training_sort_order ON training(sort_order);
