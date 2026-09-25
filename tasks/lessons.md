# Lessons Learned

> Updated after every user correction. Reviewed at session start.

## Format
- **Mistake**: what went wrong
- **Rule**: what to do instead (permanent prevention)

## Lessons
<!-- Claude appends here after each correction -->

### Confirm where an application appears before choosing a new table
- **Mistake**: Assumed ministry self-applications needed a separate table while the intended team workflow shows new applicants directly as `PENDING` rows in the existing 팀원 table.
- **Rule**: For a new approval flow, confirm the operator's working list and reuse its existing membership record and status model when the pending application belongs there; add only the fields needed for the application and decision.

### Escalated MVP priorities override a previous deferral
- **Mistake**: Deferred issue #184 solely because the written MVP scope did not list the Newcomer cutover, even though the stakeholder needed it for the MVP.
- **Rule**: Treat an explicit user statement that a capability is needed for MVP as an authorized scope decision: update the scope artifact and implement the minimal complete path; do not leave the work deferred based on an older scope document.

### Update retry tests when the workflow gains a follow-up action
- **Mistake**: Left the claim-retry test asserting that a pre-existing conflict must skip the candidate lookup after reconciliation reviews were introduced.
- **Rule**: When a workflow adds an audit or review step after a previously terminal state, revisit retry assertions: distinguish “do not duplicate the original record” from “skip all later reconciliation work.”

### Distinguish a merged implementation from an issue's GitHub state
- **Mistake**: Treated Issue #181's still-open GitHub state as proof that its implementation had not been merged.
- **Rule**: When checking delivery status, inspect `origin/main` and the relevant merged PR diff first. An open issue can be a ticket-hygiene problem; state that distinction explicitly before concluding work is absent.

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

### Never group or compare encrypted columns in SQL
- **Mistake**: Wrote a deduplication script that grouped `temp_members` by
  `(first_name, last_name, source_file)`. It reported 31 duplicates against local
  plaintext data — and would have merged genuinely different people who share a name.
  Worse, the columns are encrypted with AES-GCM under a random nonce, so the same name
  produces different ciphertext on every write: in staging or production the grouping
  silently matches nothing.
- **Rule**: Encrypted columns (`members.*`, `temp_members.*`) may only be tested for
  NULL / NOT NULL in SQL. Any equality, grouping, ordering or LIKE must go through a
  deterministic lookup hash (`email_lookup_hash`, `keycloak_lookup_hash`,
  `identity_lookup_hash`) — that is what those columns exist for. If no hash covers the
  case, do the comparison in the application after decryption, or add a hash column.

### 양육 마스터 import: measured figures (2026-08-16)
- 13 sheets: 12 person-per-row sheets totalling **405** rows, plus the 역대제자반 matrix
  with **84** name cells (18 rows × 8 cohort columns).
- After cross-sheet deduplication (matrix folded into the dated sheets by name+course):
  **436 records**, **211 distinct people**, **39 without a birth date**.
- Those 39 can never be matched automatically and stay in the admin queue under
  `NEEDS_BIRTHDATE` until someone supplies a birth date — that is the size of the manual
  workload, and the reason the queue separates that status from `AMBIGUOUS`.
- 8 cohorts exist: LEGACY 1기 plus POWER 1기–7기 (2019, 2020, 2023, 2024, 2025, 2026).

### Running the full test suite locally needs .env plus a PII override
- **Mistake**: Ran `./gradlew test -PincludeIntegration` with only the test-DB variables
  exported and reported a failure; then with the full `.env` and got a *different* set of
  failures. Neither environment runs the whole suite.
- **Rule**: The suite has two incompatible groups. `AppApplicationTests` boots the default
  (dev) profile and needs the complete `.env` including the Keycloak variables, while every
  `@ActiveProfiles("test")` IT is rejected by `PiiCryptoConfiguration` when local plaintext
  PII is on. Run the whole suite with:
  `set -a; . ./.env; set +a; export PII_LOCAL_PLAINTEXT_ENABLED=false; ./gradlew test -PincludeIntegration`
  Integration tests are excluded without `-PincludeIntegration`. Never conclude a test
  failure is caused by a code change before checking whether it is this environment split —
  a missing `${...}` placeholder in the failure message is the tell.

### A state attribute and the class that leads to it are two different things
- **Mistake**: Saw that `members.baptism` already existed and recommended dropping the
  세례입교 sheet from the training catalog as "duplicate member state". It is not
  duplicate: 세례입교 (Baptism & Church Membership Class) is a course newcomers attend
  in order to be baptised; `members.baptism` records whether the sacrament happened.
- **Rule**: Before collapsing a proposed entity into an existing column because the
  names overlap, ask what the church actually does with it. A qualification, the
  course that grants it, and the record that it happened are separate facts and may
  all need to exist. Overlapping vocabulary is not evidence of redundant modelling.

### Verify plan claims against the source file, not just the schema
- **Mistake**: Would have implemented a training-import plan whose cohort unique key
  (`training_id, label`) silently split one real cohort into two, because two sheets
  spell the same cohort `파워3기` and `청년파워제자반 3기 (2020)`.
- **Rule**: When a plan describes data that lives in an external file (Excel, CSV,
  export), probe the file's real structure before accepting the plan's schema — sheet
  list, header positions, per-column fill rates, value formats, and overlaps between
  sheets. Read it structurally (unzip + parse XML) so no PII is printed. Row counts and
  column headers routinely contradict the plan's assumptions.

### Keep reusable observability infrastructure in its own repository
- **Mistake**: Added Grafana, Loki, Alloy, dashboards, and their deployment lifecycle directly to the DN server repository even though the monitoring platform must later serve multiple independent services.
- **Rule**: Put shared observability infrastructure in a dedicated repository. Application repositories should contain only the minimal integration needed to expose or label their own logs and metrics.

### A data file teaches two separable things; don't import what it already taught
- **Mistake**: Treated the 양육 마스터 workbook as one feature. It carried two unrelated
  things: *which courses and cohorts exist* (already extracted into static seed
  migrations, frozen, never read at runtime) and *who attended what* (436 participation
  records). Because they were bundled, dropping the person data looked like it would cost
  the catalog too, and an entire Apache POI pipeline was nearly kept alive for data that
  was already committed as SQL.
- **Rule**: When a source file feeds a feature, separate what it *taught* the schema from
  what it *supplies* at runtime. Anything already frozen into a seed migration needs no
  importer. Ask which half a change actually removes before assuming the whole pipeline is
  load-bearing.

### Copy test conventions from surviving code, never from code being deleted
- **Mistake**: Wrote plan test code using `@WithMockUser`, copied from
  `TrainingImportAdminControllerTest` — a file the same plan deleted. No surviving web
  slice test in this repo uses it; all nine use `SecurityMockMvcRequestPostProcessors.jwt()`.
  `@WithMockUser` never reaches the resource-server chain here, so role tests returned 401
  instead of 403 — they would have "passed" while proving nothing about authorisation.
- **Rule**: When writing new tests, grep the convention across tests that will still exist
  afterwards. A pattern found only in a file you are deleting is not evidence of a
  convention. For `@WebMvcTest` slices here: `.jwt().authorities(SimpleGrantedAuthority(...))`,
  and import `SecurityConfig` whenever the controller takes `@AuthenticationPrincipal Jwt`
  (its argument resolver only exists under `@EnableWebSecurity`, otherwise the request 500s).

### Repair Flyway surgically; the local dev DB holds real work
- **Mistake risk**: Deleting already-applied migrations invites `make reset`, which is
  `docker compose down -v` — it would have destroyed 34 members built up by hand in the
  local dev DB, and the volume is shared by every worktree.
- **Rule**: Repair instead of reset. Drop the objects the deleted migrations created,
  `DELETE` their `flyway_schema_history` rows, and `UPDATE` the checksum of any migration
  edited in place. Flyway's checksum is CRC32 over the file's lines with line terminators
  stripped — **validate the implementation against two or three unmodified migrations
  first**; if those match the stored values, the recomputed one is trustworthy. Apply to
  both the dev DB (`hanmaumApp-db`, 5433) and the test DB (`infrastructure-test-db-1`, 5434).


### Fetch a sibling repo before diagnosing drift in it
- **Mistake**: Reported that `hanmaum-dn-ops/api/openapi.yaml` was missing every
  `/verses` path and opened a "sync" PR for it. The local ops clone was four commits
  behind (`55fd45c`/#15 while `main` was at #20); the paths had been synced hours
  earlier by ops #18, #19 and #20. The PR merged as an empty commit, and its message —
  now on ops `main` — claims 314 additive lines and a sync that "got skipped". The
  stale clone happened to hold 78 operations, the exact number the server CLAUDE.md
  records for 2026-09-01, which made the wrong conclusion look confirmed.
- **Rule**: A cross-repo claim needs a `git fetch` in that repo first, and the
  comparison runs against `origin/main`, never against whatever the local checkout
  happens to be. This applies to every sibling repo (`hanmaum-dn-ops`,
  `-mobile-app`, `-web-app`), whose clones are only current by coincidence. Two
  further guards: a count matching a number written in a doc is not corroboration —
  the doc records a past state and drifts the same way the clone does; and before
  opening a sync PR, diff the generated file against the remote (`git diff
  origin/main -- api/openapi.yaml`) and skip the PR when it is empty.

### Paginate the sorted result and protect generated operation IDs
- **Mistake**: Returned the full sorted member list inside `PageImpl`, loaded list enrichment for every match, changed the default name normalization, and let a second `graduate` endpoint rename an existing generated `operationId`.
- **Rule**: For a paginated list, test with more matches than the page size and assert both page contents and metadata. Sort the complete candidate set, slice it, then enrich only that page. Keep the established normalization for default sorting. When two controller methods share a name, assign stable, distinct OpenAPI operation IDs before syncing the generated contract.

### Prove persistence flows beneath mocked controller tests
- **Mistake**: Claimed POST, GET, and PATCH occupation acceptance criteria were covered by controller tests whose mocked service returned fixed DTOs.
- **Rule**: When a controller slice mocks the service, also test the service write and read path with assertions on the saved entity and returned DTO; a fixed mock response only proves HTTP serialization.

### Preserve exact dates across related assignment flows
- **Mistake**: The ministry self-registration branch still rounded application and approval start dates to the first of the month while another branch fixed assignment dates to retain the day.
- **Rule**: When a date semantics fix lands on main, search every related write path and assert the exact day in focused tests before resolving and merging dependent PRs.
