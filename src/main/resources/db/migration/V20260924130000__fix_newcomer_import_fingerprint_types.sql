-- HDN-202: Align newcomer import fingerprint columns with the JPA VARCHAR(64) mapping.
ALTER TABLE newcomer_import_records
    ALTER COLUMN source_fingerprint TYPE VARCHAR(64),
    ALTER COLUMN payload_fingerprint TYPE VARCHAR(64);
