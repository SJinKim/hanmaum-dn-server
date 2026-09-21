-- HDN-184: Retain a non-PII provenance marker for every accepted newcomer import row.

CREATE TABLE newcomer_import_records (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    source_fingerprint CHAR(64) NOT NULL,
    row_number INTEGER NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    member_id BIGINT NOT NULL,
    newcomer_profile_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_newcomer_import_records_source_row UNIQUE (source_fingerprint, row_number),
    CONSTRAINT fk_newcomer_import_records_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_newcomer_import_records_profile FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id)
);

CREATE INDEX idx_newcomer_import_records_payload ON newcomer_import_records (payload_fingerprint) WHERE deleted_at IS NULL;
