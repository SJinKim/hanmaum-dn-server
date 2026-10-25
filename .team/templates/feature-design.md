# Technisches Design: <KEY>-<nr> <Titel>

**Story:** #<nr> · **Figma:** <Frame-Links oder „keine UI“> · **Architekt:** Codex · **Stand:** YYYY-MM-DD

## Ziel in einem Satz

## Betroffene Repos und Module
| Repo | Modul / Schicht | Änderung |
|---|---|---|

## Datenmodell
- Neue/geänderte Tabellen und Spalten (Typ, Null, Default, Index, FK)
- Migration: `V<n>__<beschreibung>.sql` – umkehrbar? Datenmigration nötig?

```mermaid
erDiagram
```

## API-Vertrag
| Methode | Pfad | Rolle/Berechtigung | Request | Response | Fehler |
|---|---|---|---|---|---|

OpenAPI-Ausschnitt oder Verweis auf die Spezifikationsdatei.

## Backend
- Schichten nach `docs/architecture/patterns/backend.md` (Controller → UseCase → Port → Adapter …)
- Validierung, Berechtigungsprüfung (auch auf Objektebene), Transaktionen, Events

## Web (Angular)
- Komponenten-Schnitt (Smart/Dumb), Services, State (Signals), Routen, Formulare
- Verwendete PrimeNG-Komponenten laut Figma

## App (KMP)
- ViewModel, UiState, Repository, was in `commonMain`, was plattformspezifisch

## Sicherheit
- AuthN/AuthZ, Eingabevalidierung, Missbrauch, sensible Daten

## Tests
- Unit: …
- Integration: …
- E2E (Nutzerfluss): …
- Architekturregeln betroffen?

## Aufteilung in Umsetzungsschritte / Sub-Issues
1. … (Repo, Größe, empfohlener Agent)

## Offene Punkte
Nichts erfinden: offene Fragen hier auflisten → ggf. Entscheidungsvorlage.
