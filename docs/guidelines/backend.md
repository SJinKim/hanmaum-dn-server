# Kotlin und Spring

## Scope

Referenz für Implementierung und Review im bestehenden Server. Geprüft am
27.10.2026 gegen `ca378cbe581557da98717eb61f167461765a6fcc`.
H2-Überschriften bleiben stabile Linkziele für Feature-Designs, etwa
[Transactions and concurrency](#transactions-and-concurrency).

## Current state

**Ist-Stand / Belege:** [build.gradle.kts](../../build.gradle.kts) definiert Kotlin
2.2.21, Spring Boot 4.0.1 und JDK 21. Spring MVC, Bean Validation, Data JPA,
Flyway und OAuth2 Resource Server bilden den bestehenden Rahmen.
Die Kurzbeschreibung „Spring Boot 3“ in [CLAUDE.md](../../CLAUDE.md) ist veraltet;
sie ist kein Auftrag für eine Versionsänderung.

**Bekannte Lücken:** `docs/architecture/adr/` und `patterns/` enthalten auf dieser
Basis nur Platzhalter. Statt daraus Entscheidungen abzuleiten, die bestehenden
[Backend Patterns](../../.claude/skills/backend-patterns.skill.md), Projektregeln
und konkreten Feature-Belege verwenden. Die Beispielszeile in
[Produktentscheidungen](../product/decisions.md) ist keine Produktfreigabe.

## Layers

**Regel / bestehendes Muster:** `common/` enthält gemeinsame Infrastruktur;
`features/<feature>/` bündelt `api/v1`, `service`, `repository`, `domain` und
gegebenenfalls Mapper. Dünne Controller übernehmen HTTP, Validierung und
DTO-Mapping; Services tragen fachliche Regeln und Transaktionsgrenzen,
Spring-Data-Repositories den Datenzugriff. DTOs bleiben von JPA-Entities getrennt.
Keine Port/Adapter-Architektur aus generischen Vorlagen in dieses Repo übertragen.

**Ist-Stand / Belege:**
[MinistryController](../../src/main/kotlin/com/hanmaum/dn/app/features/ministry/api/v1/MinistryController.kt),
[MinistryService](../../src/main/kotlin/com/hanmaum/dn/app/features/ministry/service/MinistryService.kt)
und [Repositories](../../src/main/kotlin/com/hanmaum/dn/app/features/ministry/repository/)
zeigen den Weg. Mapper nach dem jeweils bestehenden Feature organisieren;
keine pauschale Verschiebung aller Mapper. Die
[Backend Patterns](../../.claude/skills/backend-patterns.skill.md) beschreiben die
Schichten; [API DTOs](api.md#dtos-and-validation) ergänzt die Vertragsgrenze.

## Kotlin and configuration

**Regel:** `val` bevorzugen; `var` für tatsächliche Mutation wie JPA-Entityzustand
begründen. Constructor Injection verwenden, Nullbarkeit fachlich ausdrücken,
keine ungesicherten `!!` oder `Any`-Verträge für neue Logik. Bean Validation auf
Request-DTOs mit passenden Use-Site-Targets anwenden. Bestehende Lint-Regeln
befolgen; Ausnahmen brauchen eine konkrete Inline-Begründung. Kein
Pauschal-Refactoring oder neue Bibliothek für eine Dokumentationsstory.

**Ist-Stand / Belege:** Konfigurationsregeln stehen in [CLAUDE.md](../../CLAUDE.md).
Für neue `@Value`-Werte verlangt das Repo einen sinnvollen Default; Secrets
kommen aus privater Konfiguration. Listenbindung mit der tatsächlichen
YAML-/Property-Struktur und Spring-Binding prüfen, nicht aus einem einzelnen
Stringbeispiel ableiten. Profile stehen in
[application.yml](../../src/main/resources/application.yml) und den
[dev](../../src/main/resources/application-dev.yml)/[prod](../../src/main/resources/application-prod.yml)-Dateien.

**Bestehende Besonderheit:**
[SecurityConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/SecurityConfig.kt)
liest erlaubte Issuer als Liste und Audience ohne Inline-Fallback; die Werte
sind in YAML definiert und werden beim Start auf Leerwerte geprüft. Diese
absichtlich strikten Sicherheitswerte nicht wegen der allgemeinen Default-Regel
auf permissive Ersatzwerte umstellen. Für andere Abweichungen die Projektregel
und den konkreten Bedarf vor Umsetzung klären.

## Transactions and concurrency

**Regel:** Transaktionen an Service-Operationen ausrichten; reine Leser mit
`readOnly = true` kennzeichnen. Das ist kein allgemeiner Schreibschutz und löst
keine konkurrierenden Änderungen. Fachliche Invarianten und alle Writer der
betroffenen Entity untersuchen. Sperren, Versionsprüfung oder begrenzte Updates
nach bestehendem Feature auswählen und deren Konfliktverhalten testen.
Sperrreihenfolge, Parent-/Child-Änderungen und Transaktionsgrenzen gemeinsam
betrachten; ein neuer gesperrter Writer schützt nicht automatisch vor alten
ungesperrten Writern.

**Ist-Stand / Belege:**
[BaseEntity](../../src/main/kotlin/com/hanmaum/dn/app/common/jpa/BaseEntity.kt)
besitzt kein allgemeines `@Version`.
[WorshipCountReport](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/domain/WorshipCountReport.kt)
hat dagegen ein Versionsfeld; die
[Guide-Repositories](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/repository/GuideDetailRepositories.kt)
und [WorshipCountRepository](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/repository/WorshipCountRepository.kt)
enthalten explizite Sperren.
[GuideDetailsService](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/service/GuideDetailsService.kt)
zeigt konsistente Reads und transaktionale Writes. Bei Child-Änderungen muss
auch eine erforderliche Parent-Version fortschreiten, wenn Summen gleich bleiben;
siehe [Lessons](../../tasks/lessons.md) und
[GuideDetailsConcurrencyIT](../../src/test/kotlin/com/hanmaum/dn/app/features/guide/service/GuideDetailsConcurrencyIT.kt).

**Nach Commit:**
[MemberNotificationListener](../../src/main/kotlin/com/hanmaum/dn/app/features/notifications/service/MemberNotificationListener.kt)
nutzt `AFTER_COMMIT` und `REQUIRES_NEW`: fachlicher Commit und Zustelltransaktion
sind getrennt. Dieses Muster belegt weder automatische Wiederholung noch
zuverlässige Outbox-Zustellung. Rollback-/Commit-Verhalten wird im
[Listener-Integrationstest](../../src/test/kotlin/com/hanmaum/dn/app/features/notifications/service/MemberNotificationListenerIT.kt)
geprüft. Konflikte mit dem tatsächlichen
[Fehlervertrag](api.md#errors) abgleichen, keine beliebigen Datenbankfehler
verschlucken oder pauschal umetikettieren.

## Testing

**Regel:** Tests sollen das beobachtbare Verhalten und fachliche Invarianten
beweisen. Service-Unit-Tests prüfen Entscheidungen und gespeicherte/gelieferte
Werte; HTTP-/Security-Slices Status, JSON, Validierung und Berechtigungen.
Persistenz, Constraints und Nebenläufigkeit benötigen echte PostgreSQL-Tests.
Ein fixer Mock-Response beweist keinen Write-/Read-Roundtrip.
Bei Bugs einen Regressionstest ergänzen; neue Nutzerflüsse gemäß DoD auch
über den vollständigen Flow absichern.

**Ist-Stand / Belege:**
[MinistryMemberControllerTest](../../src/test/kotlin/com/hanmaum/dn/app/features/ministry/api/v1/MinistryMemberControllerTest.kt)
nutzt `jwt().authorities(...)` und importiert `SecurityConfig`, damit
Methodensicherheit tatsächlich aktiv ist und `@AuthenticationPrincipal Jwt`
aufgelöst wird. Dieses bestehende Muster verwenden; `@WithMockUser` bildet den
Resource-Server-Pfad hier nicht gleichwertig ab. 401, 403, erlaubten Zugriff,
vererbte Rolle und fremdes Objekt passend zum Eingriff abdecken.
Ein Test-JWT prüft den echten Decoder nicht, siehe
[Security Verification](security.md#verification).

[Gradle](../../build.gradle.kts) schließt mit `integration` markierte Tests im
normalen Lauf aus. Die [CI](../../.github/workflows/ci.yml) führt normale Tests
und eine explizite Auswahl von Integrationstests sowie Shell-/Realm-Prüfungen
aus; sie ist keine Zusicherung, dass sämtliche Integrationstests laufen.
Architekturtests sind derzeit nicht eingerichtet.

```bash
./gradlew build -x test
./gradlew ktlintCheck
./gradlew test
# Integrationstests zusätzlich einschließen; eigene Test-DB/Docker vorbereiten:
./gradlew test -PincludeIntegration
```

Datenbankzugang des gewählten Tests vorher prüfen: nicht jede Integrationstestklasse
startet selbst Testcontainers. Gemeinsame Datenbanken/Ports nicht beanspruchen;
eigene Wegwerfressourcen mit expliziten Ports koordinieren. Die Befehle aus
[.team/config.yml](../../.team/config.yml) sind der Einstieg, die Gradle-Tagfilter
bestimmen den tatsächlich geprüften Umfang.

**Für reine Dokumentationsänderungen:** Quellen, Dateilinks, H2-Anker und
GitHub-Markdown-Darstellung sowie `git diff --check` prüfen. #367 ergänzt keine
Laufzeitlogik und keine Architekturtests; ein vollständiger Anwendungstestlauf
liefert dafür keine zusätzliche Abnahmeaussage.

## References

- [CLAUDE.md](../../CLAUDE.md), [Backend Patterns](../../.claude/skills/backend-patterns.skill.md),
  [Lessons](../../tasks/lessons.md).
- [API](api.md), [Security](security.md), [PostgreSQL](postgresql.md).
- [Feature-Design #367](https://github.com/SJinKim/hanmaum-dn-server/issues/367#technisches-design).
