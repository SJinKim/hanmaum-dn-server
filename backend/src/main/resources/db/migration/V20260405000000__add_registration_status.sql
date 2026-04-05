-- V20260405000000__add_registration_status.sql
ALTER TABLE ministry_registrations
    ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
