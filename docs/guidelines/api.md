# API

## Scope

Referenz für REST-Verträge und Reviews. Geprüft am 27.10.2026 gegen
`ca378cbe581557da98717eb61f167461765a6fcc`. H2-Überschriften bleiben als
Feature-Design-Anker stabil, etwa [Contract workflow](#contract-workflow).

## Current state

**Ist-Stand / Belege:** Der gemeinsame Vertrag ist
[hanmaum-dn-ops/api/openapi.yaml](https://github.com/SJinKim/hanmaum-dn-ops/blob/main/api/openapi.yaml),
erzeugt aus Server-Controllern und DTOs mit springdoc. Maßgeblich für den Ablauf
sind [API-Contracts-Skill](../../.claude/skills/api-contracts/SKILL.md),
[CLAUDE.md](../../CLAUDE.md), [Makefile](../../Makefile) und
[Gradle](../../build.gradle.kts). API-Pfade beginnen bereits mit `/api/v1`.

**Bekannte Lücken:** Auf der geprüften Basis fehlen ausgearbeitete ADRs/Muster in
`docs/architecture/`; deren Platzhalter sind keine Architekturentscheidung.
Fehlerformate sind gemischt, und die Versionsaussage im Skill ist veraltet;
Details stehen unten. Diese Richtlinie vereinheitlicht keine Laufzeitverträge.

## Contract workflow

**Regel / bestehender Ablauf:**

1. Im Ticket Methode, Pfad, Request-/Response-DTO, Fehler und Berechtigungen
   festhalten; Auswirkungen auf Web und Mobile prüfen.
2. Implementierung und Tests erstellen; tatsächlich serialisiertes JSON mit dem
   erzeugten Vertrag vergleichen.
3. Frischen Ops-Stand holen (`git fetch origin` im eigenen Ops-Worktree).
   Generierungsports 8089 und 15433 mit anderen Sitzungen koordinieren.
4. Aus dem Server-Worktree mit expliziten Pfaden generieren:

   ```bash
   OPS_DIR=/absolute/path/to/ops-worktree \
   OPENAPI_ENV_FILE=/absolute/path/to/private.env make openapi-sync
   ```

   [sync-openapi-to-ops.sh](../../scripts/sync-openapi-to-ops.sh) startet eine
   Wegwerf-DB auf 15433, generiert mit der Anwendung dieses Checkouts auf 8089
   und entfernt anschließend seine DB. Mit bereits laufender eigener Wegwerf-DB:

   ```bash
   ./gradlew syncOpenApiToOps \
     -PopsDir=/absolute/path/to/ops-worktree \
     -PopenApiEnvFile=/absolute/path/to/private.env \
     -PopenApiDbPort=15433
   ```

5. In Ops `git diff origin/main -- api/openapi.yaml` prüfen: nur erwartete Pfade,
   Schemas, Status, Nullbarkeit und Operation-IDs. Bei tatsächlicher Änderung
   Ops-PR mit Server-PR und HDN-Ticket verknüpfen und Übergabe für Clients im
   Ticket festhalten. Bei leerem Diff keinen leeren Sync-PR erzeugen.

**Ist-Stand / Grenzen:** Gradle verlangt einen vorhandenen Ops-Git-Checkout samt
Spec, private Env-Datei und expliziten DB-Port ungleich dem gemeinsamen Dev-Port
5433. Es ersetzt nur die generierte Server-URL durch den Vertragswert.
Ein Server-Commit startet keinen automatischen Sync; `openapi.yaml` niemals als
manuell bearbeiteten Endstand einchecken. API-Änderungen benötigen den erzeugten
Diff; reine Dokumentationsänderungen wie #367 nicht.

## DTOs and validation

**Regel:** JPA-Entities verlassen die Persistenzschicht nicht als REST-Payload.
Typisierte Request-/Response-DTOs und vorhandene Feature-Mapper verwenden.
Bean Validation am Request aktivieren (`@Valid`, passende Kotlin-Use-Site-Targets
wie `@field:NotBlank`); fachliche und objektbezogene Regeln im Service prüfen.
Keine rohen `Map`-/`Any`-Antworten für neue fachliche Verträge.

**Ist-Stand / Belege:** Der [API-Skill](../../.claude/skills/api-contracts/SKILL.md)
beschreibt Kotlin-DTOs; Beispiele liegen in den
[Ministry-DTOs](../../src/main/kotlin/com/hanmaum/dn/app/features/ministry/api/v1/dto/).
[OpenApiConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/OpenApiConfig.kt)
enthält bereits gezielte Nullability-Korrekturen. Kotlin-Nullbarkeit allein
beweist daher keinen korrekten OpenAPI-Vertrag.

**Prüfregel:** Ausgelassene Werte und explizites `null` bei PATCH getrennt
betrachten. Boolean-Namen im JSON, Enum-Nullwerte, Pflichtfelder und stabile
`operationId`s prüfen, insbesondere bei gleichnamigen Controller-Methoden.
Pagination mit mehr Treffern als Seitengröße testen: vollständige Sortierung,
Seitenschnitt, Metadaten und Anreicherung nur der benötigten Seite.
Siehe die entsprechenden [Lessons](../../tasks/lessons.md).

## Errors

**Regel / Best Practice:** HTTP-Status, Content-Type und Body sind zusammen der
Vertrag. Clients verzweigen auf dokumentierte maschinenlesbare Codes statt auf
Fehlerprosa. Keine Stacktraces, Tokens oder DB-Zeilendetails ausgeben. Nur den
bekannten Constraint in einen fachlichen Konflikt übersetzen, nicht jeden
Integritätsfehler als 409 behandeln. Spring unterstützt RFC 9457 über
[ProblemDetail](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

**Ist-Stand / Belege:** Zwei Formate bestehen nebeneinander:

| Pfad der Fehlerbehandlung | Format und Verhalten |
|---|---|
| [GlobalExceptionHandler](../../src/main/kotlin/com/hanmaum/dn/app/common/config/GlobalExceptionHandler.kt) mit [ErrorResponse](../../src/main/kotlin/com/hanmaum/dn/app/common/api/ErrorResponse.kt) | Eigenes JSON-DTO, üblicherweise `application/json`: `timestamp`, `status`, `message`, `error`, optionale `code`, `fieldErrors` und `conflictingDefinition`. Beispiele: 400 Validierung, 404 Ressource/Profil, 409 optimistischer Konflikt, 422 unvollständige Publikation. Nicht jeder Fehler besitzt einen Code oder eine Feldmap. |
| [SecurityConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/SecurityConfig.kt) / [SecurityProblemDetails](../../src/main/kotlin/com/hanmaum/dn/app/common/security/SecurityProblemDetails.kt) | 401/403 als `application/problem+json`, mit Status, Titel und sicherem Detail. Auch die AuthZ-Behandlung im globalen Advice gibt `ProblemDetail` zurück. |
| [GuideExceptionHandler](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/api/v1/GuideExceptionHandler.kt) | Feature-spezifisches `ProblemDetail`; fachlicher Status, 409 bei Versionskonflikt oder genau dem aktiven Datums-Unique-Constraint, sonst sicherer 500 bei Integritätsfehlern. |

Die Repo-Klasse `common.api.ErrorResponse` ist nicht Springs gleichnamiges
Interface. [ApiErrorCode](../../src/main/kotlin/com/hanmaum/dn/app/common/api/ApiErrorCode.kt)
definiert vorhandene fachliche Codes. Der allgemeine Validierungshandler liefert
400 mit Meldung, aber keine generelle `fieldErrors`-Map. Neue Feature-Designs
benennen das tatsächlich verwendete Format; eine repo-weite Umstellung braucht
einen eigenen abgestimmten Vertrag.

## Versioning

**Regel:** Entfernen/Umbenennen von Feldern oder Endpunkten, Typwechsel,
neue erforderliche Request-Felder und geänderte Fehlercode-Semantik auf
Client-Brüche prüfen. Breaking Changes gehen vor Umsetzung an Jin; bestehende
Consumer und koordinierte Migration gehören in die Entscheidung.

**Ist-Stand / Abweichung:**
[ApiPrefixConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/ApiPrefixConfig.kt)
setzt für Feature-Controller standardmäßig `/api/v1`; ebenso
[application.yml](../../src/main/resources/application.yml).
Die Skill-Aussage „unversioned during pre-1.0“ beschreibt diesen Stand nicht.
[OpenApiConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/OpenApiConfig.kt)
hat v1-/v2-Dokumentationsgruppen; die v2-Gruppe allein belegt keine implementierten
v2-Endpunkte. Bestehende Routen deshalb nicht umbenennen.

## Verification

**Regel:** Service-Verhalten, HTTP-Serialisierung/Validierung, Fehlerformat und
Berechtigungen passend zum Eingriff testen; ein Controller mit gemocktem Service
beweist keine Speicherung. Den generierten Vertrag einschließlich Nullbarkeit
prüfen und Änderungen gegen den frisch gefetchten Ops-Stand bewerten.
Testauswahl und Integrationstag siehe [Backend](backend.md#testing),
Objektberechtigungen siehe [Security](security.md#authorization).

**Ist-Stand:** Die [CI](../../.github/workflows/ci.yml) prüft Build, Lint, normale
Tests und ausgewählte Integrationstests. Sie öffnet keinen automatischen
Vertrags-PR. Prüfung von Links, Quellen und Markdown ist für #367 ausreichend;
keine OpenAPI-Erzeugung ohne API-Änderung.

## References

- [API-Contracts-Skill](../../.claude/skills/api-contracts/SKILL.md),
  [Projektregeln](../../CLAUDE.md), [Lessons](../../tasks/lessons.md).
- [Security: Authentication](security.md#authentication) und
  [Backend: Layers](backend.md#layers).
- [Feature-Design #367](https://github.com/SJinKim/hanmaum-dn-server/issues/367#technisches-design).
