# Produktentscheidungen

Verbindliche Produktentscheidungen von Jin. Alle Agenten lesen diese Datei vor Refinement,
Design und Implementierung. Technische Entscheidungen stehen als ADR in `docs/architecture/adr/`.

Neue Einträge oben anfügen.

| Datum | Frage | Entscheidung | Begründung (kurz) | Issue |
|---|---|---|---|---|
| 2026-10-29 | 장기결석자 (HDN-375): Auswahl und Memo-Datenschutz | **F1 A:** Person mit `CHANGED_CHURCH`/`RETURNED_OR_MOVED` am früheren Newcomer-Profil wird ausgeschlossen, übrige Werte nur angezeigt. **F2 A:** Nur belegbare Zeiten zählen (benannte 2부/3부 immer; andere Logs nur bei gleichem Erfassungs-/Besuchsdatum Berlin und Sonntag 08:00–15:00 inkl. Grenzen). **F3 A:** Memo bleibt, Eingabehinweis „nur organisatorische Rückmeldung, keine Diagnosen oder Gesundheitsdetails“, verschlüsselt. **Frist:** Memo einer beendeten Phase wird nach 90 Tagen hart gelöscht; laufende Phase bleibt; bei Austritt/INACTIVE oder Hard Delete des Mitglieds mit dem Mitglied gelöscht; Soft Delete/Wiederherstellung folgt dem Mitglied. Kein neues Lese-Audit im MVP. **Rahmen:** Es gilt kirchliche Datenschutzregelung (kirchenrechtlich), Ansprechperson 이찬규 목사님; der Hinweis an Mitglieder nennt das Memo bereits. **Offen:** Backup-Rotationszyklus benennen; Bestätigung durch Datenschutzfachleute steht aus (keine Rechtsberatung). | Zweck ist aktuelle Nachverfolgung, keine Historie; Art. 9 DSGVO-Risiko durch Eingabehinweis und Minimierung begrenzt | #376, #375 |
| YYYY-MM-DD | Beispiel: Push-Benachrichtigungen im MVP? | Nein, nur In-App; Push in v1.1 | Aufwand Store/APNs, MVP-Fokus | #0 |
