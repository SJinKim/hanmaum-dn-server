# F1 Member Management — Backend Design Spec

**Date:** 2026-03-26
**Branch:** feature/member-management-backend
**Status:** Completed ✅

---

## 1. Goal

Implement the F1 Member Management backend: the foundational member entity that all other features depend on. Includes full CRUD, soft delete, status transitions, Keycloak identity sync, and role-based access control.

---

## 2. Domain Model

### `Member`

| Field | Type | Constraint |
|-------|------|------------|
| `id` | Long | PK, from BaseEntity |
| `publicId` | UUID | unique, not null |
| `lastName` | String | NotBlank, max 100 |
| `firstName` | String | NotBlank, max 100 |
| `email` | String | unique, valid format |
| `phone` | String? | nullable |
| `dateOfBirth` | LocalDate? | nullable |
| `gender` | Gender | MALE \| FEMALE |
| `baptism` | Baptism | BAPTIZED \| CATECHUMEN \| NONE |
| `memberStatus` | MemberStatus | ACTIVE \| INACTIVE \| DELETED |
| `role` | Role | ADMIN \| MEMBER |
| `profileImageUrl` | String? | nullable |
| `keycloakId` | String? | unique, nullable (legacy compat) |
| `createdAt` | Instant | from BaseEntity |
| `updatedAt` | Instant | from BaseEntity |
| `deletedAt` | Instant | null = active; from BaseEntity |

**Status transitions:** `ACTIVE ↔ INACTIVE → DELETED` (DELETED is terminal — enforced in `applyPatch()`)

---

## 3. Database Migration

File: `V1774557470475__add_member_keycloak_id_profile_image_inactive_status.sql`

- Add `keycloak_id VARCHAR(255) UNIQUE`
- Add `profile_image_url VARCHAR(512)`
- Add `INACTIVE` to `member_status` enum
- Migrate existing statuses: `PENDING → ACTIVE`, `REJECTED → INACTIVE`

---

## 4. Repository Layer

**`MemberRepository`**
- `findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>`
- `findActiveMembers(search: String?, pageable: Pageable): Page<Member>`
- `findByKeycloakIdAndDeletedAtIsNull(keycloakId: String): Member?`
- `findByEmailAndDeletedAtIsNull(email: String): Optional<Member>`
- `existsByEmailAndDeletedAtIsNull(email: String): Boolean`

---

## 5. DTO Layer

### Request DTOs

```kotlin
data class CreateMemberRequest(
    @NotBlank @Size(max=100) val lastName: String,
    @NotBlank @Size(max=100) val firstName: String,
    @Email @NotBlank val email: String,
    val phone: String?,
    val dateOfBirth: LocalDate?,
    val gender: Gender,
    val baptism: Baptism,
    val role: Role = Role.MEMBER,
    val profileImageUrl: String?
)

data class UpdateMemberRequest(  // PATCH semantics — all nullable
    val lastName: String?, val firstName: String?, val email: String?,
    val phone: String?, val dateOfBirth: LocalDate?, val gender: Gender?,
    val baptism: Baptism?, val memberStatus: MemberStatus?, val role: Role?,
    val profileImageUrl: String?
)
```

### Response DTOs

```kotlin
data class MemberSummaryDto(publicId, fullName, email, memberStatus, role)
data class MemberDto(publicId, lastName, firstName, email, phone, dateOfBirth,
                     gender, baptism, memberStatus, role, profileImageUrl, keycloakId)
```

Internal `id` never exposed in any response.

---

## 6. Service Layer

**`MemberService`**

| Method | Guards |
|--------|--------|
| `createMember(req)` | 409 if email already taken |
| `getMembers(search?, page, size)` | soft-delete filtered; search on lastName/firstName/email |
| `getMember(publicId)` | 404 if not found |
| `getMyProfile(keycloakSub)` | lookup by keycloakId first, fallback to email for legacy |
| `updateMember(publicId, req)` | 404 if not found; blocks DELETED via PATCH (IllegalStateException) |
| `softDeleteMember(publicId)` | 404 if not found; sets memberStatus=DELETED + deletedAt |

---

## 7. Controller Layer

Base path: `/api/v1/members`

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/` | ADMIN | Create member |
| GET | `/` | ADMIN | List; `?search ?page ?size` |
| GET | `/{publicId}` | ADMIN | Detail |
| GET | `/me` | authenticated | Own profile via JWT sub |
| PATCH | `/{publicId}` | ADMIN | Partial update |
| DELETE | `/{publicId}` | ADMIN | Soft delete → DELETED |

All responses: `ApiResponse<T>`. All request bodies: `@Valid`.

---

## 8. Mapper Layer

File: `api/MemberMappers.kt`

```kotlin
fun CreateMemberRequest.toEntity(): Member
fun Member.applyPatch(req: UpdateMemberRequest)   // status-transition guard here
fun Member.toSummaryDto(): MemberSummaryDto
fun Member.toDto(): MemberDto
```

---

## 9. Tests

Files: `MemberServiceTest.kt`, `MemberMappersTest.kt`

Key scenarios covered:
- Create — happy path, 409 duplicate email
- List — paginated, search filter, soft-delete exclusion
- Get — 404 not found
- Update (PATCH) — partial fields only, blocks DELETED transition
- Soft delete — sets deletedAt + DELETED status
- `getMyProfile` — keycloakId lookup, legacy email fallback

---

## 10. Files Delivered

| File | Action |
|------|--------|
| `domain/Member.kt` | updated — added keycloakId, profileImageUrl, INACTIVE status |
| `domain/MemberStatus.kt` | updated — added INACTIVE |
| `repository/MemberRepository.kt` | updated — new query methods |
| `api/v1/dto/MemberDtos.kt` | rewritten — publicId only, no internal id, PATCH semantics |
| `api/MemberMappers.kt` | rewritten — toEntity, applyPatch, toSummaryDto, toDto |
| `api/v1/MemberController.kt` | rewritten — correct path, ApiResponse, @PreAuthorize, @Valid |
| `service/MemberService.kt` | rewritten — JWT auth, all guards, soft delete |
| `db/migration/V1774557470475__...sql` | new — keycloakId, profileImageUrl, INACTIVE |
| `test/.../MemberServiceTest.kt` | rewritten |
| `test/.../MemberMappersTest.kt` | rewritten |
