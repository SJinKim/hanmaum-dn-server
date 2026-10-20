-- HDN-342: 직분 (members.role) was never filled by any client; the field and its column go.
ALTER TABLE members DROP COLUMN IF EXISTS role;
