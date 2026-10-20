-- HDN-339: 신분 and 소속 live on members since HDN-332. V20261020150000 copied identity_status
-- and V20261021100000 re-encrypted work_or_school onto members, both earlier in this same run,
-- so the old columns hold nothing the app still reads. Dropping them also drops
-- ck_newcomer_profiles_identity.
ALTER TABLE newcomer_profiles DROP COLUMN IF EXISTS identity_status;
ALTER TABLE newcomer_profiles DROP COLUMN IF EXISTS work_or_school;
