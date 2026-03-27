# F3 Attendance Tracking — Backend Design Spec

**Date:** 2026-03-27
**Branch:** feature/attendance-backend
**Status:** Approved

---

## 1. Goal

Implement the F3 Attendance Tracking backend: allow admins to define recurring attendance windows, allow members to check in via JWT, and allow admins to query logs and stats.

---

## 2. Domain Model

### `AttendanceDefinition`

Represents a recurring attendance event (e.g., Sunday Service 10:00–12:00).

| Field | Type | Constraint |
|-------|------|------------|
| `id` | Long | PK, from BaseEntity |
| `publicId` | UUID | unique, not null |
| `title` | String | NotBlank, max 100 |
| `dayOfWeek` | DayOfWeek | not null |
| `windowStart` | LocalTime | not null |
| `windowEnd` | LocalTime | not null, must be after windowStart |
| `isActive` | Boolean | default true |
| `createdAt` | Instant | from BaseEntity |
| `updatedAt` | Instant | from BaseEntity |
| `deletedAt` | Instant | null = active; from BaseEntity |

### `AttendanceLog`

One row per member check-in.

| Field | Type | Constraint |
|-------|------|------------|
| `id` | Long | PK, from BaseEntity |
| `publicId` | UUID | unique, not null |
| `definition` | AttendanceDefinition | ManyToOne FK, not null |
| `member` | Member | ManyToOne FK, not null |
| `attendanceDate` | LocalDate | not null |
| `attended` | Boolean | default true |
| `createdAt` | Instant | from BaseEntity |
| `updatedAt` | Instant | from BaseEntity |
| `deletedAt` | Instant | from BaseEntity |

**Unique constraint:** `(member_id, definition_id, attendance_date)`

---

## 3. Database Migration

New file: `V{timestamp}__attendance_spec_align.sql`

### `attendance_definitions`
- Add `public_id UUID NOT NULL DEFAULT gen_random_uuid()` + unique constraint
- Rename `start_time` → `window_start`
- Rename `end_time` → `window_end`
- `ALTER COLUMN title SET NOT NULL`

### `attendance_logs`
- `TRUNCATE attendance_logs` (pre-production, expected empty)
- Add `definition_id BIGINT NOT NULL REFERENCES attendance_definitions(id)`
- Rename `date` → `attendance_date`
- Drop `category` column
- Drop `status` column
- Add `attended BOOLEAN NOT NULL DEFAULT TRUE`
- Add `UNIQUE(member_id, definition_id, attendance_date)`

---

## 4. Repository Layer

**`AttendanceDefinitionRepository`**
- `findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<AttendanceDefinition>`
- `findAllByDeletedAtIsNull(): List<AttendanceDefinition>`
- `findAllByIsActiveTrueAndDeletedAtIsNull(): List<AttendanceDefinition>`
- `findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(day: DayOfWeek): List<AttendanceDefinition>`

**`AttendanceLogRepository`**
- `existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(memberId: Long, definitionId: Long, date: LocalDate): Boolean`
- `findAllByMemberIdAndAttendanceDateBetweenAndDeletedAtIsNull(memberId: Long, from: LocalDate, to: LocalDate): List<AttendanceLog>`
- `findAllByAttendanceDateBetweenAndDeletedAtIsNull(from: LocalDate, to: LocalDate): List<AttendanceLog>`

---

## 5. DTO Layer

### Request DTOs

```kotlin
data class CreateDefinitionRequest(
    @field:NotBlank @field:Size(max = 100) val title: String,
    val dayOfWeek: DayOfWeek,
    val windowStart: LocalTime,
    val windowEnd: LocalTime
)

data class UpdateDefinitionRequest(
    @field:Size(max = 100) val title: String?,
    val dayOfWeek: DayOfWeek?,
    val windowStart: LocalTime?,
    val windowEnd: LocalTime?,
    val isActive: Boolean?
)
// CheckInRequest has no body — member resolved from JWT
```

### Response DTOs

```kotlin
data class DefinitionDto(publicId, title, dayOfWeek, windowStart, windowEnd, isActive)
data class AttendanceLogDto(publicId, definitionTitle, memberName, attendanceDate, attended)
data class AttendanceStatsDto(memberPublicId, memberName, attendanceCount)
```

---

## 6. Service Layer

**`AttendanceService`**

| Method | Guards |
|--------|--------|
| `createDefinition(req)` | windowEnd must be after windowStart (400) |
| `getDefinitions(activeOnly: Boolean)` | — |
| `getDefinition(publicId)` | 404 if not found |
| `updateDefinition(publicId, req)` | 404 if not found |
| `deactivateDefinition(publicId)` | 404 if not found; sets `deletedAt` |
| `checkIn(keycloakSub)` | Resolve member from JWT (404); find active definitions matching today's dayOfWeek + current time within window (400 if none found); if multiple match, use the first one (rare edge case — church typically has one window per day); 409 if already checked in for that definition today |
| `getLogs(memberId?, from?, to?, definitionId?)` | — |
| `getMyLogs(keycloakSub)` | Resolve member from JWT (404) |
| `getStats(from, to)` | Returns count per member for given date range |

---

## 7. Controller Layer

Base path: `/api/v1/attendance`

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/definitions` | ADMIN | Create definition |
| GET | `/definitions` | MEMBER | List; `?active=true` |
| PATCH | `/definitions/{id}` | ADMIN | Update definition |
| DELETE | `/definitions/{id}` | ADMIN | Deactivate (soft delete) |
| POST | `/check-in` | MEMBER | Check in via JWT; no request body |
| GET | `/logs` | ADMIN | All logs; `?memberId ?from ?to ?definitionId` |
| GET | `/logs/me` | MEMBER | Own logs |
| GET | `/stats` | ADMIN | Attendance count per member; `?from ?to` |

All responses wrapped in `ApiResponse<T>`. All request bodies use `@Valid`.

---

## 8. Mapper Layer

File: `api/AttendanceMappers.kt`

```kotlin
fun AttendanceDefinition.toDto(): DefinitionDto
fun AttendanceLog.toDto(): AttendanceLogDto
fun List<AttendanceLog>.toStatsDto(): List<AttendanceStatsDto>  // group + count by member
```

---

## 9. Tests

File: `test/.../attendance/service/AttendanceServiceTest.kt` (rewrite in-place)

| Test | Scenario |
|------|----------|
| `createDefinition - happy path` | saves and returns DefinitionDto |
| `createDefinition - windowEnd before windowStart` | throws 400 |
| `checkIn - happy path` | creates log, returns LogDto |
| `checkIn - member not found` | throws 404 |
| `checkIn - no active definition for current window` | throws 400 |
| `checkIn - already checked in today` | throws 409 |
| `deactivateDefinition - happy path` | sets deletedAt |
| `deactivateDefinition - not found` | throws 404 |
| `getMyLogs - happy path` | returns member's own logs |
| `getStats - happy path` | returns count per member |

---

## 10. Files Changed

| File | Action |
|------|--------|
| `domain/AttendanceDefinition.kt` | rewrite in-place |
| `domain/AttendanceLog.kt` | rewrite in-place |
| `repository/AttendanceRepositories.kt` | rewrite in-place |
| `api/v1/dto/AttendanceDtos.kt` | rewrite in-place |
| `api/v1/AttendanceController.kt` | rewrite in-place |
| `service/AttendanceService.kt` | rewrite in-place |
| `api/AttendanceMappers.kt` | **new file** |
| `db/migration/V{ts}__attendance_spec_align.sql` | **new file** |
| `test/.../AttendanceServiceTest.kt` | rewrite in-place |
| `CHANGELOG.md` | update F3 entry |
