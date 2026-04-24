---
name: db-migrations
description: Use when creating, modifying, or reviewing Flyway database migrations for this PostgreSQL backend
---

# Flyway migration rules

## Location and naming
- All migrations: `src/main/resources/db/migration/`
- Versioned: `V<YYYYMMDDHHMM>__<snake_case_description>.sql`
  Example: `V202604151030__add_keycloak_id_to_members.sql`
- Repeatable (views, functions): `R__<snake_case_description>.sql`

## Versioning rules
- Never edit a migration that has been applied to any shared environment.
- If a deployed migration is buggy, fix forward with a new migration.
- Same-day collisions: distinct minute timestamps; second author rebases.

## Safety rules for live-DB migrations
- **Adding a column**: nullable or with a default. Never `NOT NULL` without a
  default in one step.
- **Renaming a column**: three migrations across three releases:
  1. Add new column, copy data, keep old column.
  2. Application reads both, writes both.
  3. Drop old column only after old code is fully retired.
- **Dropping a column or table**: verify zero code references first. Ship the
  code change, then the migration.
- **Indexing a large table**: `CREATE INDEX CONCURRENTLY` in its own migration
  (Flyway runs it outside a transaction).
- **Data migrations**: idempotent. Use `WHERE NOT EXISTS` or `ON CONFLICT`.

## Required in every migration file
- Header comment with ticket ID: `-- HDN-<id>: <short description>`
- One logical change per file. Adding a table + seeding it = two migrations.
- No `DROP TABLE IF EXISTS` as an opening line — masks real problems.
- No Flyway `-- callback:` markers without explicit review.

## Verification before committing
Run the full chain against a fresh Postgres:
`docker compose down -v && docker compose up -d postgres && ./gradlew flywayMigrate`

For migrations touching >10k rows on a real table: comment the estimated runtime.

## When in doubt
- Two small reversible migrations beat one big clever one.
- Non-trivial migration → flag the PR with label `db-migration-review`.
