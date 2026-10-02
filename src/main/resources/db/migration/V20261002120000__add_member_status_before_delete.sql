-- HDN-246: remember the status a member had before the soft delete, so a restore can return to it

ALTER TABLE members
    ADD COLUMN status_before_delete VARCHAR(50);
