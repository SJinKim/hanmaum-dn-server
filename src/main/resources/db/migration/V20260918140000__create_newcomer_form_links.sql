CREATE TABLE newcomer_form_links (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    use_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE newcomer_form_submissions (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    form_link_id BIGINT NOT NULL,
    newcomer_profile_id BIGINT NOT NULL,
    idempotency_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_form_submissions_link FOREIGN KEY (form_link_id) REFERENCES newcomer_form_links(id),
    CONSTRAINT fk_form_submissions_profile FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id),
    CONSTRAINT uq_form_submissions_idempotency UNIQUE (form_link_id, idempotency_hash)
);

CREATE INDEX idx_newcomer_form_links_active ON newcomer_form_links (expires_at) WHERE revoked_at IS NULL AND deleted_at IS NULL;
