# Lessons Learned

> Updated after every user correction. Reviewed at session start.

## Format
- **Mistake**: what went wrong
- **Rule**: what to do instead (permanent prevention)

## Lessons

### Child-only attendance edits must advance the parent version
- A room redistribution can leave all parent totals unchanged. Touch the parent audit state in the same locked transaction so its optimistic version still advances; prove both same-total saves and an overlapping legacy summary writer with PostgreSQL tests.

### Import review: distinguish source formats and application lifecycle
- Parse German thousands separators only in CSV; an XLSX cached decimal is fractional data and must be rejected.
- Translate only the named date-uniqueness constraint to 409. Log safe diagnostics for other persistence failures without PostgreSQL row details.
- A maintenance runner must not close a successful application context before other startup runners finish. Test the packaged process's success/failure exit, not just its callback.

### Guide historical import — approved year blocks and excluded columns
- Follow the final user-approved block boundaries, including header 116 as 2024; stored Excel date years and older discussion estimates are not authoritative.
- Import only Summary B:G/I. Never recover, validate or replace these counts using H, K:X, right-hand comparison tables or weekly room details. Preserve NULL versus accounting-format zero.
<!-- Claude appends here after each correction -->

### Publication does not imply immediate member visibility
- **Mistake**: The current bulletin opened before Friday, while history and direct date access exposed all published future Sundays.
- **Rule**: A Sunday bulletin becomes member-visible only on Friday at 00:00 Europe/Berlin (`serviceDate <= Berlin today + 2 calendar days`). Apply the same cutoff to current, direct and history reads, filtering in the database before pagination/counting. Admin publication is independent; test every member path and the midnight boundary.

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
- **Rule**: Expose person-level attendance only where it was explicitly approved, and keep it out of the aggregate path. `AttendanceService` hands out 순 aggregates only. The repo owner approved a person-level 체크인 명단 for ADMIN on 2026-09-26 (#224); it lives solely in `AttendanceRosterService` / `AttendanceLogController` behind `hasRole('ADMIN')`. Anything else that names a member needs its own approval first.

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

### Keep design authority and issue boards explicit across repositories
- **Correction**: Figma is always the design source; server issues belong to DN Server, web issues to DN WebApp.
- **Rule**: Before implementation, inspect previous issues, create and place each issue on its own repository's board (server Project #5, web Project #7), then update the existing Figma design before writing UI code. Local design documentation is a reference, never an override for Figma.

### Classify persistence conflicts and bound public date cursors
- **Correction**: A generic integrity failure was mislabeled as a taken Sunday, and cursor arithmetic accepted dates that could overflow.
- **Rule**: Translate only the named constraint that identifies the domain conflict; rethrow unrelated integrity errors. Bound cursor arithmetic before adding periods, tolerate stale read cursors, and document whether pagination changes a global suggestion.

### Follow UI dependencies and preserve secure defaults when expanding roles
- **Mistake**: Expanded general operations member assignment rights without granting the required member-name picker, and removed controller defaults while splitting reads from writes.
- **Rule**: Trace the complete UI action, including lookup endpoints, when adding a role. Preserve a restrictive class-level authorization baseline and explicitly allow broader reads. Test an unannotated handler's inherited default, document privileged assignment changes, and name ownership bypass flags after their actual permission rather than an assumed admin role.

### Scoped member writes must coexist with other editors
- **Mistake**: Locked only the new training-flag writer while existing member edits could flush stale values for every column and silently undo the flag.
- **Rule**: Trace all writers of a shared entity when adding a scoped update. Use column-level dirty updates or coordinated concurrency control, and prove independent changes survive overlapping real database transactions. In negative persistence tests, clear the persistence context and query the stored row, including soft-deleted rows, rather than asserting only on the fixture object.


### Realm configuration automation belongs to every access change
- **Correction**: New training roles and groups were added to exports without an existing-realm provisioning path, leaving the user to discover the missing live configuration.
- **Rule**: Whenever roles, groups or role mappings change, update configure-realm.sh or its managed input and both exports in the same change, verify dry-run and repeated application, and report live rollout separately. This is part of the feature, without needing a separate reminder from the user.


### Reconciliation must surface parser failures and follow configuration growth
- **Correction**: Review found unchecked jq process substitutions, a fixed group count in the integration test and validation of an unused dashboard URL in access-only mode.
- **Rule**: Materialize parsed work lists with checked exit codes before any writes. Derive expected managed state from the configuration, and isolate validation to the settings used by the selected mode. Keep failed integration diagnostics visible after temporary resources are removed.

### Reuse room master data for ministry counting locations
- **Correction**: Guide counting locations were encoded as a fixed enum despite the existing room master table.
- **Rule**: Inspect shared location master data before adding feature-specific room identifiers. Reference the existing room by foreign key; model counting areas separately when one room has multiple areas, and retain historical report labels and membership when configuration changes.

### Verify role inheritance before accepting authorization review claims
- Correction: Guide editors already inherited viewer access in Roles.HIERARCHY; the reported absence was repeated without inspecting that source.
- Rule: Trace annotations, the actual role hierarchy and a single-role controller test before confirming an authorization defect. Keep controller access explicit when it improves clarity.

### Match deployment health states exactly
- Correction: A substring check for healthy also accepted unhealthy in the final deployment gate.
- Rule: Match enum-like health output as an entire fixed string; test unhealthy, starting, missing output and upstream command failures alongside the positive case.

### Validate nullable enums in the generated OpenAPI contract
- **Correction**: Kotlin nullable RSVP fields were emitted as non-null strings, and the response enum excluded the actual JSON null used for unanswered members.
- **Rule**: Compare serialized unanswered DTOs with the generated OpenAPI 3.1 schema. Permit null in both the type union and any enum constraint; verify default and grouped documentation, regenerate the Ops contract, and compare its semantic diff before merge.

### Preserve the presenter's personal motivation in stakeholder materials
- **Mistake**: Made the community presentation mainly about benefits and collaboration, while the presenter wanted their substantial time, effort, and feelings to help encourage adoption.
- **Rule**: When the presenter explicitly asks for personal motivation, include their stated effort and sincere hope in the opening and closing, then connect it to a concrete request to use and improve the project together. Do not invent sacrifices or imply an obligation to repay the presenter.

### Make stakeholder decks visually varied and scripts speakable
- **Mistake**: Repeated flat text layouts made the presentation feel bland, and separate explanatory paragraphs made the Korean script sound disconnected.
- **Rule**: For a warm community presentation, vary the compositions, use relevant illustrations and clear typographic emphasis, and add restrained slide transitions when requested. Write the full script as connected spoken Korean, with a natural handoff between slides, and read it as one speech before delivery.

### Use the community's own name in public-facing materials
- **Mistake**: Used the repository's DN abbreviation as the presentation name, while the community is 한마음교회 청년교구 LDN.
- **Rule**: Treat the user's community name as authoritative in slide titles, scripts, speaker notes, and deliverable filenames. Keep repository names only in technical source references.

### Distinguish realm exports from live Keycloak rollout
- **Mistake**: Reported the training roles/groups as delivered after merging realm exports, while the existing staging realm had not been updated and configure-realm.sh did not provision them.
- **Rule**: Report checked-in realm definitions, deployment and verified live realm state separately. For changes intended for an existing realm, provide an idempotent provisioning path and verify the live roles, group hierarchy and mappings before claiming rollout is complete.

### Collect new decisions at the end and leave PRs open
- **Correction**: The user wants newly required decisions summarized at the end of the issue queue, with PRs left open.
- **Rule**: Continue independent authorized work; collect unresolved choices with affected issues/PRs, options and a recommendation in the final report. Leave affected PRs open, do not merge or guess approval, and preserve decisions already explicitly approved by the user.

### An application counter is not concurrency control
- **Mistake**: The profile image upload read the stored key and version, then wrote the new ones. Two concurrent uploads by one member both read the same state, both bumped the version, and the loser's object was never deleted, leaving it orphaned in S3.
- **Rule**: For read-modify-write on member-owned fields, load the row with `PESSIMISTIC_WRITE` (`CurrentMemberResolver.requireForUpdate`) or use JPA `@Version`. Prove it with a two-transaction integration test that fails without the lock.

### A stale read must not acknowledge a cleanup order that was renewed meanwhile
- **Mistake**: The cleanup sweeper checked a key, then deleted its row unconditionally. A producer that re-listed the same key in between (`ON CONFLICT DO NOTHING`) had its retry removed by the sweeper.
- **Rule**: Release a work-queue row only if it is unchanged since it was read (compare `created_at`), and make re-listing move that timestamp. List the object key in the same transaction that deletes its last reference.

### Every acknowledgement of a queue row needs the handle of the listing it answers
- **Mistake**: After fixing the sweeper, the upload's after-commit callback still released its key with an unconditional `DELETE`. A parallel delete had re-listed the key, so the upload removed the only durable retry.
- **Rule**: When a producer lists a row, keep the timestamp `track` returns and acknowledge with `releaseIfUnchanged(key, timestamp)`. Audit every `release`/`DELETE` of a work-queue row, not just the one the review named.

### Read and write a backfill batch under one row lock
- **Mistake**: The PII backfill read a batch, then wrote it back in a later transaction. A business change committed in between was overwritten by the re-encrypted old snapshot, and the stalled check could not notice because the stale value was correctly encrypted.
- **Rule**: A job that rewrites rows from values it read earlier must read and write in the same transaction with `SELECT ... FOR UPDATE`. Cover it with a two-connection test that commits a change between read and write.

### Member-bound data must follow identity changes
- **Mistake**: Consent was stored on the temporary registration member; claim and manual link deleted that row and the proof went with it.
- **Rule**: When adding data bound to a member, check every path that merges, claims, links or purges members (`CurrentMemberResolver`, `MemberReconciliationService`) and carry the data over there, with a PostgreSQL test of the register → link → status flow.

### A member lock must survive an identity change
- **Mistake**: Withdrawal resolved the member, then locked that row; a parallel link moved the consent to the canonical member, so the withdrawal ended with 204 and the grant stayed standing.
- **Rule**: After taking a per-member lock, resolve the identity again and lock the member it now points to (`ConsentService.resolveAndLockMember`) before reading or writing member-bound data.

- [HDN-379] Every path that ends a phase (check-in writer, daily job, manual return) must raise the member's `absence_revision` under the member lock; a close without it lets an old editor token match a later phase. Secure each such path with a test that goes close → log correction → old write → 409.
- [HDN-379] A JWT `sub` that goes into a persisted actor field needs an explicit non-blank check (401) in the controller; a non-null Kotlin type does not reject `" "`.
