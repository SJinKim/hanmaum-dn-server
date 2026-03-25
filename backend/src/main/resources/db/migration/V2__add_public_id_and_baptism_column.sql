-- 1. Extension für UUID-Generierung aktivieren (falls noch nicht vorhanden)
-- "pgcrypto" wird für gen_random_uuid() in älteren Postgres-Versionen benötigt.
-- In neueren Versionen (ab v13) ist es nativ, aber dieser Befehl schadet nicht.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Spalten hinzufügen (zunächst NULL, um Fehler bei existierenden Daten zu vermeiden)
ALTER TABLE members
    ADD COLUMN public_id UUID,
    ADD COLUMN baptism VARCHAR(50);

-- 3. Bestehende Daten befüllen
-- Wir geben allen existierenden Mitgliedern eine UUID und setzen die Taufe auf 'UNBAPTIZED'
UPDATE members
SET
    public_id = gen_random_uuid(),
    baptism = 'UNBAPTIZED'
WHERE public_id IS NULL;

-- 4. Constraints setzen (Jetzt, wo Daten vorhanden sind)
-- public_id muss zwingend da sein und eindeutig sein
ALTER TABLE members
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE members ADD CONSTRAINT uq_members_public_id UNIQUE (public_id);

-- 5. Performance Index erstellen
-- Da wir im Frontend und der API fast nur noch über die public_id suchen,
-- macht dieser Index die Suche extrem schnell.
CREATE INDEX idx_members_public_id ON members(public_id);