-- HDN-332: 신분 and 소속 move from the newcomer profile to the member, so self-registration
-- can set them before any newcomer profile exists.
ALTER TABLE members ADD COLUMN identity_status VARCHAR(30);
ALTER TABLE members ADD COLUMN work_or_school TEXT;
ALTER TABLE members ADD CONSTRAINT ck_members_identity_status CHECK (identity_status IS NULL OR identity_status IN ('EMPLOYEE', 'UNIVERSITY_STUDENT', 'EXAM_PREPARATION', 'WORKING_HOLIDAY', 'EXCHANGE_STUDENT', 'SELF_EMPLOYED', 'EXPATRIATE', 'JOB_SEEKING'));

UPDATE members m
SET identity_status = np.identity_status
FROM newcomer_profiles np
WHERE np.member_id = m.id
  AND np.identity_status IS NOT NULL;

-- work_or_school is encrypted with its column as AES-GCM associated data, so SQL cannot move
-- it; MemberWorkOrSchoolBackfillRunner re-encrypts it at startup and clears the old column.
-- newcomer_profiles.identity_status stays until a later migration drops both old columns.
