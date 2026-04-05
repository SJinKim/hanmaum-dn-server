# dev-log — hanmaum D+N

## 2026-04-05 (Session 2 — Feature Planning)

### What was done this session

Completed F2 App layer (Tasks 10–15):
- Compiled and committed all 6 KMP presentation files (MinistryList + MinistryDetail screens)
- Fixed LazyColumn missing `key` and stale snapshot in `register()` (code review catch)
- Wired Routes.kt, AppModule.kt, App.kt, QuickMenuSection.kt, HomeScreen.kt (Task 14)
- Updated CHANGELOG.md + MVP.md, pushed both repos

### Feature specs written (no implementation yet)

All 4 spec files are in `docs/superpowers/specs/`. Each is approved and ready for a `writing-plans` session.

| Spec file | Feature | Roadmap |
|-----------|---------|---------|
| `2026-04-05-bulletin-design.md` | 주보 — weekly PDF bulletin | new (after v0.1.0) |
| `2026-04-05-quicklinks-sidebar-design.md` | 설교영상 YouTube config + sidebar drawer | new (after v0.1.0) |
| `2026-04-05-push-notifications-design.md` | Push notifications (FCM) | v0.2.0 |
| `2026-04-05-dashboard-analytics-admin-design.md` | Dashboard analytics + all admin control pages | v0.1.0 remaining + v0.5.0 |

### Key design decisions captured

- **주보:** PDF upload → MinIO (dev) / Cloudflare R2 (prod). Inline WebView in app. Current week only.
- **설교영상:** Configurable via `ChurchConfig` DB table (key-value). App deep-links to YouTube on tap.
- **Sidebar:** Hamburger `ModalNavigationDrawer`. Static items (Profile, Settings, Logout) hardcoded. Dynamic items from backend, managed by admin.
- **Notifications:** FCM. `NotificationEventType` enum with admin toggle table. Initial triggers: NEW_BULLETIN + NEW_ANNOUNCEMENT.
- **Dashboard:** F3 Attendance pages + ministry approval workflow UI are v0.1.0 remaining. Analytics (charts, stats) is v0.5.0.
- **File storage:** `FileStorageService` abstraction (MinIO/R2) is shared infrastructure — also solves member photos + ministry images.

### Next sessions (suggested order)

1. **F3 Attendance App** — complete the last 🔲 item in MVP v0.1.0
2. **Dashboard — F3 Attendance + Ministry approval UI** — close out v0.1.0 dashboard
3. **주보 feature** — Backend (FileStorageService + MinIO + Bulletin entity) → Dashboard → App
4. **Quick-links + Sidebar** — ChurchConfig + SidebarItems Backend → Dashboard → App
5. **Push Notifications** — Firebase setup + FCM backend + App token flow
6. **Dashboard Analytics** — v0.5.0 stats endpoints + charts

### Open prerequisites before push notifications

- Firebase project must be created manually (console.firebase.google.com)
- `google-services.json` (Android) and `GoogleService-Info.plist` (iOS) must be downloaded
- Firebase Admin SDK service account JSON must be stored as an environment secret

---

## 2026-04-05

### Feature
F2: Ministry + Registration — Backend approval workflow + KMP App layer

### What's done (committed)

**Backend — branch: `feat/f2-ministry-approval` in `dn-app`**
- `V20260405000000__add_registration_status.sql` — adds `registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'` to `ministry_registrations`
- `RegistrationStatus.kt` — enum: PENDING, APPROVED, REJECTED
- `MinistryRegistration.kt` — added `status: RegistrationStatus` field with `@Enumerated(EnumType.STRING)`
- `MinistryDtos.kt` — `RegistrationDto` now includes `status: String`; added `UpdateRegistrationStatusRequest`
- `MinistryMappers.kt` — `toDto()` maps `status = this.status.name`
- `MinistryRegistrationRepository.kt` — added `findByMinistryIdAndMemberIdAndPeriod()` JPQL query
- `MinistryService.kt` — updated `registerSelf` (re-apply on REJECTED), added `getMyRegistration()`, `approveOrRejectRegistration()`
- `MinistryController.kt` — added `GET /{id}/registrations/me` (MEMBER) and `PATCH /{id}/registrations/{regId}` (ADMIN)
- `MinistryServiceTest.kt` — 8 new tests; all passing

**KMP App — branch: `feat/f2-ministry-app` in `HanmaumDnApp`**
- Domain models committed: `Ministry.kt` (Ministry, MinistryDetail, MyRegistration, RegistrationStatus enum)
- Domain repository committed: `MinistryRepository.kt` interface
- Data layer committed: `MinistrySummaryResponse`, `MinistryDetailResponse`, `RegistrationResponse`, `CreateRegistrationRequest`, `MinistryRepositoryImpl`

### What's on disk but NOT yet committed (HanmaumDnApp)

These files are written and ready — next session just needs compile-check + commit:
- `presentation/list/MinistryListUiState.kt`
- `presentation/list/MinistryListViewModel.kt`
- `presentation/list/MinistryListScreen.kt`
- `presentation/detail/MinistryDetailUiState.kt`
- `presentation/detail/MinistryDetailViewModel.kt`
- `presentation/detail/MinistryDetailScreen.kt`

### Remaining tasks for next session

**Task 10** — compile-check + commit the presentation files above:
```bash
cd HanmaumDnApp
./gradlew compileKotlinAndroid --no-daemon
git add composeApp/src/commonMain/kotlin/com/hanmaum/dn/mobile/features/ministry/presentation/
git commit -m "feat(ministry-app): add MinistryList and MinistryDetail screens with ViewModels"
```

**Task 14** — Wire navigation, DI, QuickMenu (`Routes.kt`, `AppModule.kt`, `App.kt`, `QuickMenuSection.kt`, `HomeScreen.kt`):
- Add `MinistryListRoute` and `MinistryDetailRoute` to `Routes.kt`
- Register `MinistryRepository`, `MinistryListViewModel`, `MinistryDetailViewModel` in `AppModule.kt`
- Add ministry composable routes in `App.kt`
- Add `onMinistryClick` parameter to `QuickMenuSection` and `HomeScreen`
- Full compile check after wiring

**Task 15** — CHANGELOG + MVP.md:
- Add F2 App entries to `CHANGELOG.md`
- Mark F2 App ✅ in `MVP.md`
- Commit both repos
- Push `feat/f2-ministry-approval` → PR to `dev` in `dn-app`
- Push `feat/f2-ministry-app` → PR to `main` in `HanmaumDnApp`

### Plan file
`dn-app/docs/superpowers/plans/2026-04-05-f2-ministry-app.md`

## 2026-03-27

### Feature
- F2: Ministry + Registration

### Blockers
1. **`dn-app/.git/index.lock` stale — AGAIN** (same sandbox restriction)
   - All code is written to disk but could not be committed.
   - **Action required (user):** `rm Private_Projects/dn-app/.git/index.lock`
   - Then: `cd Private_Projects/dn-app && git checkout dev && git pull`
   - Then: `git checkout -b feature/ministry-registration-backend`
   - Then: `git add backend/src/main/kotlin/com/hanmaum/dn/app/features/ministry/ backend/src/main/resources/db/migration/V1774598535211__ministry_schema_align_spec.sql backend/src/test/kotlin/com/hanmaum/dn/app/features/ministry/`
   - Then: `git commit -m "feat(ministry): complete F2 ministry + registration backend to DoD"`
   - Build verify: `./gradlew build` — must pass with 0 errors.
2. No network access — `./gradlew build` must run on host.

### Completed (code on disk, commit pending host action above)
- **F2 Ministry + Registration Backend — all DoD items delivered:**

  **Entity changes:**
  - `Ministry.kt` — split `description: String` → `shortDescription: String (max 200)` + `longDescription: String? (TEXT)`
  - `MinistryRegistration.kt` — changed `memberId: Long` → `member: Member` (`@ManyToOne` FK proper)

  **New Flyway migration:**
  - `V1774598535211__ministry_schema_align_spec.sql`
    - Renames `ministries.description` → `short_description VARCHAR(200)`
    - Adds `long_description TEXT NULL`
    - Adds FK constraint `ministry_registrations.member_id → members(id) ON DELETE CASCADE`

  **New files (all in `backend/src/main/kotlin/...features/ministry/`):**
  - `repository/MinistryRepository.kt` — findAllActive(active?), existsByName, findByPublicId
  - `repository/MinistryRegistrationRepository.kt` — findByMinistryId(?period), existsByMinistryIdAndMember_IdAndPeriod
  - `api/v1/dto/MinistryDtos.kt` — MinistrySummaryDto, MinistryDto, LeaderDto, RegistrationDto, Create/Update/CreateRegistrationRequest (all validated)
  - `api/MinistryMappers.kt` — extension functions: toEntity, applyPatch, toSummaryDto, toDto, toLeaderDto, MinistryRegistration.toDto
  - `service/MinistryService.kt` — full business logic: 409 on duplicate name/registration, 403 on unauthorized withdraw, 400 on inactive ministry, 404 on all lookups, @Transactional on all writes
  - `api/v1/MinistryController.kt` — all 8 endpoints with correct roles and HTTP status codes

  **Tests:**
  - `backend/src/test/.../ministry/service/MinistryServiceTest.kt` — 12 tests covering all happy paths + 409/403/400/404 error cases

### In Progress
- Nothing (F2 backend complete, pending commit)

### Next Session
1. **Commit F2 backend** (host — see Blocker #1 above for exact commands)
2. `./gradlew build` — must pass; fix any compilation errors
3. **F2 Dashboard** — build Angular ministry list + detail + registration views (wired to `/api/v1/ministries`)
4. Check if F1 Dashboard needs any fixes (ng build has never run; may have TS errors)

---

## 2026-03-26

### Feature
- F1: Member Management

### Blockers

1. **`dn-app/.git/index.lock` — stale git lock, cannot be removed**
   - Root cause: Sandbox file system restrictions (`Operation not permitted`) on `.git/objects/` during `git stash --include-untracked`.
   - The stash itself was saved (`stash@{0}: autonomous-session-stash: main dirty state before switching to dev`).
   - **Action required (user):** Delete `.git/index.lock` from outside the sandbox (e.g., terminal on host machine: `rm Private_Projects/dn-app/.git/index.lock`), then `git stash drop` if not needed, or `git stash pop` to restore the staged MVP.md.
   - This blocked: `git checkout dev`, `git checkout -b feature/...`, and all `git commit` calls.

2. **No network access — Gradle wrapper download blocked**
   - `./gradlew compileKotlin` failed with `UnknownHostException: services.gradle.org`.
   - Build was **manually verified** instead: cross-referenced all changed files for type mismatches and stale method calls. No compilation errors found.
   - **Action required (user):** Run `./gradlew build` after network access is restored.

### Completed

- **F1 Backend — all DoD violations fixed** (code written, uncommitted due to git lock):

  1. **`MemberResponse` internal `id` exposure** (critical architectural violation) — FIXED
     - `MemberResponse.id: Long` → `publicId: String` in `MemberDtos.kt:49`
     - `toResponse()` in `MemberMappers.kt:138` now uses `this.publicId.toString()`

  2. **Wrong API route** — FIXED
     - `MemberController` mapping: `/members` → `/api/v1/members`

  3. **`ApiResponse<T>` not used consistently** — FIXED
     - All 6 endpoints now return `ResponseEntity<ApiResponse<T>>`

  4. **No `@Valid` on request bodies** — FIXED
     - `@Valid` added to `createMember`, `updateMember`, `registerMember` in controller

  5. **No role security** — FIXED
     - `@PreAuthorize("hasRole('ADMIN')")` on: `listMembers`, `getMember`, `createMember`, `updateMember`, `deleteMember`
     - `@PreAuthorize("isAuthenticated()")` on: `getMyProfile`

  6. **PUT not PATCH for partial update** — FIXED
     - `updateMember` now uses `@PatchMapping`
     - `UpdateMemberRequest` all fields nullable (PATCH semantics)
     - `updateForm()` replaced by `applyPatch()` in `MemberMappers.kt`

  7. **`getAllMembers()` returning raw `List<Member>` entity** — FIXED
     - Service method renamed `getMembers(search, page, size)` → returns `Page<MemberSummaryDto>`
     - Soft-delete filter: uses `findActiveMembers()` (deletedAt IS NULL)
     - Search filter on lastName / firstName / email

  8. **`createMember()` returning raw `Member` entity** — FIXED
     - Now returns `MemberDto` (publicId, no internal id)

  9. **Status transition not validated** — FIXED
     - `applyPatch()` in `MemberMappers.kt` blocks DELETED via PATCH; throws `IllegalStateException`
     - `softDeleteMember()` handles DELETED transition exclusively

  10. **`MemberControllerV2` conflicting at `/members`** — FIXED
      - Replaced with empty comment-only file

  11. **Missing `keycloakId` field on Member** — FIXED
      - Added `keycloakId: String?` to `Member.kt:50`
      - Flyway `V1774557470475__add_member_keycloak_id_profile_image_inactive_status.sql`
      - `registerMember()` now stores keycloakId from Keycloak `Location` header

  12. **Missing `profileImageUrl` field** — FIXED
      - Added `profileImageUrl: String?` to entity + migration + all DTOs/mappers

  13. **`MemberStatus.INACTIVE` missing** — FIXED
      - Added `INACTIVE` to `MemberStatus.kt`
      - Migration maps `PENDING → ACTIVE`, `REJECTED → INACTIVE`

  14. **`getMemberProfile()` using email as lookup (fragile)** — FIXED
      - Service now looks up by `keycloakId` (JWT `sub`) first, falls back to email for legacy records

  15. **Tests updated** — updated `MemberServiceTest.kt` and `MemberMappersTest.kt` to match new signatures

### In Progress
- F1 Dashboard scaffold written; commit blocked by sandbox index.lock (see below)

### F1 Dashboard Scaffold — completed this session
All 32 files created in `dn-app-dashboard/`. Angular 21 standalone, PrimeNG 19, Tailwind 3, Keycloak-js 26.

**Commit blocked by sandbox** — same `index.lock` issue. Run on host:
```bash
cd Private_Projects/dn-app-dashboard
rm .git/index.lock
git commit -m "chore: scaffold Angular 21 dashboard for F1 Member Management"
npm install
ng build --configuration production
```

**Files in scaffold:**
```
package.json, angular.json, tsconfig*.json, tailwind.config.js, .gitignore
src/main.ts, src/index.html, src/styles.scss, src/proxy.conf.json
src/environments/environment{.prod}.ts
src/app/app.config.ts              — APP_INITIALIZER (Keycloak), JWT interceptor, PrimeNG Aura theme
src/app/app.routes.ts              — lazy member routes behind authGuard
src/app/app.component.{ts,html,scss} — Menubar shell + logout
src/app/core/services/auth.service.ts     — Keycloak PKCE init, token refresh, signals
src/app/core/services/api.service.ts      — Generic HTTP + ApiResponse unwrapping
src/app/core/interceptors/jwt.interceptor.ts — Bearer token attachment
src/app/core/guards/auth.guard.ts         — authGuard + adminGuard
src/app/core/models/api-response.model.ts — ApiResponse<T>, PageResponse<T>
src/app/core/models/member.model.ts       — MemberSummary, Member, DTOs, enums, labels
src/app/features/members/member.service.ts         — getMembers/getMember/create/update/delete
src/app/features/members/members.routes.ts
src/app/features/members/members-list/members-list.component.{ts,html}
src/app/features/members/member-detail/member-detail.component.{ts,html}
src/app/features/members/member-edit/member-edit.component.{ts,html}
```

### Next Session
1. **Commit dashboard** (host): `cd dn-app-dashboard && rm .git/index.lock && git commit`
2. `npm install && ng build --configuration production` — must pass; fix any TS errors found
3. Fix backend git: `cd dn-app && rm .git/index.lock && git checkout dev && git pull`
4. `git checkout -b feature/member-management-backend` then commit backend changes
5. After both builds pass → MVP.md: F1 Backend ✅, F1 Dashboard ✅
6. Next feature: **F2 Ministry backend** (domain written; needs service, controller, mapper, tests)
