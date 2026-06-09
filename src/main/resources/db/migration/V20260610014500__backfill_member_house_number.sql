-- HDN-0: Best-effort backfill of member house numbers from legacy street values
--
-- Before the mobile registration form split street and houseNumber, clients stored
-- values like "Hauptstraße 12a" in members.street. For rows where house_number is
-- still empty, move a trailing house-number token into members.house_number and
-- leave only the street name in members.street.
--
-- Intentionally conservative: only splits when the value ends with a whitespace-
-- separated numeric token such as "12", "12a", "12 a", "12-14", or "12/1".
-- Runtime: expected negligible pre-MVP; single pass over members.

UPDATE members
SET
    house_number = trim((regexp_match(street, '\s+([0-9]+\s*[[:alpha:]]?(?:\s*[-/]\s*[0-9]+\s*[[:alpha:]]?)*)\s*$'))[1]),
    street = nullif(trim(regexp_replace(street, '\s+[0-9]+\s*[[:alpha:]]?(?:\s*[-/]\s*[0-9]+\s*[[:alpha:]]?)*\s*$', '')), '')
WHERE house_number IS NULL
  AND street IS NOT NULL
  AND street ~ '\S\s+[0-9]+\s*[[:alpha:]]?(?:\s*[-/]\s*[0-9]+\s*[[:alpha:]]?)*\s*$';
