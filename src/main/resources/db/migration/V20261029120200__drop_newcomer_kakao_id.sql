-- HDN-360: V20261029120100 re-encrypted the values onto members, so the old column is redundant.
ALTER TABLE newcomer_profiles DROP COLUMN IF EXISTS kakao_id;
