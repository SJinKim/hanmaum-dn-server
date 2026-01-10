-- 1. GRUPPEN
CREATE TABLE church_groups
(
    id          BIGSERIAL PRIMARY KEY,
    division    VARCHAR(50),           -- "다니엘", "느헤미야"
    name        VARCHAR(100) NOT NULL, -- "사랑", "온유"
    created_at  TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_group_division_name UNIQUE (division, name)
);

-- 2. MITGLIEDER
CREATE TABLE members
(
    id BIGSERIAL PRIMARY KEY,
    last_name         VARCHAR(100) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    discriminator     VARCHAR(10),
    gender            VARCHAR(10),
    birth_date        DATE,
    phone_number      VARCHAR(50),
    email             VARCHAR(100),

    -- Neue Adress-Struktur
    street            VARCHAR(255),
    zip_code          VARCHAR(10),
    city              VARCHAR(100),

    registration_date DATE,
    member_status     VARCHAR(50) DEFAULT 'ACTIVE',
    role              VARCHAR(50),

    group_id          BIGINT REFERENCES church_groups(id),

    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

-- 3. FAMILIEN
CREATE TABLE family_relationships
(
    id BIGSERIAL PRIMARY KEY,
    member_id         BIGINT NOT NULL REFERENCES members(id),
    related_member_id BIGINT NOT NULL REFERENCES members(id),
    relationship_type VARCHAR(50) NOT NULL,
    created_at        TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_family_rel UNIQUE (member_id, related_member_id)
);

-- 4. ANWESENHEIT
CREATE TABLE attendance_logs
(
    id BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL REFERENCES members(id),
    date        DATE NOT NULL,
    category    VARCHAR(50) DEFAULT 'SUNDAY_SERVICE',
    status      VARCHAR(20) DEFAULT 'PRESENT',
    created_at  TIMESTAMP DEFAULT NOW()
);

-- 5. INDEXES
CREATE INDEX idx_members_group ON members(group_id);
CREATE INDEX idx_members_city ON members(city);
CREATE INDEX idx_groups_division ON church_groups(division);
CREATE INDEX idx_attendance_date ON attendance_logs(date);