-- HDN-167: The DN side of an application submitted to application.hanmaum.de
--
-- The external API makes POST /applications idempotent on clientApplicationId, but only if
-- the caller sends the same id again. That id has to outlive the request that minted it —
-- a timeout, a crashed pod, a user tapping 신청하기 twice — so it is written here BEFORE the
-- external call, as PENDING, and reused by every retry for the same member and course.
--
-- external_application_id stays NULL until the external API confirms the application.
-- A PENDING row without it means "we may or may not have created it"; the service asks
-- GET /applications/by-client-id/{uuid} before sending again.
--
-- No PII here: names, birth dates and phone numbers are sent to the external API and are
-- not copied into this table.
--
-- The partial unique index is the concurrency-safe guard that one member has at most one
-- live attempt per external course. CANCELLED rows are excluded so a later re-application
-- is possible once cancellation exists.

CREATE TABLE course_application_attempts
(
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID        NOT NULL UNIQUE,
    member_id               BIGINT      NOT NULL REFERENCES members (id),
    training_id             BIGINT      NOT NULL REFERENCES training (id),
    external_course_id      INTEGER     NOT NULL,
    -- The course name as published when the member applied, e.g. "큐베세 직장인/청년 반".
    -- Kept so 나의 신청 can list applications without asking the external API.
    external_course_name    VARCHAR(255) NOT NULL,
    client_application_id   UUID        NOT NULL UNIQUE,
    external_application_id BIGINT,
    status                  VARCHAR(16) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT ck_course_application_attempt_status CHECK (status IN ('PENDING', 'CREATED', 'CANCELLED')),
    CONSTRAINT ck_course_application_attempt_created_has_external_id
        CHECK (status <> 'CREATED' OR external_application_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_course_application_attempt_member_course
    ON course_application_attempts (member_id, external_course_id)
    WHERE deleted_at IS NULL AND status <> 'CANCELLED';

CREATE INDEX idx_course_application_attempt_member
    ON course_application_attempts (member_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE course_application_attempts IS
    'Idempotency ledger for applications proxied to application.hanmaum.de. No PII.';
