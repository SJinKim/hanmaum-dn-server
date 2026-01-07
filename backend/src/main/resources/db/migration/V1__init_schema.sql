CREATE SEQUENCE IF NOT EXISTS members_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS members
(
    id BIGINT NOT NULL DEFAULT nextval('members_id_seq'),

    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    deleted_at        TIMESTAMP WITHOUT TIME ZONE,

    korean_name       VARCHAR(255) NOT NULL,
    discriminator     VARCHAR(255),
    gender            VARCHAR(255),
    birth_date        date,
    phone_number      VARCHAR(255),
    address_street    VARCHAR(255),
    registration_date date,
    member_status     VARCHAR(255) DEFAULT 'ACTIVE',

    CONSTRAINT pk_members PRIMARY KEY (id)
);