-- =================================================================
-- PRE-CLEANUP: Alte V3-Tabellen löschen, falls sie existieren.
-- Das macht das Skript "wiederholbar", ohne dass es Fehler gibt.
-- WICHTIG: Deine 'members' Tabelle wird HIER NICHT ANGEFASST!
-- =================================================================
DROP TABLE IF EXISTS meeting_attendances CASCADE;
DROP TABLE IF EXISTS group_meetings CASCADE;
DROP TABLE IF EXISTS dishwashing_schedule CASCADE;
DROP TABLE IF EXISTS car_passengers CASCADE;
DROP TABLE IF EXISTS cars CASCADE;
DROP TABLE IF EXISTS attendance_definitions CASCADE;
DROP TABLE IF EXISTS announcements CASCADE;


-- =================================================================
-- NEUER AUFBAU (Deine neue Struktur)
-- =================================================================

-- 1. ANNOUNCEMENTS (Mitteilungen)
CREATE TABLE announcements (
                               id BIGSERIAL PRIMARY KEY,

    -- UUID public_id (generiert sich selbst)
                               public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

                               title VARCHAR(255) NOT NULL,
                               body TEXT NOT NULL,

    -- Kategorie (NOTICE, EVENT, MINISTRY)
                               category VARCHAR(50) NOT NULL,

    -- Gültigkeitszeitraum
                               start_at TIMESTAMP NOT NULL,
                               end_at TIMESTAMP,

    -- is_pinned entspricht deinem "Banner"-Feature
                               is_pinned BOOLEAN DEFAULT FALSE,

                               created_at TIMESTAMP DEFAULT NOW(),
                               updated_at TIMESTAMP,
                               deleted_at TIMESTAMP
);

-- 2. ATTENDANCE DEFINITIONS
CREATE TABLE attendance_definitions (
                                        id BIGSERIAL PRIMARY KEY,
                                        day_of_week VARCHAR(20) NOT NULL,
                                        start_time TIME NOT NULL,
                                        end_time TIME NOT NULL,
                                        title VARCHAR(100),
                                        is_active BOOLEAN DEFAULT TRUE,
                                        created_at TIMESTAMP DEFAULT NOW(),
                                        updated_at TIMESTAMP,
                                        deleted_at TIMESTAMP
);

-- 3. CARPOOL (Autos)
CREATE TABLE cars (
                      id BIGSERIAL PRIMARY KEY,
                      driver_member_id BIGINT NOT NULL REFERENCES members(id), -- Verknüpfung bleibt heil!
                      session_date DATE NOT NULL,
                      name VARCHAR(100),
                      max_seats INT NOT NULL,
                      current_passengers INT DEFAULT 0,
                      departure_location VARCHAR(255),
                      departure_time TIME,
                      created_at TIMESTAMP DEFAULT NOW(),
                      updated_at TIMESTAMP,
                      deleted_at TIMESTAMP
);

CREATE TABLE car_passengers (
                                id BIGSERIAL PRIMARY KEY,
                                car_id BIGINT NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
                                member_id BIGINT NOT NULL REFERENCES members(id),
                                created_at TIMESTAMP DEFAULT NOW(),
                                updated_at TIMESTAMP,
                                deleted_at TIMESTAMP,
                                CONSTRAINT uq_member_car_assignment UNIQUE (member_id, car_id)
);

-- 4. DISHWASHING SCHEDULE
CREATE TABLE dishwashing_schedule (
                                      id BIGSERIAL PRIMARY KEY,
                                      scheduled_date DATE NOT NULL,
                                      group_id BIGINT NOT NULL REFERENCES church_groups(id),
                                      note VARCHAR(255),
                                      CONSTRAINT uq_dishwashing_date_group UNIQUE (scheduled_date, group_id),
                                      created_at TIMESTAMP DEFAULT NOW(),
                                      updated_at TIMESTAMP,
                                      deleted_at TIMESTAMP
);

-- 5. GROUP MEETINGS
CREATE TABLE group_meetings (
                                id BIGSERIAL PRIMARY KEY,
                                group_id BIGINT NOT NULL REFERENCES church_groups(id),
                                meeting_time TIMESTAMP NOT NULL,
                                location VARCHAR(255),
                                description TEXT,
                                created_at TIMESTAMP DEFAULT NOW(),
                                updated_at TIMESTAMP,
                                deleted_at TIMESTAMP
);

CREATE TABLE meeting_attendances (
                                     id BIGSERIAL PRIMARY KEY,
                                     meeting_id BIGINT NOT NULL REFERENCES group_meetings(id),
                                     member_id BIGINT NOT NULL REFERENCES members(id),
                                     attendance_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
                                     prayer_request TEXT,
                                     created_at TIMESTAMP DEFAULT NOW(),
                                     CONSTRAINT uq_meeting_member UNIQUE (meeting_id, member_id),
                                     updated_at TIMESTAMP,
                                     deleted_at TIMESTAMP
);