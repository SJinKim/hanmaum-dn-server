# DB-Schema-Snapshot

`schema.md` ist ein kompakter, **erzeugter** Überblick über das Schema, das die Flyway-Migrationen
aufbauen: Tabellen, Spalten mit Typ, Nullbarkeit und Default, Constraints (inkl. Fremdschlüssel)
und Indizes. Wer DB-Änderungen bewertet, liest diese Datei statt aller Migrationen.

| Datei | Inhalt | Bearbeiten |
|---|---|---|
| `schema.md` | Erzeugter Snapshot | **nie von Hand** |
| `schema-notes.md` | Fachliche Notizen pro Tabelle, werden in `schema.md` übernommen | ja |

## Wie er entsteht

Der CI-Job **Schema snapshot** (`.github/workflows/ci.yml`) läuft bei jedem PR gegen `main` und
jedem Push nach `main`:

1. frische PostgreSQL 16 (wie Staging) als CI-Service,
2. alle Migrationen aus `classpath:db/migration` – SQL aus `src/main/resources/db/migration` und
   Kotlin aus `src/main/kotlin/db/migration` – mit Flyway anwenden, ohne die Anwendung zu starten,
3. Metadaten aus dem PostgreSQL-Katalog lesen, gegen `information_schema` auf Vollständigkeit
   prüfen und als Markdown rendern (`build/schema/schema.md`, Artefakt `schema-snapshot`),
4. byteweise mit `docs/db/schema.md` vergleichen.

Der Kopf von `schema.md` enthält einen SHA-256 über Pfade und Inhalte aller Migrationsdateien.
Jede Änderung, Umbenennung oder Löschung einer Migration macht den Snapshot deshalb veraltet –
auch eine reine Datenmigration ohne sichtbare Schemaänderung.

## Snapshot aktualisieren (ohne lokales Docker)

Ist der Job rot, weil der Snapshot veraltet ist:

1. Im PR den Lauf des **neuesten Commits** öffnen, Job *Schema snapshot*, Artefakt
   `schema-snapshot` herunterladen – oder per CLI:
   ```bash
   gh run download <run-id> -n schema-snapshot -D /tmp/schema-snapshot
   ```
   Die Fehlermeldung des Jobs nennt den fertigen Befehl mit der Run-ID.
2. `/tmp/schema-snapshot/schema.md` nach `docs/db/schema.md` kopieren, Diff prüfen, committen,
   pushen. Der nächste CI-Lauf bestätigt die Datei.

Schlägt schon die Migration oder die Erzeugung fehl, gibt es kein Artefakt – dann zuerst die
Migration reparieren.

Lokal geht es auch, mit einer **leeren, eigenen** Datenbank (nie die gemeinsame Dev-DB):

```bash
SCHEMA_SNAPSHOT_DB_URL=jdbc:postgresql://127.0.0.1:<port>/<leere-db> \
SCHEMA_SNAPSHOT_DB_USER=<user> SCHEMA_SNAPSHOT_DB_PASSWORD=<passwort> \
  ./gradlew schemaSnapshot checkSchemaSnapshot
```

## Fachliche Notizen

`schema-notes.md` hat pro Tabelle einen Abschnitt `## <schema>.<tabelle>`; alles bis zur nächsten
solchen Überschrift wird unverändert unter die Tabelle in `schema.md` übernommen. Innerhalb einer
Notiz nur Überschriften ab `###` verwenden. Notizen zu einer Tabelle, die es nicht mehr gibt,
lassen die Erzeugung scheitern – bei Umbenennung oder Löschung den Abschnitt anpassen. Nach einer
Notizänderung den Snapshot wie oben aktualisieren.
