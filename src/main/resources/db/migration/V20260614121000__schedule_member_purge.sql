-- HDN-0: Schedule permanent member deletion after the retention window

ALTER TABLE members
    ADD COLUMN delete_entry_at TIMESTAMPTZ;

CREATE INDEX idx_members_delete_entry_at
    ON members (delete_entry_at)
    WHERE deleted_at IS NOT NULL;
