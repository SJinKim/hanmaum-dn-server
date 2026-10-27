# Fachliche Notizen zum DB-Schema

Ein Abschnitt `## <schema>.<tabelle>` pro Tabelle; der Inhalt landet unverändert in `schema.md`.
Format und Regeln: `docs/db/README.md`.

## public.members

`keycloak_id` ist der `sub`-Claim des Keycloak-JWT und verknüpft das Login mit dem Mitglied
(Anlage beim ersten Login).
