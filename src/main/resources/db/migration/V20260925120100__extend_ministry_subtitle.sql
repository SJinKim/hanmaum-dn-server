-- HDN-214: The ministry description field in the web form allows 500 characters.
ALTER TABLE ministries
    ALTER COLUMN short_description TYPE VARCHAR(500);
