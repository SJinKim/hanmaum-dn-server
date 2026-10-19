# HDN-329 implementation plan (approved issue scope)
1. Complete: report schema, UUID CRUD, exact calendar previous-week delta, optimistic versioning, Guide roles/provisioning.
2. Complete: fixed six year blocks B:G/I, nullable missing weeks, cached XLSX values/CSV parity, provenance, dry-run, atomic idempotent commit and guarded rollback.
3. Complete: 1,145 unit/controller tests, six PostgreSQL integration tests, five parser tests including original-source parity; lint/build and ten realm-reconciliation tests pass. Original workbook and shared databases unchanged.
4. Complete: OpenAPI generated into isolated ops worktree, explicit nullable types and stable operation IDs reviewed; fresh full-migration application startup verified.
5. Delivered for review: server PR #335 and ops PR #63, board In Review, claim retained. User controls merge. Live Keycloak rollout/ST import remain pending. Subsequent dependent issues sequentially after base available.
