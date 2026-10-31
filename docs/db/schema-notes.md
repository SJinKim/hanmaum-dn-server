# Fachliche Notizen zum DB-Schema

Ein Abschnitt `## <schema>.<tabelle>` pro Tabelle; der Inhalt landet unverändert in `schema.md`.
Format und Regeln: `docs/db/README.md`.

## public.attendance_absence_changes

Kontakt- und Memo-Verlauf einer 장기결석자-Phase (HDN-379). Jeder Eintrag hält den **neuen** Wert;
aufeinanderfolgende `MEMO_CHANGED`-Einträge sind die Memo-Versionen. `revision` ist der
Konfliktzähler des Mitglieds beim Schreiben. `origin = LEGACY_SNAPSHOT` ist der bei Einführung
übernommene Stand ohne bekannte Memo-Zeit/-Autorschaft. `actor_subject` und `memo` sind
verschlüsselt. Memo leeren löscht alle Memo-Versionen der Phase hart; 90 Tage nach Phasenende
ebenso (#380).

## public.attendance_absence_followups

Nachverfolgung der 장기결석자 (HDN-375, HDN-379): eine Zeile je gespeicherter Abwesenheitsphase,
mit Kontaktstatus und Memo. Die Phase zählt ab `absence_anchor_date`, dem späteren Datum aus
letztem zählenden Sonntags-Check-in (`last_sunday_date`) und manueller Rückkehr; `phase_generation`
unterscheidet Phasen mit gleichem `last_sunday_date`. Die Liste selbst wird aus `attendance_logs`
berechnet. `memo`, `contacted_by` und `returned_by` sind verschlüsselt. Eine abgeschlossene Phase
(`closed_on`, `end_reason`) wird nie wieder geöffnet; Memo und Memo-Versionen werden 90 Tage nach
`closed_on` gelöscht, die Phase nach zehn Jahren (#380).

## public.members

`keycloak_id` ist der `sub`-Claim des Keycloak-JWT und verknüpft das Login mit dem Mitglied
(Anlage beim ersten Login).

`absence_manual_return_on` ist die manuelle Rückkehr, ab der die laufende Abwesenheit zählt;
`absence_revision` der monotone Konfliktzähler der 장기결석자-Nachverfolgung (Wire-`version`),
der nie zurückgesetzt wird (HDN-379).
