-- HDN-275: PENDING is the app-approval queue. Members from the 새가족 intake have no account
-- and were never waiting for approval, yet they were created PENDING and counted as 승인 대기.
UPDATE members
SET member_status = 'ACTIVE'
WHERE origin = 'NEWCOMER_FORM'
  AND member_status = 'PENDING'
  AND keycloak_id IS NULL
  AND deleted_at IS NULL;
