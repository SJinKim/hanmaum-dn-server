-- HDN-181: Add the protected one-to-one newcomer process profile.

CREATE TABLE newcomer_profiles (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    member_id BIGINT NOT NULL UNIQUE,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    intake_round INTEGER,
    has_visited BOOLEAN NOT NULL DEFAULT FALSE,
    caregiver_member_id BIGINT,
    identity_status VARCHAR(30),
    work_or_school TEXT,
    first_visit_date DATE,
    assigned_group_id BIGINT,
    assignment_reason TEXT,
    overall_notes TEXT,
    post_assignment_attendance VARCHAR(30),
    english_name TEXT,
    kakao_id TEXT,
    previous_church TEXT,
    church_experience VARCHAR(30),
    visit_motives TEXT,
    additional_notes TEXT,
    pii_key_id VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_newcomer_profiles_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_newcomer_profiles_caregiver FOREIGN KEY (caregiver_member_id) REFERENCES members(id),
    CONSTRAINT fk_newcomer_profiles_group FOREIGN KEY (assigned_group_id) REFERENCES church_groups(id),
    CONSTRAINT ck_newcomer_profiles_lifecycle CHECK (lifecycle_status IN ('SUBMITTED', 'IN_CARE', 'GRADUATED', 'ARCHIVED')),
    CONSTRAINT ck_newcomer_profiles_identity CHECK (identity_status IS NULL OR identity_status IN ('EMPLOYEE', 'UNIVERSITY_STUDENT', 'EXAM_PREPARATION', 'WORKING_HOLIDAY', 'EXCHANGE_STUDENT', 'SELF_EMPLOYED', 'EXPATRIATE', 'JOB_SEEKING')),
    CONSTRAINT ck_newcomer_profiles_attendance CHECK (post_assignment_attendance IS NULL OR post_assignment_attendance IN ('REGULAR', 'OCCASIONAL', 'WORSHIP_ONLY', 'ABSENT_OVER_MONTH', 'CHANGED_CHURCH', 'RETURNED_OR_MOVED')),
    CONSTRAINT ck_newcomer_profiles_experience CHECK (church_experience IS NULL OR church_experience IN ('FIRST_TIME', 'CHILDHOOD_FEW_TIMES', 'IRREGULAR', 'REGULAR'))
);

CREATE INDEX idx_newcomer_profiles_status ON newcomer_profiles (lifecycle_status) WHERE deleted_at IS NULL;
CREATE INDEX idx_newcomer_profiles_caregiver ON newcomer_profiles (caregiver_member_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_newcomer_profiles_group ON newcomer_profiles (assigned_group_id) WHERE deleted_at IS NULL;
