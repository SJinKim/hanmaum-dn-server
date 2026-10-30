# Produktentscheidungen

Verbindliche Produktentscheidungen von Jin. Alle Agenten lesen diese Datei vor Refinement,
Design und Implementierung. Technische Entscheidungen stehen als ADR in `docs/architecture/adr/`.

Neue Einträge oben anfügen.

| Datum | Frage | Entscheidung | Begründung (kurz) | Issue |
|---|---|---|---|---|
| 2026-10-30 | Aufbewahrung von Memo-Versionen und Abwesenheitshistorie (Langzeit-Abwesende)? | Memo und alle früheren Memo-Versionen werden 90 Tage nach Phasenende hart gelöscht (wie #376). Die Phase bleibt: Zeitraum, Status, Actor und Rückkehrzeit (Ansicht ohne Memo) sind historische Daten und werden 10 Jahre aufbewahrt. Das Memo ist optional: Wird es in einer laufenden Phase gelöscht, verschwinden Text und alle früheren Versionen sofort; die Ansicht zeigt ein leeres Memo, ohne Hinweis „Memo gelöscht“ und ohne eigenen Verlaufseintrag. Phasen-Metadaten bleiben davon unberührt. | Freitext ist heikel, Verlauf ohne Text bleibt für Nachverfolgung nutzbar. Datenschutzregel der Gemeinde (이찬규 목사님) noch nicht geklärt. | #380 |
| YYYY-MM-DD | Beispiel: Push-Benachrichtigungen im MVP? | Nein, nur In-App; Push in v1.1 | Aufwand Store/APNs, MVP-Fokus | #0 |
