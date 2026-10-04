-- HDN-267: 새가족 no longer records an English name. Stored values are dropped on purpose.
ALTER TABLE newcomer_profiles DROP COLUMN english_name;
