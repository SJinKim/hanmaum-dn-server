-- HDN-0: Create user_training join table (a member's training progress)
--
-- One row per (member, training). status is IN_PROGRESS | COMPLETED.
-- completed_at is a DATE pinned to the first of the month (YY/MM granularity
-- on the form); null while the training is still in progress.

CREATE TABLE user_training (
    id           BIGSERIAL   PRIMARY KEY,
    public_id    UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id      BIGINT      NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    training_id  BIGINT      NOT NULL REFERENCES training(id),
    status       VARCHAR(20) NOT NULL,
    completed_at DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_user_training_member_training UNIQUE (user_id, training_id)
);

CREATE INDEX idx_user_training_user_id ON user_training(user_id);
CREATE INDEX idx_user_training_training_id ON user_training(training_id);
