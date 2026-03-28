# F2 Ministry + Registration — Backend Design Spec

**Date:** 2026-03-27
**Branch:** feature/member-management-backend
**Status:** Completed ✅

---

## 1. Goal

Implement the F2 Ministry + Registration backend: allow admins to manage ministry departments, allow members to self-register for ministries per year, and allow admins to view registrations. Depends on F1 (Member entity must exist).

---

## 2. Domain Model

### `Ministry`

| Field | Type | Constraint |
|-------|------|------------|
| `id` | Long | PK, from BaseEntity |
| `publicId` | UUID | unique, not null |
| `name` | String | NotBlank, max 100 |
| `shortDescription` | String | NotBlank, max 200 |
| `longDescription` | String? | TEXT, nullable |
| `imageUrl` | String? | nullable |
| `leader` | Member? | ManyToOne FK, nullable |
| `isMinistryActive` | Boolean | default true |
| `createdAt` | Instant | from BaseEntity |
| `updatedAt` | Instant | from BaseEntity |
| `deletedAt` | Instant | null = active; from BaseEntity |

**Note:** DELETE deactivates (`isMinistryActive = false`) — not a hard delete and not `deletedAt`. Hard soft-delete (deletedAt) is for data removal only.

### `MinistryRegistration`

| Field | Type | Constraint |
|-------|------|------------|
| `id` | Long | PK, from BaseEntity |
| `publicId` | UUID | unique, not null |
| `ministry` | Ministry | ManyToOne FK, not null |
| `member` | Member | ManyToOne FK, not null |
| `registrationPeriod` | String | NotBlank, format `"YYYY"` (e.g. `"2026"`) |
| `note` | String? | max 500, nullable |
| `createdAt` | Instant | from BaseEntity |
| `updatedAt` | Instant | from BaseEntity |
| `deletedAt` | Instant | null = active; from BaseEntity |

**Unique constraint:** `(ministry_id, member_id, registration_period)` — enforced at DB + service layer.

---

## 3. Database Migration

File: `V1774598535211__ministry_schema_align_spec.sql`

- `ministries`: rename `description TEXT` → `short_description VARCHAR(200)` + add `long_description TEXT NULL`
- `ministry_registrations`: add FK constraint `member_id → members(id) ON DELETE CASCADE`

---

## 4. Repository Layer

**`MinistryRepository`**
- `findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Ministry>`
- `findAllActive(active: Boolean?): List<Ministry>` — JPQL, filters `isMinistryActive` and `deletedAt`
- `existsByNameAndDeletedAtIsNull(name: String): Boolean`

**`MinistryRegistrationRepository`**
- `findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<MinistryRegistration>`
- `findByMinistryId(ministryId: Long, period: String?): List<MinistryRegistration>` — JPQL
- `existsByMinistryIdAndMemberIdAndRegistrationPeriodAndDeletedAtIsNull(ministryId, memberId, period): Boolean`

---

## 5. DTO Layer

### Request DTOs

```kotlin
data class CreateMinistryRequest(
    @NotBlank @Size(max=100) val name: String,
    @NotBlank @Size(max=200) val shortDescription: String,
    val longDescription: String?,
    val imageUrl: String?,
    val leaderPublicId: String?   // null = no leader
)

data class UpdateMinistryRequest(  // PATCH semantics
    @Size(max=100) val name: String?,
    @Size(max=200) val shortDescription: String?,
    val longDescription: String?,
    val imageUrl: String?,
    val leaderPublicId: String?,  // empty string = remove leader
    val isActive: Boolean?
)

data class CreateRegistrationRequest(
    @NotBlank @Pattern(regexp="\\d{4}") val period: String,
    @Size(max=500) val note: String?
)
```

### Response DTOs

```kotlin
data class MinistrySummaryDto(publicId, name, shortDescription, imageUrl, leaderName, isActive)
data class MinistryDto(publicId, name, shortDescription, longDescription, imageUrl, leader: LeaderDto?, isActive)
data class LeaderDto(publicId, fullName)
data class RegistrationDto(publicId, ministryPublicId, memberPublicId, memberName, registrationPeriod, note)
```

---

## 6. Service Layer

**`MinistryService`**

| Method | Guards |
|--------|--------|
| `createMinistry(req)` | 409 if name already taken |
| `getMinistries(active?)` | active=null → all; true → active only; false → inactive only |
| `getMinistry(publicId)` | 404 if not found |
| `updateMinistry(publicId, req)` | 404 if not found; resolves leaderPublicId (empty string = remove leader) |
| `deactivateMinistry(publicId)` | 404 if not found; sets `isMinistryActive = false` (NOT deletedAt) |
| `registerSelf(ministryPublicId, keycloakSub, req)` | 404 ministry; 404 member; 400 if ministry inactive; 409 if duplicate `(ministry, member, period)` |
| `getRegistrations(ministryPublicId, period?)` | 404 if ministry not found |
| `withdrawRegistration(ministryPublicId, regPublicId, keycloakSub)` | 404 ministry + registration; 403 if not own registration; soft deletes |

---

## 7. Controller Layer

Base path: `/api/v1/ministries`

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/` | ADMIN | Create ministry |
| GET | `/` | authenticated | List; `?active=true\|false` |
| GET | `/{publicId}` | authenticated | Detail with leader info |
| PATCH | `/{publicId}` | ADMIN | Partial update |
| DELETE | `/{publicId}` | ADMIN | Deactivate (isActive=false) |
| POST | `/{publicId}/registrations` | authenticated | Register self; 409 on duplicate |
| GET | `/{publicId}/registrations` | ADMIN | List registrations; `?period=2026` |
| DELETE | `/{publicId}/registrations/{regPublicId}` | authenticated | Withdraw own only; 403 on other's |

All responses: `ApiResponse<T>`. All request bodies: `@Valid`.

---

## 8. Mapper Layer

File: `api/MinistryMappers.kt`

```kotlin
fun CreateMinistryRequest.toEntity(leader: Member?): Ministry
fun Ministry.applyPatch(req: UpdateMinistryRequest, resolveLeader: ((String) -> Member?)?)
fun Ministry.toSummaryDto(): MinistrySummaryDto
fun Ministry.toDto(): MinistryDto
fun Member.toLeaderDto(): LeaderDto
fun MinistryRegistration.toDto(): RegistrationDto
```

---

## 9. Tests

File: `MinistryServiceTest.kt` — 12 tests

Key scenarios covered:
- `createMinistry` — happy path, 409 duplicate name
- `getMinistries` — filter by active, null returns all
- `getMinistry` — 404 not found
- `updateMinistry` — patches non-null fields only, deactivate via isActive flag
- `deactivateMinistry` — sets isMinistryActive=false, 404 not found
- `registerSelf` — happy path, 409 duplicate, 400 inactive ministry
- `withdrawRegistration` — 403 wrong member, soft delete own registration

---

## 10. Files Delivered

| File | Action |
|------|--------|
| `domain/Ministry.kt` | updated — shortDescription + longDescription split |
| `domain/MinistryRegistration.kt` | updated — member as ManyToOne FK (was memberId: Long) |
| `repository/MinistryRepository.kt` | new |
| `repository/MinistryRegistrationRepository.kt` | new |
| `api/v1/dto/MinistryDtos.kt` | new |
| `api/MinistryMappers.kt` | new |
| `api/v1/MinistryController.kt` | new |
| `service/MinistryService.kt` | new |
| `db/migration/V1774598535211__ministry_schema_align_spec.sql` | new |
| `test/.../MinistryServiceTest.kt` | new — 12 tests |
