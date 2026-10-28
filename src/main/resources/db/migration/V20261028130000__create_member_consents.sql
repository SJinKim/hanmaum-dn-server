-- HDN-358: Consent to processing personal data, one row per grant (GDPR Art. 7(1)).
--
-- Append-only. Granting writes a row; withdrawing or a newer policy version ends the row
-- (ended_at + end_reason) instead of deleting it, so the history of what a member agreed to,
-- and when, stays provable. A new grant after an end writes a new row.
--
-- policy_version names the text the member saw, e.g. 'v1'. The app sends the version it
-- displayed and the server rejects a stale one, so a row never claims a text nobody showed.
--
-- No PII beyond the member reference: no IP address, no device identifiers.

CREATE TABLE member_consents
(
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID        NOT NULL UNIQUE,
    member_id      BIGINT      NOT NULL REFERENCES members (id),
    purpose        VARCHAR(50) NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    source         VARCHAR(30) NOT NULL,
    granted_at     TIMESTAMPTZ NOT NULL,
    ended_at       TIMESTAMPTZ,
    end_reason     VARCHAR(20),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT ck_member_consent_end_reason CHECK (end_reason IN ('WITHDRAWN', 'SUPERSEDED')),
    CONSTRAINT ck_member_consent_end_pair CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

-- At most one standing grant per member and purpose. Ended rows are history and excluded.
CREATE UNIQUE INDEX uq_member_consent_standing
    ON member_consents (member_id, purpose)
    WHERE ended_at IS NULL AND deleted_at IS NULL;

COMMENT ON TABLE member_consents IS
    'Append-only consent ledger (HDN-358). Rows are ended, never updated in place or deleted, except by member purge.';
