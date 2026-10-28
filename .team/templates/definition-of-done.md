# Definition of Done

Ein Ticket ist erst `Done`, wenn alle zutreffenden Punkte erfüllt sind.

## Code
- [ ] Alle Akzeptanzkriterien erfüllt
- [ ] Implementiert gemäß Feature-Design, ADRs und `docs/architecture/patterns/`; Abweichungen im PR begründet
- [ ] Keine nicht freigegebene Stufe-1/2-Entscheidung im Code
- [ ] Build, Lint, alle Tests und Architekturtests grün (CI)
- [ ] Keine Secrets, keine neuen Warnungen im Security-/Dependency-Scan

## Tests (schreibt der Developer)
- [ ] Unit-Tests für neue Logik
- [ ] Integrationstests für neue Endpunkte / Persistenz
- [ ] E2E-Test für jeden neuen oder geänderten Nutzerfluss
- [ ] Regressionstest für jeden behobenen Bug

## Qualität
- [ ] Code-Review bestanden (Review-Matrix)
- [ ] QA-Abnahme bestanden (`size:M` und `ui`)
- [ ] Design-QA bestanden (`ui`)

## Doku
- [ ] API-Doku / OpenAPI aktualisiert
- [ ] README / Betriebsdoku bei neuen Komponenten oder Konfiguration
- [ ] ADR bzw. `docs/product/decisions.md` bei getroffenen Entscheidungen
- [ ] `[lesson]`-Punkte aus Review/QA in `tasks/lessons.md` eingetragen
