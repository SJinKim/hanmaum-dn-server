# Guide summary import — HDN-329

The importer is disabled by default. It is an explicit maintenance application
runner, without an HTTP endpoint. Merging this code does not import historical
data or provision an existing Keycloak realm. Rehearse against a disposable or
staging database before an explicitly approved live import.

## Approved mapping (`summary-main-v1`)

Read **Summary B:G and I only**, through row 264. Exclude H, K:X, Y, all other
right-hand tables, weekly detail sheets and statistics beginning at row 267.
Use cached XLSX values without recalculating formulas. CSV is the same inventory,
UTF-8, semicolon-separated, with German thousands separators; an accounting dash
represents numeric zero. Preserve notes, Unicode and line breaks. `특별예배`
becomes **특이사항** (`notes`). Counts are nullable nonnegative int32 values;
totals use int64. NULL means not recorded, including all three counts in rows
24–27, even where the source displays zero.

| Year | Header | Data rows | Recorded | Annual total |
|---|---:|---|---:|---:|
| 2021 | 4 | 5–8 | 4 | 1,657 |
| 2022 | 9 | 10–61 | 48 | 22,774 |
| 2023 | 62 | 63–114 | 52 | 26,342 |
| 2024 | 116 | 117–168 | 52 | 37,205 |
| 2025 | 169 | 170–221 | 52 | 40,340 |
| 2026 | 222 | 223–264 | 42 | 31,435 |

The explicit user override makes header 116 **2024**, regardless of its source
label. Month/day comes from B; the year comes from this approved block. Do not
move historical dates to a different weekday. Source dates, allowed values and
formulas are retained in the import ledger. No room distribution is invented.
All imported reports expose `reportMode=SUMMARY_ONLY`.

F must match C+D+E for complete reports; it is derived on reads thereafter.
G is provenance, not a target field: the API computes the difference to the
active report **exactly seven days earlier**. If either report is missing or
incomplete, the delta is NULL. This corrects G262 to −93; do not carry over the
source's incorrect cell reference. Date normalization and delta differences
appear as row-number warnings, without notes or source values in the log.

Expected inventory: **254 reports, 250 recorded, 4 unrecorded, 116 notes**.
Service sums: **41,317 / 81,757 / 36,679**, combined **159,753 visits**.
The importer validates these totals and the annual totals before any write.

Approved original file SHA-256:

```text
XLSX 9163e7f85c3196bac09c11aac4273f76966665eb8f5378d07cd3edaf90a292e7
CSV  2ca5f6dc17b52c9fb3cb0bb74408906604364d2fb1151300462813efa54ba894
```

## Rehearse and import

1. Deploy the reviewed artifact and migrations to the chosen environment. Verify
   the database target, take a restorable database backup, record the commit and
   source hash, and pause competing Guide writes for the maintenance window.
   Keep the source outside Git and unchanged. Load that environment's usual
   datasource, PII and Keycloak settings without copying credentials between
   environments. Bind the maintenance process only to loopback on an unused
   port; avoid running another application instance against the wrong database.
2. Start the built application with the following additional arguments, replacing
   the jar and source paths. The normal server remains running after the result
   is logged; stop this maintenance process after reviewing the result.

   ```bash
   java -jar /absolute/path/to/server.jar \
     --server.address=127.0.0.1 --server.port=18089 \
     --app.guide-import.enabled=true \
     --app.guide-import.file=/absolute/path/to/source.xlsx \
     --app.guide-import.dry-run=true
   ```

   Require `read=254`, `recorded=250`, `notes=116`, the three service sums above,
   and no conflicts. Dry-run creates no reports or batches. Inspect warnings
   against the approved rules; never resolve them using excluded columns.
3. After reviewing that exact hash, restart with the same file and arguments,
   replacing `--app.guide-import.dry-run=true` with both:

   ```text
   --app.guide-import.dry-run=false
   --app.guide-import.expected-sha256=<reviewed dry-run hash>
   ```

   Record the returned batch UUID. The entire batch commits atomically. A hash
   mismatch, source validation failure or existing-date conflict aborts it.
   Do not enable these flags permanently in deployment configuration.
4. Verify report/notes counts and annual/service sums in the target database;
   check 2026-10-18 totals 186/384/146=716 and delta +63, the four NULL reports,
   and row 262 delta −93. Repeat dry-run: expect `new=0`, `skipped=254` and no
   conflicts. CSV and XLSX canonical rows are identical, so either repeated
   source is skipped; they are never imported as two datasets.

The ledger stores source SHA-256, mapping version, original row, permitted source
values/formulas and canonical fingerprint. A repeated import skips only
unchanged, active rows with the original version. An edited, moved, deleted or
manually existing report produces a conflict; the importer never overwrites it.

## Guarded rollback

Use the same environment and artifact, **omit** `app.guide-import.file`, and set:

```text
--app.guide-import.enabled=true
--app.guide-import.rollback-batch=<recorded batch UUID>
--app.guide-import.dry-run=true
```

Review the number of affected reports, then explicitly run again with
`dry-run=false`. Rollback locks and checks every report first. If any report was
edited or deleted, the entire rollback is refused. Otherwise it soft-deletes
only unchanged reports belonging to that batch and retains the audit ledger.
Repeating rollback returns zero. Reimporting a rolled-back batch requires a
separately reviewed recovery plan because its ledger remains; do not remove
ledger rows to bypass this protection. A restored backup is the fallback for a
rollback that cannot safely run.

## API and access

`/api/v1/guide/worship-counts`: paged GET (optional `from`, `to`, zero-based `page`,
`size` 1–200, `sort=serviceDate,asc|desc`), GET by UUID, POST, PATCH by UUID and
DELETE by UUID with expected `version`. PATCH replaces all editable summary
fields and requires the returned version. Stale versions or duplicate active
dates return 409. Missing/deleted UUIDs return 404. Counts accept integer JSON
numbers or NULL only; strings, decimals, exponents, negatives and overflow return
400. Ordinary member access is denied.

`GUIDE_VIEWER` reads; `GUIDE_EDITOR` reads and writes through the server role
hierarchy. Admins and pastors have both. Both realm exports include the roles
and `/ministries/guide/viewers` and `/ministries/guide/editors` groups. No user
assignment is created. Follow [Keycloak rollout](KEYCLOAK_RUNBOOK.md#staff-and-leader-roles-and-groups)
for existing staging and production realms; require zero changes on the second
apply and fresh-token read/write checks before enabling the UI. Checked-in
configuration and automated mock reconciliation tests are not live rollout
evidence.

## Local verification without committing private fixtures

Run the focused Guide tests against a disposable PostgreSQL database with
`-PincludeIntegration`. Set `GUIDE_IMPORT_REAL_SOURCE_DIR` to the original files'
directory to additionally verify actual-source parity, full import, retry and
rollback. Test transactions roll back. Leave the variable unset in CI: synthetic
fixtures cover parser/CRUD/permissions/conflicts and rollback independently.
