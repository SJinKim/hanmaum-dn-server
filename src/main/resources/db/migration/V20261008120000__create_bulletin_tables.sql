-- HDN-289: weekly 주보 editions, their ordered lists, and the tables they reference.
--
-- bulletin_service and bulletin_section_title come from HDN-291 but are created here,
-- because bulletin_edition.service_id needs its target. HDN-291 adds only the endpoints.
--
-- Content columns of an edition are nullable on purpose: a draft may be incomplete.
-- Required fields are checked when publishing (HDN-146), not by the database.
--
-- Column lengths: names of people 100, single-line titles 200, Bible references 100
-- ("고린도후서 5:17-21" plus room for several ranges), free text 2000. created_by and
-- updated_by hold the Keycloak subject, as in newcomer_form_links (64).

CREATE TABLE bulletin_service
(
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID        NOT NULL UNIQUE,
    name                VARCHAR(50) NOT NULL,
    -- Wall-clock start in Europe/Berlin; a 주보 prints it, it never takes part in instants.
    start_time          TIME        NOT NULL,
    sort_order          INT         NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    is_bulletin_default BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

-- At most one service is preselected for a new edition.
CREATE UNIQUE INDEX uq_bulletin_service_default ON bulletin_service (is_bulletin_default)
    WHERE is_bulletin_default;

-- Keys are fixed; only the title can be renamed or reset to default_title.
CREATE TABLE bulletin_section_title
(
    key           VARCHAR(40) PRIMARY KEY,
    title         VARCHAR(50) NOT NULL,
    default_title VARCHAR(50) NOT NULL,
    updated_by    VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT ck_bulletin_section_title_key CHECK (key IN ('SECTION_WORSHIP', 'SECTION_OFFERING',
                                                            'SECTION_SENDING', 'FIXED_BLESSING_PRAYER'))
);

CREATE TABLE bulletin_edition
(
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID        NOT NULL UNIQUE,
    service_date        DATE        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- VOL number, assigned on first publish (HDN-290); never reused.
    volume              INT,
    service_id          BIGINT      NOT NULL,
    -- Snapshot taken on publish, so a later change to the service leaves old editions alone.
    service_name        VARCHAR(50),
    service_start_time  TIME,
    opening_prayer_by   VARCHAR(100),
    offering_song_by    VARCHAR(100),
    scripture_reference VARCHAR(100),
    sermon_title        VARCHAR(200),
    sermon_preacher     VARCHAR(100),
    response_prayer_by  VARCHAR(100),
    response_song       VARCHAR(200),
    published_at        TIMESTAMPTZ,
    withdrawn_at        TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_bulletin_edition_service_date UNIQUE (service_date),
    CONSTRAINT uq_bulletin_edition_volume UNIQUE (volume),
    CONSTRAINT fk_bulletin_edition_service FOREIGN KEY (service_id) REFERENCES bulletin_service (id),
    CONSTRAINT ck_bulletin_edition_sunday CHECK (extract(isodow FROM service_date) = 7),
    CONSTRAINT ck_bulletin_edition_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT ck_bulletin_edition_volume CHECK (volume IS NULL OR volume > 0),
    CONSTRAINT ck_bulletin_edition_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_bulletin_edition_service ON bulletin_edition (service_id);

-- 예배를 여는 찬양. The 1–8 limit is a publish rule (HDN-146), not a constraint.
CREATE TABLE bulletin_song
(
    edition_id BIGINT       NOT NULL REFERENCES bulletin_edition (id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    title      VARCHAR(200) NOT NULL,
    PRIMARY KEY (edition_id, position)
);

-- 교회소식
CREATE TABLE bulletin_announcement
(
    edition_id BIGINT        NOT NULL REFERENCES bulletin_edition (id) ON DELETE CASCADE,
    position   INT           NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       VARCHAR(2000),
    PRIMARY KEY (edition_id, position)
);

-- 설교 나눔
CREATE TABLE bulletin_sharing_block
(
    edition_id BIGINT        NOT NULL REFERENCES bulletin_edition (id) ON DELETE CASCADE,
    position   INT           NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    text       VARCHAR(2000) NOT NULL,
    reference  VARCHAR(100),
    PRIMARY KEY (edition_id, position),
    CONSTRAINT ck_bulletin_sharing_block_type CHECK (type IN ('HEADING', 'PARAGRAPH', 'SCRIPTURE', 'QUESTION')),
    CONSTRAINT ck_bulletin_sharing_block_reference CHECK (type = 'SCRIPTURE' OR reference IS NULL)
);
