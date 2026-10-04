-- HDN-261: 방문 기록. Visitors captured on the day, kept apart from newcomer_profiles as
-- historical data for the 새가족 funnel (방문 → 새가족 등록 → 등반) and the 광고 evaluation.

CREATE TABLE newcomer_visits (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    visit_date DATE NOT NULL,
    last_name TEXT NOT NULL,
    first_name TEXT NOT NULL,
    gender TEXT,
    birth_year INTEGER,
    visit_type VARCHAR(20) NOT NULL,
    source VARCHAR(30),
    note TEXT,
    newcomer_profile_id BIGINT,
    pii_key_id VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_newcomer_visits_profile FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id) ON DELETE SET NULL,
    CONSTRAINT ck_newcomer_visits_type CHECK (visit_type IN ('FIRST', 'REVISIT')),
    CONSTRAINT ck_newcomer_visits_source CHECK (source IS NULL OR source IN ('FRIEND_FAMILY', 'ADVERTISEMENT', 'SOCIAL_MEDIA', 'WEBSITE', 'WALK_IN', 'OTHER')),
    CONSTRAINT ck_newcomer_visits_birth_year CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 2100)
);

CREATE INDEX idx_newcomer_visits_date ON newcomer_visits (visit_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_newcomer_visits_profile ON newcomer_visits (newcomer_profile_id) WHERE deleted_at IS NULL;
