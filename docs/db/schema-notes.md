# Fachliche Notizen zum DB-Schema

Ein Abschnitt `## <schema>.<tabelle>` pro Tabelle; der Inhalt landet unverändert in `schema.md`.
Format und Regeln: `docs/db/README.md`.

## public.attendance_absence_followups

Nachverfolgung der 장기결석자 (HDN-375): Kontaktstatus und Memo je Mitglied und Abwesenheitsphase.
Die Phase ist `(member_id, last_sunday_date)`, das Datum des letzten zählenden Sonntags-Check-ins;
die Liste selbst wird aus `attendance_logs` berechnet. `memo` und `contacted_by` sind verschlüsselt.
`closed_on` ist die erste Rückkehr nach dem Anker; der Cleanup-Job löscht die Zeile 90 Tage danach hart.

## public.members

`keycloak_id` ist der `sub`-Claim des Keycloak-JWT und verknüpft das Login mit dem Mitglied
(Anlage beim ersten Login).
