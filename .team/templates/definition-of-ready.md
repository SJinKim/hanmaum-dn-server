# Definition of Ready

Ein Ticket darf erst auf `Ready`, wenn alle zutreffenden Punkte erfüllt sind. Auf `Ready` setzt nur Jin.

## Alle Tickets
- [ ] Titel und Beschreibung verständlich ohne Vorwissen
- [ ] Story-Format „Als <Rolle> möchte ich <Ziel>, damit <Nutzen>“ (Stories) bzw. Reproduktionsschritte (Bugs)
- [ ] Akzeptanzkriterien testbar (Gegeben / Wenn / Dann), inkl. Fehler- und Randfälle
- [ ] Größe `size:S|M` gesetzt (`size:L` wird nicht umgesetzt, sondern zerlegt)
- [ ] Keine offenen `decision-needed`, keine unklaren Abhängigkeiten
- [ ] Betroffene Repos benannt

## Zusätzlich `size:M`
- [ ] Technisches Design im Issue oder `docs/design/<KEY>-<nr>.md`
- [ ] Datenmodell- und API-Änderungen beschrieben

## Zusätzlich `ui`
- [ ] Figma-Frames verlinkt (Web und/oder App), alle Zustände entworfen
- [ ] Design von Jin freigegeben
