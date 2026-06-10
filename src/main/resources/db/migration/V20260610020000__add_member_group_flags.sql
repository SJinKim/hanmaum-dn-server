ALTER TABLE members
    ADD COLUMN is_next_group_leader     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN one_on_one_signup_filled BOOLEAN NOT NULL DEFAULT FALSE;
