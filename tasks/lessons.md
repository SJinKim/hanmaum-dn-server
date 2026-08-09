# Lessons Learned

> Updated after every user correction. Reviewed at session start.

## Format
- **Mistake**: what went wrong
- **Rule**: what to do instead (permanent prevention)

## Lessons
<!-- Claude appends here after each correction -->

### Deploy order: staging before production
- **Mistake**: Promoting to production without verifying staging first. (Originally phrased as "push to `dev` first" — the `dev` branch was retired in #104, but the staging-before-prod requirement stands.)
- **Rule**: Deploys run as manual `workflow_dispatch` on `main` only. Merge the feature branch to `main`, run the staging pipeline, and verify `dn-admin-dashboard.st.graceops.de` before running the production pipeline for `dn-admin-dashboard.graceops.de`. Never skip the staging step.

### Respect repository scope
- **Mistake**: Interpreted a server change as an end-to-end request and modified the mobile repository without being asked.
- **Rule**: When the user asks to adjust behavior in a specific repository, change only that repository unless cross-repository work is explicitly requested.

### Model repeatable contacts as a collection
- **Mistake**: Modeled ministry contacts as fixed leader/coordinator fields even though contact roles can vary and grow.
- **Rule**: For repeatable role-to-person data, use an ordered collection of `{role, name}` objects instead of role-specific columns or DTO fields.

### Separate undecided infrastructure choices
- **Mistake**: Mixed a provider-specific pCloud implementation into the PII/security branch before the storage architecture had been decided.
- **Rule**: Keep unresolved storage-provider work out of unrelated security changes. Decide the abstraction and provider first, then implement object storage in a separate branch and PR.

### Preserve internal audit timestamps unless retention requires removal
- **Mistake**: Recommended removing exact attendance timestamps even though they may have future operational value and are not exposed by the API.
- **Rule**: Keep inexpensive internal audit timestamps by default; enforce data minimization at DTO/API boundaries and remove stored metadata only when a concrete privacy or retention requirement calls for it.

### Keep attendance availability definition-driven
- **Mistake**: Described button availability as if it needed a separate status mechanism.
- **Rule**: Treat `attendance_definitions` (`dayOfWeek`, `windowStart`, `windowEnd`, `isActive`) as the single source of truth for attendance-button availability; derived API state must not introduce separate mutable availability data.

### Separate attendance deduplication from user-facing tracking
- **Mistake**: Planned individual attendance logs and member statistics before stakeholders had agreed that person-level reporting was necessary.
- **Rule**: For attendance V1, expose only church-group aggregates. Keep the minimum internal member reference required to enforce one check-in per person, but do not expose member identities, names, personal history, or person-level statistics until explicitly approved for a later version.

### Local-only data: run SQL directly, don't touch files
- **Mistake**: When asked to add local test data, modified/created a seed file instead of running SQL directly against the local DB.
- **Rule**: "Only on local backend" means `docker exec ... psql` with direct INSERT statements. No file changes. Seed files are for data that must survive a DB wipe; ad-hoc local data does not belong there.

### Don't create new attendance definitions for seed data
- **Mistake**: Created "로컬 테스트용 (전일)" all-day definitions to simplify date-window matching, without being asked.
- **Rule**: When asked to add attendance logs, use only the definitions that already exist. Never create new definitions unless explicitly requested.

### Commitlint: restricted scope enum + lowercase subject
- **Mistake**: Wrote commits like `feat(events): expose announcementId ...` and `feat(ministry): ...`. commit-msg hook rejected them: scope `events`/`ministry` not allowed, and the subject failed `subject-case` because of camelCase/uppercase (`announcementId`, `RSVP`).
- **Rule**: Commit scope must be one of `[auth, member, api, db, config, ci, deps, release, cleanup]` — use `api` for endpoint/DTO changes, `auth` for security/roles; never a feature-domain scope. The subject must be all lowercase (no camelCase identifiers or acronyms) — put precise identifiers like `announcementId` in the body instead. Same rules apply in the `hanmaum-dn-ops` repo.

### @Value List<String> does not work with YAML list syntax
- **Mistake**: Defined `app.security.allowed-issuers` as a YAML list (`- item`) and injected it via `@Value("\${app.security.allowed-issuers}")` targeting `List<String>`. Spring stores YAML lists as indexed properties (`[0]`, `[1]`), leaving the scalar unresolvable — startup crash, staging down.
- **Rule**: `@Value` with `List<String>` requires a comma-separated scalar string. Use `allowed-issuers: "a,b,c"` in YAML, not the list syntax. For multi-value properties use `@ConfigurationProperties` if a list is needed. Always add the property to `application-test.yml` so the Spring context test fails fast locally instead of in staging.

### Keep reusable observability infrastructure in its own repository
- **Mistake**: Added Grafana, Loki, Alloy, dashboards, and their deployment lifecycle directly to the DN server repository even though the monitoring platform must later serve multiple independent services.
- **Rule**: Put shared observability infrastructure in a dedicated repository. Application repositories should contain only the minimal integration needed to expose or label their own logs and metrics.
