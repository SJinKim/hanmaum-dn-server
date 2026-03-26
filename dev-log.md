# dev-log — hanmaum D+N

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
- None — backend work complete but uncommitted

### Next Session
1. **Resolve git lock first**: `rm Private_Projects/dn-app/.git/index.lock` on host
2. Stash state: `git stash list` — stash@{0} contains pre-session main branch state. Either pop or drop.
3. Switch to `dev`: `git checkout dev && git pull origin dev`
4. Create feature branch: `git checkout -b feature/member-management-backend`
5. Stage all changed files: `git add` (see list below) and commit: `feat(member): complete F1 backend to DoD`
6. Run `./gradlew build` — must pass before proceeding
7. After backend build passes → update MVP.md F1 Backend: 🔄 → ✅
8. Next layer: **F1 Dashboard** — scaffold `dn-app-dashboard` (Angular 21 + PrimeNG + Tailwind + Keycloak PKCE)

### Files Changed (need staging + commit)

```
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/domain/Member.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/api/v1/dto/MemberDtos.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/api/MemberMappers.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/repository/MemberRepository.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/service/MemberService.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/api/v1/MemberController.kt
backend/src/main/kotlin/com/hanmaum/dn/app/features/members/api/v2/MemberControllerV2.kt
backend/src/main/kotlin/com/hanmaum/dn/app/common/domainvalue/MemberStatus.kt
backend/src/main/resources/db/migration/V1774557470475__add_member_keycloak_id_profile_image_inactive_status.sql
backend/src/test/kotlin/com/hanmaum/dn/app/features/members/service/MemberServiceTest.kt
backend/src/test/kotlin/com/hanmaum/dn/app/features/members/api/MemberMappersTest.kt
dev-log.md
```
