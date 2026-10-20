# HDN-329 implementation plan (approved issue scope)
1. Complete: report schema, UUID CRUD, exact calendar previous-week delta, optimistic versioning, Guide roles/provisioning.
2. Complete: fixed six year blocks B:G/I, nullable missing weeks, cached XLSX values/CSV parity, provenance, dry-run, atomic idempotent commit and guarded rollback.
3. Complete: 1,145 unit/controller tests, six PostgreSQL integration tests, five parser tests including original-source parity; lint/build and ten realm-reconciliation tests pass. Original workbook and shared databases unchanged.
4. Complete: OpenAPI generated into isolated ops worktree, explicit nullable types and stable operation IDs reviewed; fresh full-migration application startup verified.
5. Delivered for review: server PR #335 and ops PR #63, board In Review, claim retained. User controls merge. Live Keycloak rollout/ST import remain pending. Subsequent dependent issues sequentially after base available.

## Review follow-up (user authorized fixes and merge)
1. Complete: page delta bulk lookup, targeted integrity translation, XLSX/CSV number parsing and shared nullable totals, with regressions.
2. Complete: explicit guide-import profile, shutdown after all startup runners, ordinary dev/staging/prod profiles refused; packaged process dry-run exits 0, bad hash exits 1 with no Guide writes in disposable DB.
3. Complete: rollback/deletion tombstones intentionally retained, tested against retry resurrection. Contract regenerated and semantically identical to ops PR #63.
4. Local gates pass: 1,154 unit tests, lint/build; focused PostgreSQL/parser tests pass. Review replies and exact-head CI precede user-authorized server #335 / ops #63 merges. No deployment or live import.
