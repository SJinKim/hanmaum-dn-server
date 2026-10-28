# Security

## Scope

Referenz für Authentifizierung, Berechtigungen und personenbezogene Daten.
Geprüft am 27.10.2026 gegen `ca378cbe581557da98717eb61f167461765a6fcc`.
H2-Überschriften bleiben für Feature-Designs stabil, insbesondere
[Authorization](#authorization). Diese Datei führt keine neuen Rechte oder
Aufbewahrungsregeln ein.

## Current state

**Ist-Stand / Belege:** Keycloak verwaltet Identitäten; der Server ist ein
stateless OAuth2 Resource Server mit JWT und Methodensicherheit. Quellen sind
[SecurityConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/SecurityConfig.kt),
[Keycloak Environments](../../KEYCLOAK_ENVIRONMENTS.md),
[Runbook](../../KEYCLOAK_RUNBOOK.md) und der
[Auth-Skill](../../.claude/skills/auth-keycloak/SKILL.md).
Der Token-Subject verknüpft Identität und Member; Zugangsdaten gehören nicht in
Anwendungsdaten oder Logs.

**Bekannte Lücken:** Es gibt auf der geprüften Basis keine ausgearbeiteten ADRs
oder Architektur-Muster unter `docs/architecture/`. Ältere Abschnitte im Auth-Skill
beschreiben eine automatische Erstanlage im Filter; sie sind kein aktueller
Implementierungsbeleg. Tatsächlich bündelt
[CurrentMemberResolver](../../src/main/kotlin/com/hanmaum/dn/app/features/members/service/CurrentMemberResolver.kt)
die Auflösung und kontrollierte Verknüpfung vorhandener Registrierungen.
`require` schlägt ohne Profil fehl; `resolveAndLink` verlangt eine bestehende
Registrierung und prüft verifizierte E-Mail/Identitätsübereinstimmung.
Keine pauschale automatische Profilerzeugung oder Vollständigkeit aller Claims
aus der alten Skill-Beschreibung ableiten.

## Authentication

**Regel / bestehendes Muster:** JWT nicht selbst dekodieren oder aus ungeprüften
Claims Identität ableiten. Spring Security validiert vor geschützten Operationen.
Umgebungsgrenzen aus der [Realm-/Issuer-Matrix](../../KEYCLOAK_ENVIRONMENTS.md#matrix)
übernehmen; gleiche Audience allein trennt ST und Prod nicht.

**Ist-Stand / Belege:**
[SecurityConfig](../../src/main/kotlin/com/hanmaum/dn/app/common/config/SecurityConfig.kt)
verifiziert die Signatur über den konfigurierten JWK-Endpunkt.
[AccessTokenValidator](../../src/main/kotlin/com/hanmaum/dn/app/common/security/AccessTokenValidator.kt)
prüft Zeitgültigkeit (`exp`/`nbf`), erlaubten Issuer und API-Audience. Beim
`typ`-Claim akzeptiert der aktuelle Code `Bearer` **oder ein fehlendes Claim**;
explizite andere Werte wie `ID` und `Refresh` werden abgewiesen. Das ist keine
zusätzliche Pflichtprüfung auf Vorhandensein von `typ`.
Leere Issuer-/Audience-Konfiguration verhindert den Start.

Öffentliche Feature-Operationen stehen explizit in
[PublicApiRoutes](../../src/main/kotlin/com/hanmaum/dn/app/common/config/PublicApiRoutes.kt)
und werden auch im OpenAPI als öffentlich markiert. Weitere Ausnahmen für
Actuator/Dokumentation stehen in `SecurityConfig`; ihre externe Erreichbarkeit
hängt zusätzlich von Profil und Proxy ab. Nicht aus `permitAll` eine
beabsichtigte öffentliche Prometheus-Freigabe ableiten. Für neue Ausnahmen den
gesamten Pfad und Schutzmechanismus prüfen, siehe [PUBLIC_ROUTES.md](../../PUBLIC_ROUTES.md).
Fehlende/ungültige Authentifizierung ergibt an geschützten Endpunkten 401,
unzureichende Berechtigung bei authentifiziertem Aufruf 403; Format siehe
[API Errors](api.md#errors).

## Authorization

**Regel:** Realm-Rolle, Rollenvererbung und Objektzugriff gemeinsam prüfen.
Restriktive Controller-Defaults erhalten, breitere Lesezugriffe explizit
freigeben. Ein neuer Handler darf nicht durch fehlende Annotation unbeabsichtigt
Schreibrechte erhalten. Selbstzugriffe leiten ihren Subject aus dem validierten
JWT ab; eine vom Client gelieferte Member-ID beweist keine Eigentümerschaft.

**Ist-Stand / Belege:**
[Roles.HIERARCHY](../../src/main/kotlin/com/hanmaum/dn/app/common/security/Roles.kt)
vererbt PASTOR nach ADMIN sowie ausgewählte Editor- nach Viewer-Rechte.
`SecurityConfig` erweitert Authorities auch für programmatische Prüfungen.
Einzelne Rollen daher einschließlich ihrer vererbten Rechte testen, bevor ein
fehlender Zugriff behauptet wird.

[MinistryOwnership](../../src/main/kotlin/com/hanmaum/dn/app/features/ministry/service/MinistryOwnership.kt)
zeigt die Objektprüfung: `MINISTRY_LEADER` sagt nur, dass jemand ein Ministry
leitet. Schreibzugriff auf ein konkretes Ministry verlangt dessen aktuelle
`LEADER`-Zuordnung, sofern keine ausdrücklich berechtigte Umgehung greift.
`SUB_LEADER` ist dafür kein Eigentümer. Der aktuelle Parameter heißt
`bypassOwnership`; den erlaubten Bypass nach tatsächlicher Berechtigung prüfen,
nicht pauschal nach einem vermuteten Admin-Flag.

**Prüfregel:** Den ganzen Nutzerfluss einschließlich Member-Picker und Lookups
verfolgen. Ein erlaubter Speichervorgang ist unbenutzbar, wenn die Rolle die
benötigte Auswahlliste nicht lesen darf. Dabei nur die benötigten Felder
freigeben; keine administrative Detailansicht aus Bequemlichkeit öffnen.
Beispiele und Rechte stehen im [Runbook](../../KEYCLOAK_RUNBOOK.md#staff-and-leader-roles-and-groups).

## PII and logging

**Regel:** Keine Tokens, Secrets, personenbezogenen Freitexte oder vollständigen
JWTs loggen. Auch Exception-Meldungen und PostgreSQL-Details können
personenbezogene Inhalte enthalten. SLF4J mit Parametern und sicheren
technischen Diagnosen wie Operation, Fehlercode und SQL-State verwenden.
DTOs auf die für die erlaubte Aktion benötigten Daten begrenzen; keine neuen
Speicher- oder Aufbewahrungsfristen aus dieser Richtlinie ableiten.

**Ist-Stand / Belege:**
[PiiAttributeConverters](../../src/main/kotlin/com/hanmaum/dn/app/common/pii/PiiAttributeConverters.kt)
und [PiiCryptoService](../../src/main/kotlin/com/hanmaum/dn/app/common/pii/PiiCryptoService.kt)
verschlüsseln Felder mit AES-GCM und zufälliger Nonce, spaltengebundenen
Associated Data und separaten Lookup-Hashes. Zufälliger Ciphertext eignet sich
nicht für fachliche SQL-Gleichheit, Sortierung, `LIKE` oder Gruppierung.
Vorhandene normalisierte Hash-Suchpfade beziehungsweise autorisierte
Anwendungsverarbeitung verwenden; Normalisierungssemantik beibehalten und bei
Pagination die vollständige Sortierung berücksichtigen.

Der [GuideExceptionHandler](../../src/main/kotlin/com/hanmaum/dn/app/features/guide/api/v1/GuideExceptionHandler.kt)
loggt bei Integritätsfehlern bewusst nur den SQL-State und liefert sichere
Details. Das ist ein lokaler Beleg, keine Aussage, dass bereits jeder bestehende
Handler gefahrlos loggt. [CLAUDE.md](../../CLAUDE.md) und
[Lessons](../../tasks/lessons.md) bleiben die übergreifenden Regeln.

## Realm rollout

**Regel / bestehender Ablauf:** Rollen, Gruppen und Gruppen-Rollen-Mappings werden
im selben Feature-PR in beiden
[Realm-Exports](../../infrastructure/docker/keycloak/export/) und im idempotenten
Pfad für bestehende Realms gepflegt. Der
[Reconciler](../../scripts/keycloak/configure-realm.sh) liest die verwalteten
Definitionen aus den Exports; Exportänderungen allein aktualisieren keinen
bestehenden Realm. Unabhängige Realm-Konfiguration und Nutzerzuordnungen erhalten,
keine Personen automatisch privilegierten Gruppen zuweisen.

**Ist-Stand / Belege:** Der [Auth-Skill](../../.claude/skills/auth-keycloak/SKILL.md)
und das [Runbook](../../KEYCLOAK_RUNBOOK.md#staff-and-leader-roles-and-groups)
beschreiben Dry Run als Standard, explizites `APPLY=1`, erneute Anwendung ohne
Duplikate und abschließenden Dry Run ohne Änderungen. Erst ST prüfen, danach
Prod, soweit der konkrete Auftrag den Rollout umfasst. Rollen, Gruppenpfade,
Leaf-Mappings und effektive API-Rechte mit erneuerten Tokens verifizieren.

Merge, Backend-Deployment, Realm-Anwendung und verifizierter Live-Stand getrennt
berichten. Ein Backend-Deploy kopiert den Reconciler, führt ihn aber nicht aus.
Zielrealm und freigegebenen Anwendungsbefehl im Rollout festhalten; die Richtlinie
selbst autorisiert keinen Live-Eingriff. Ausführliche Kommandos und Umgebungswahl
stehen im [Environment-Runbook](../../KEYCLOAK_ENVIRONMENTS.md#rollout-staging-first-then-prod).

## Verification

**Regel:** Authentifizierung und Autorisierung getrennt belegen: fehlender oder
ungültiger Token, falscher Issuer/Audience/Token-Art, gültiger Zugriff,
unzureichende Rolle, Rollenvererbung und fremdes Objekt. HTTP-Slices mit einem
Test-JWT umgehen den echten Decoder und beweisen daher keine Signaturprüfung.
Den tatsächlichen Realm-/Tokenpfad bei Auth-Änderungen zusätzlich integrieren.

**Ist-Stand / Belege:**
[SecurityConfigTest](../../src/test/kotlin/com/hanmaum/dn/app/common/config/SecurityConfigTest.kt),
[MinistryMemberControllerTest](../../src/test/kotlin/com/hanmaum/dn/app/features/ministry/api/v1/MinistryMemberControllerTest.kt)
und [MinistryOwnershipIT](../../src/test/kotlin/com/hanmaum/dn/app/features/ministry/service/MinistryOwnershipIT.kt)
zeigen unterschiedliche Testgrenzen. Realm-Reconciliation wird durch
[test-realm-access.py](../../scripts/keycloak/test-realm-access.py) und
[test-realm-access-integration.py](../../scripts/keycloak/test-realm-access-integration.py)
geprüft; beide laufen in der [CI](../../.github/workflows/ci.yml).
Befehle und Integrationstag siehe [Backend Testing](backend.md#testing).

## References

- [KEYCLOAK_ENVIRONMENTS.md](../../KEYCLOAK_ENVIRONMENTS.md),
  [KEYCLOAK_RUNBOOK.md](../../KEYCLOAK_RUNBOOK.md),
  [Auth-Skill](../../.claude/skills/auth-keycloak/SKILL.md).
- [API Errors](api.md#errors), [PostgreSQL Normalization](postgresql.md#normalization).
- [Feature-Design #367](https://github.com/SJinKim/hanmaum-dn-server/issues/367#technisches-design).
