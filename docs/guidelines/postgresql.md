# PostgreSQL

## Scope

Entscheidungshilfe für Datenmodell, Abfragen und Migrationen. Geprüft am 27.10.2026
gegen `ca378cbe581557da98717eb61f167461765a6fcc`. Feature-Designs verlinken die
H2-Anker, beispielsweise [Table or column](#table-or-column); diese Überschriften
bleiben stabil. Die Regeln dokumentieren den bestehenden Rahmen und ersetzen
keine Freigabe einer Schemaänderung durch Jin.

## Current state

**Ist-Stand / Belege:** [Staging Compose](../../docker-compose.staging.yml) verwendet
PostgreSQL 16. [application.yml](../../src/main/resources/application.yml) aktiviert
Flyway; [dev](../../src/main/resources/application-dev.yml) und
[prod](../../src/main/resources/application-prod.yml) verwenden Hibernate
`ddl-auto: validate`. Hibernate erzeugt damit keine fehlenden Schemaänderungen.
SQL-Migrationen und eine Kotlin-Migration werden gemeinsam versioniert.

**Bekannte Lücken:** `docs/architecture/adr/` und `patterns/` enthalten auf dieser
Basis nur Platzhalter, keine ausgearbeiteten ADRs oder Muster. Der Schema-Snapshot
`docs/db/schema.md` wird separat in [#366](https://github.com/SJinKim/hanmaum-dn-server/issues/366)
erstellt und ist hier noch nicht verfügbar. Nach dessen Merge diesen Snapshot
für DB-Fragen lesen und nur gezielt einzelne Migrationen ergänzen; keine eigene
Schemaerzeugung oder vollständige Migrationslektüre für diese Richtlinie.

## Table or column

**Regel / Best Practice:** Im Feature-Design zuerst Kardinalität, Lebenszyklus und
konkrete Lese-/Schreibabfragen nennen. Eine einzelne Eigenschaft desselben
Datensatzes spricht für eine Spalte. Wiederholbare Werte, eigene Beziehungen oder
eigenständig verwaltete Daten sprechen für eine Tabelle mit Fremdschlüssel.
Bestehende Stammdaten wiederverwenden, bevor eine zweite Darstellung entsteht.
Abfragekosten, Schreibaufwand und Migrationsumfang der einfachsten vollständigen
Lösung begründen. FK-Löschverhalten, Soft Delete und benötigte Historie ausdrücklich
festlegen; ein Soft Delete löst kein SQL-`ON DELETE` aus.

**Ist-Stand / Beleg:** [BaseEntity](../../src/main/kotlin/com/hanmaum/dn/app/common/jpa/BaseEntity.kt)
stellt interne ID, öffentliche UUID und `deletedAt` bereit. Das allein filtert
keine Abfrage. Der [MemberRepository](../../src/main/kotlin/com/hanmaum/dn/app/features/members/repository/MemberRepository.kt)
zeigt explizite `DeletedAtIsNull`-Abfragen. Bestehende Raum-Stammdaten bei
Zählorten berücksichtigen, siehe [Lessons](../../tasks/lessons.md).

## Normalization

**Regel / Best Practice:** Eine fachliche Tatsache hat eine maßgebliche Quelle.
Beziehungen mit FK und fachliche Eindeutigkeit mit passenden Constraints absichern;
keine parallelen Listen oder kopierten Stammdaten ohne nachgewiesenen Bedarf.
Historische Snapshots und berechnete Summen sind zulässig, wenn das Design ihren
Zweck, den Schreibzeitpunkt und die Konsistenzregel erklärt. Ein heutiger
Stammdatenwechsel darf historische Berichte nicht unbeabsichtigt verändern.

**Ist-Stand / Belege:** Das bestehende
[Guide-Detail-Modell](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/domain/)
enthält historische Berichtsdaten; die
[Lessons](../../tasks/lessons.md) beschreiben Fehler durch nachträglich geänderte
Zuordnungen. Verschlüsselte PII braucht gesonderte Suchpfade, siehe
[Security](security.md#pii-and-logging); Normalisierung allein macht Ciphertext
nicht fachlich vergleichbar.

## Indexes

**Regel / Best Practice:** Indizes aus konkreten Filtern, Joins und Sortierungen
ableiten. PK-/Unique-Indizes vor einem zusätzlichen Index berücksichtigen;
Fremdschlüssel erzeugen auf der referenzierenden Seite nicht automatisch einen
Index. Mehrspaltige Reihenfolge und Selektivität anhand der Abfrage begründen.
Keine Vorratsindizes: jeder Index kostet Speicher und Schreibarbeit.
Ein partieller Index für `deleted_at IS NULL` hilft nur Abfragen, deren Prädikat
diese Einschränkung impliziert; bei Unique-Indizes gilt die Eindeutigkeit dann
nur für diese Teilmenge. Grundlage: [PostgreSQL Constraints](https://www.postgresql.org/docs/16/ddl-constraints.html)
und [CREATE INDEX](https://www.postgresql.org/docs/16/sql-createindex.html).

**Ist-Stand / Belege:** Die aktiven Member-Lookups sind im
[Repository](../../src/main/kotlin/com/hanmaum/dn/app/features/members/repository/MemberRepository.kt)
sichtbar. Bei einer DB-Story den Snapshot aus #366 und die konkrete
Indexmigration prüfen, statt aus dem Methodennamen einen vorhandenen Index
abzuleiten. Den Nutzen mit `EXPLAIN` und repräsentativen Daten belegen;
`EXPLAIN ANALYZE` führt die Abfrage tatsächlich aus und gehört bei schreibenden
Abfragen in eine isolierte Testumgebung.

## Migration safety

**Regel / Best Practice:** Für jede Änderung Sperrmodus/-dauer und Rewrite-Risiko
getrennt bewerten. `ALTER TABLE` benötigt regelmäßig `ACCESS EXCLUSIVE`, soweit die
jeweilige Operation keine schwächere Sperre dokumentiert. Ein konstanter Default
vermeidet seit PostgreSQL 11 den sofortigen zeilenweisen Rewrite beim Hinzufügen
einer Spalte; ein volatiler Default kann ihn erzwingen. Auch eine schnelle
Metadatenänderung muss ihre Sperre erst erhalten.
Siehe [Tabellen ändern](https://www.postgresql.org/docs/16/ddl-alter.html) und
[ALTER TABLE](https://www.postgresql.org/docs/16/sql-altertable.html).

Große Backfills in begrenzten, wiederaufnehmbaren Schritten planen: stabile
Auswahl, Fortschritt, Wiederholung und Datenprüfung festlegen. Daten bereinigen,
bevor strengere Constraints aktiviert werden. Bei FK-/CHECK-Constraints kann
`NOT VALID` mit späterem `VALIDATE CONSTRAINT` die Bestandsprüfung trennen;
dieses Verfahren gilt nicht pauschal für Unique- oder NOT-NULL-Constraints.
Code-/Migrationsreihenfolge, Kompatibilität mit der alten Anwendung, Sperrwartezeit,
Laufzeitschätzung und Wiederanlauf gehören ins Design. Irreversible Änderungen
vor Umsetzung Jin vorlegen; kein automatisches Down-Migrationsversprechen.

**Ist-Stand / Abweichung:** Der
[Migrations-Skill](../../.claude/skills/db-migrations/SKILL.md) beschreibt
Expand/Contract und kleine Migrationen. Seine Aussage zu Concurrent Index Builds
ist verkürzt: `CREATE INDEX CONCURRENTLY` darf nicht im Transaktionsblock laufen.
Eine eigene SQL-Datei allein garantiert den passenden Flyway-Ablauf nicht.
Nichttransaktionale Ausführung für die verwendete Flyway-Version explizit
konfigurieren beziehungsweise verifizieren und den Fehler-/Wiederanlaufpfad
prüfen; ein abgebrochener Build kann einen ungültigen Index hinterlassen.
Grundlage: [Building indexes concurrently](https://www.postgresql.org/docs/16/sql-createindex.html#SQL-CREATEINDEX-CONCURRENTLY).

## Flyway

**Regel / bestehende Konvention:** Der
[DB-Migrations-Skill](../../.claude/skills/db-migrations/SKILL.md) definiert
SQL-Pfad, Timestamp-Namen, Ticket-Header und eine logische Änderung pro Datei.
Neue SQL-Dateien verwenden `V<YYYYMMDDHHMM>__<snake_case_description>.sql`,
Repeatables `R__<snake_case_description>.sql` für passende Views/Funktionen.
Bereits gemeinsam angewandte Migrationen bleiben unverändert; Fehler vorwärts
mit einer neuen Migration korrigieren. Alte Dateien nicht zur optischen
Vereinheitlichung umnummerieren. Versionskollisionen vor Merge auflösen.

**Ist-Stand / Beleg:** SQL liegt unter
[db/migration](../../src/main/resources/db/migration/). Für Verschieben
verschlüsselter PII mit spaltengebundenen Associated Data existiert die
[Kotlin-Migration](../../src/main/kotlin/db/migration/V20261021100000__CopyNewcomerWorkOrSchoolToMembers.kt)
unter `src/main/kotlin/db/migration/`, als `BaseJavaMigration` ohne Spring-Bean.
Sie teilt die Versionsfolge mit SQL; der Skill beschreibt das abweichende
Sekundenformat und die Crypto-Initialisierung. Solche Migrationen nur einsetzen,
wenn SQL die notwendige Operation nicht leisten kann. Fehlerbehandlung und
Restdatenprüfung nach dem vorhandenen Muster ausdrücklich testen.

## Verification

**Regel:** Die gesamte Kette auf einer eigenen leeren Wegwerf-PostgreSQL-DB testen,
zusätzlich Upgrade von repräsentativem Altstand, Constraints, Dateninhalt und
Wiederanlauf prüfen. Migrationserfolg und Hibernate-Validierung getrennt belegen.
Persistenz-/Nebenläufigkeitsprüfungen siehe [Backend Testing](backend.md#testing).

**Abweichung:** Den destruktiven Reset-Befehl aus dem Migrations-Skill hier nicht
übernehmen. Gemeinsame lokale Datenbanken und Volumes enthalten echte Arbeit.
Keine gemeinsamen Volumes löschen; eigenes Containerprojekt und expliziten Port
verwenden und nur die selbst erstellten Ressourcen aufräumen. Diese
Dokumentationsstory führt keine Migration oder Live-DB-Prüfung aus.

## References

- [Projektregeln](../../CLAUDE.md) und [Lessons](../../tasks/lessons.md).
- [DB-Migrations-Skill](../../.claude/skills/db-migrations/SKILL.md).
- [Feature-Design #367](https://github.com/SJinKim/hanmaum-dn-server/issues/367#technisches-design).
- Für PostgreSQL-Verhalten gelten die oben verlinkten offiziellen Dokumente
  zur eingesetzten Version 16; sie ersetzen keine Messung des konkreten Vorhabens.
