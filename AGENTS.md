# AGENTS.md – hanmaum-dn-server

Projektregeln (Stack, Architektur, API-Vertrag, Migrationen, Keycloak) stehen in `CLAUDE.md` und gelten auch für Codex.

<!-- ai-dev-team:start -->
## KI-Entwicklungsteam

Dieses Repo wird vom KI-Entwicklungsteam bearbeitet (Claude A, Claude B, Codex; Entscheidungen: Jin).

- **Vor jeder Team-Aufgabe** den Skill `team-workflow` laden und `.team/config.yml` lesen.
- Projekt-Key: `HDN` · Board: GitHub Project #5 (SJinKim) · Rolle dieses Repos: backend
- Verwandte Repos: `SJinKim/hanmaum-dn-web-app`, `SJinKim/hanmaum-dn-mobile-app`, Vertrag: `hanmaum-dn-ops/api/openapi.yaml`
- Architektur: `docs/architecture/` (overview, ADRs, patterns) – **vor jeder Implementierung lesen; Abweichungen nur per Entscheidungsvorlage.**
- Produktentscheidungen: `docs/product/decisions.md` – gelten verbindlich.
- Designs: UI-Designs liegen bei Web und App · technische Feature-Designs in `docs/design/` bzw. im Issue.
- Erkenntnisse: `tasks/lessons.md` – vor der Arbeit überfliegen; `[lesson]`-Punkte aus Reviews hier eintragen.
- Arbeit nur in eigenen Worktrees unter `../.worktrees/` – nie im Haupt-Checkout.
- Merge-bereit = Label `ready-to-merge`; gemergt wird nur von Jin (außer `auto_merge: true`).
- Produkt- und wichtige Technikentscheidungen trifft **nur Jin** (Skill `team-decision`).
- Lücken in Story/Design nie erfinden – nachfragen.

### Review guidelines
Für das Codex-GitHub-Review dieses Repos:
- **P0/P1 (blockierend):** Sicherheitslücken (AuthN/AuthZ, IDOR, Injection, Secrets im Code/Log),
  Datenverlust, Verstöße gegen `docs/architecture/` (Schichten, Muster, ADRs), nicht erfüllte
  Akzeptanzkriterien des verlinkten Issues, fehlende Tests für neue Logik.
- **P2/P3:** Lesbarkeit, Fehlerbehandlung, Randfälle, kleinere Abweichungen.
- Keine Stil-Kommentare zu Dingen, die Linter prüfen.

### Befehle
- Build: `./gradlew build -x test` · Tests: `./gradlew test` (Testcontainers, Docker nötig) · Lint: `./gradlew ktlintCheck` · Architekturtests: – (noch keine)
- Lokal starten: `make up` (Docker Compose: Postgres, Keycloak, Backend)
- API geändert (Controller-Mapping oder DTO): `./gradlew syncOpenApiToOps` und ops-PR verlinken – `openapi.yaml` nie von Hand als Endstand.
<!-- ai-dev-team:end -->
