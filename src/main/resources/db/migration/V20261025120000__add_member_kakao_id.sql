-- HDN-360: 카카오 아이디 moves from the newcomer profile to the member, so it survives when the
-- newcomer profile is archived and is carried over when an app registration claims a member.
ALTER TABLE members ADD COLUMN kakao_id TEXT;

-- kakao_id is encrypted with its column as AES-GCM associated data, so SQL cannot move it;
-- V20261025120100 re-encrypts the old values under the member context before
-- V20261025120200 drops newcomer_profiles.kakao_id.
