# F3 Attendance Tracking Backend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the existing prototype attendance module to meet the F3 spec: server-side check-in time-window validation, JWT-based auth, proper FK relationships, and all 8 API endpoints.

**Architecture:** Rewrite all existing attendance files in-place (same paths, new content). One new Flyway migration aligns the DB schema. One new `AttendanceMappers.kt` follows the F1/F2 mapper pattern. Tests cover all service-layer guards with Mockito unit tests.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, Mockito

---

## File Map

| Action | File |
|--------|------|
| **New** | `backend/src/main/resources/db/migration/V1774647410890__attendance_spec_align.sql` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/domain/AttendanceDefinition.kt` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/domain/AttendanceLog.kt` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/repository/AttendanceRepositories.kt` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/dto/AttendanceDtos.kt` |
| **New** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/AttendanceMappers.kt` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceService.kt` |
| **Rewrite** | `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/AttendanceController.kt` |
| **Rewrite** | `backend/src/test/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceServiceTest.kt` |

---

## Task 1: Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V1774647410890__attendance_spec_align.sql`

- [ ] **Step 1.1: Write the migration SQL**

```sql
-- V1774647410890: Align attendance schema with F3 spec
--
-- attendance_definitions:
--   Add public_id UUID, rename start_time→window_start, end_time→window_end,
--   make title NOT NULL
--
-- attendance_logs:
--   TRUNCATE (pre-production, empty), add definition_id FK NOT NULL,
--   rename date→attendance_date, drop category + status,
--   add attended BOOLEAN, add unique constraint

-- ── attendance_definitions ──────────────────────────────────────────────────

ALTER TABLE attendance_definitions
    ADD COLUMN IF NOT EXISTS public_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE attendance_definitions
    DROP CONSTRAINT IF EXISTS uq_attendance_definitions_public_id;

ALTER TABLE attendance_definitions
    ADD CONSTRAINT uq_attendance_definitions_public_id UNIQUE (public_id);

ALTER TABLE attendance_definitions
    RENAME COLUMN start_time TO window_start;

ALTER TABLE attendance_definitions
    RENAME COLUMN end_time TO window_end;

UPDATE attendance_definitions SET title = 'Unnamed' WHERE title IS NULL;

ALTER TABLE attendance_definitions
    ALTER COLUMN title SET NOT NULL;

-- ── attendance_logs ──────────────────────────────────────────────────────────

-- Pre-production: truncate to allow schema changes without data migration pain
TRUNCATE TABLE attendance_logs;

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS definition_id BIGINT
        REFERENCES attendance_definitions(id) ON DELETE CASCADE;

-- Make definition_id NOT NULL now that table is empty
ALTER TABLE attendance_logs
    ALTER COLUMN definition_id SET NOT NULL;

ALTER TABLE attendance_logs
    RENAME COLUMN date TO attendance_date;

ALTER TABLE attendance_logs
    DROP COLUMN IF EXISTS category;

ALTER TABLE attendance_logs
    DROP COLUMN IF EXISTS status;

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS attended BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE attendance_logs
    DROP CONSTRAINT IF EXISTS uq_attendance_log;

ALTER TABLE attendance_logs
    ADD CONSTRAINT uq_attendance_log
        UNIQUE (member_id, definition_id, attendance_date);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_definition_id ON attendance_logs (definition_id);
CREATE INDEX IF NOT EXISTS idx_attendance_definitions_public_id ON attendance_definitions (public_id);
```

- [ ] **Step 1.2: Commit**

```bash
cd /path/to/dn-app
git add backend/src/main/resources/db/migration/V1774647410890__attendance_spec_align.sql
git commit -m "feat(attendance): add Flyway migration for F3 schema alignment"
```

---

## Task 2: Domain Entities

**Files:**
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/domain/AttendanceDefinition.kt`
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/domain/AttendanceLog.kt`

- [ ] **Step 2.1: Rewrite `AttendanceDefinition.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime

@Entity
@Table(name = "attendance_definitions")
class AttendanceDefinition(
    @Column(name = "title", nullable = false, length = 100)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    var dayOfWeek: DayOfWeek,
    @Column(name = "window_start", nullable = false)
    var windowStart: LocalTime,
    @Column(name = "window_end", nullable = false)
    var windowEnd: LocalTime,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseEntity()
```

- [ ] **Step 2.2: Rewrite `AttendanceLog.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "attendance_logs",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_attendance_log",
            columnNames = ["member_id", "definition_id", "attendance_date"],
        ),
    ],
)
class AttendanceLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    val definition: AttendanceDefinition,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @Column(name = "attended", nullable = false)
    val attended: Boolean = true,
) : BaseEntity()
```

- [ ] **Step 2.3: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/domain/
git commit -m "feat(attendance): rewrite domain entities per F3 spec"
```

---

## Task 3: Repositories

**Files:**
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/repository/AttendanceRepositories.kt`

- [ ] **Step 3.1: Rewrite `AttendanceRepositories.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.util.Optional
import java.util.UUID
import java.time.LocalDate

@Repository
interface AttendanceDefinitionRepository : JpaRepository<AttendanceDefinition, Long> {

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<AttendanceDefinition>

    /** All non-deleted definitions; [activeOnly] true filters to isActive=true only. */
    @Query(
        """
        SELECT d FROM AttendanceDefinition d
        WHERE d.deletedAt IS NULL
          AND (:activeOnly = false OR d.isActive = true)
        ORDER BY d.dayOfWeek ASC, d.windowStart ASC
        """,
    )
    fun findAll(@Param("activeOnly") activeOnly: Boolean): List<AttendanceDefinition>

    /** Active definitions for a given day of week — used during check-in window lookup. */
    fun findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(dayOfWeek: DayOfWeek): List<AttendanceDefinition>
}

@Repository
interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {

    /** Duplicate guard: has this member already checked in for this definition on this date? */
    fun existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(
        memberId: Long,
        definitionId: Long,
        attendanceDate: LocalDate,
    ): Boolean

    /** Own history — all logs for a member ordered by date descending. */
    fun findAllByMemberIdAndDeletedAtIsNullOrderByAttendanceDateDesc(memberId: Long): List<AttendanceLog>

    /** Admin: logs filtered by optional member, definition, and date range. */
    @Query(
        """
        SELECT l FROM AttendanceLog l
        WHERE l.deletedAt IS NULL
          AND (:memberId IS NULL OR l.member.id = :memberId)
          AND (:definitionId IS NULL OR l.definition.id = :definitionId)
          AND l.attendanceDate >= :from
          AND l.attendanceDate <= :to
        ORDER BY l.attendanceDate DESC
        """,
    )
    fun findForAdmin(
        @Param("memberId") memberId: Long?,
        @Param("definitionId") definitionId: Long?,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<AttendanceLog>

    /** Stats: all logs in a date range for grouping by member. */
    @Query(
        """
        SELECT l FROM AttendanceLog l
        WHERE l.deletedAt IS NULL
          AND l.attendanceDate >= :from
          AND l.attendanceDate <= :to
        """,
    )
    fun findForStats(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<AttendanceLog>
}
```

- [ ] **Step 3.2: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/repository/
git commit -m "feat(attendance): rewrite repositories per F3 spec"
```

---

## Task 4: DTOs

**Files:**
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/dto/AttendanceDtos.kt`

- [ ] **Step 4.1: Rewrite `AttendanceDtos.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ─── Response DTOs ────────────────────────────────────────────────────────────

data class DefinitionDto(
    val publicId: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val isActive: Boolean,
)

data class AttendanceLogDto(
    val publicId: String,
    val definitionPublicId: String,
    val definitionTitle: String,
    val memberPublicId: String,
    val memberName: String,
    val attendanceDate: LocalDate,
    val attended: Boolean,
)

data class AttendanceStatsDto(
    val memberPublicId: String,
    val memberName: String,
    val attendanceCount: Int,
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateDefinitionRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 100, message = "제목은 최대 100자입니다.")
    val title: String,
    @field:NotNull(message = "요일은 필수입니다.")
    val dayOfWeek: DayOfWeek,
    @field:NotNull(message = "시작 시간은 필수입니다.")
    val windowStart: LocalTime,
    @field:NotNull(message = "종료 시간은 필수입니다.")
    val windowEnd: LocalTime,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateDefinitionRequest(
    @field:Size(max = 100)
    val title: String? = null,
    val dayOfWeek: DayOfWeek? = null,
    val windowStart: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    val isActive: Boolean? = null,
)
// CheckInRequest has no body — member resolved entirely from JWT subject.
```

- [ ] **Step 4.2: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/dto/
git commit -m "feat(attendance): rewrite DTOs per F3 spec"
```

---

## Task 5: Mappers

**Files:**
- Create: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/AttendanceMappers.kt`

- [ ] **Step 5.1: Create `AttendanceMappers.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.api

import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog

fun AttendanceDefinition.toDto(): DefinitionDto =
    DefinitionDto(
        publicId = this.publicId.toString(),
        title = this.title,
        dayOfWeek = this.dayOfWeek,
        windowStart = this.windowStart,
        windowEnd = this.windowEnd,
        isActive = this.isActive,
    )

fun AttendanceLog.toDto(): AttendanceLogDto =
    AttendanceLogDto(
        publicId = this.publicId.toString(),
        definitionPublicId = this.definition.publicId.toString(),
        definitionTitle = this.definition.title,
        memberPublicId = this.member.publicId.toString(),
        memberName = "${this.member.lastName}${this.member.firstName}",
        attendanceDate = this.attendanceDate,
        attended = this.attended,
    )

fun List<AttendanceLog>.toStatsDto(): List<AttendanceStatsDto> =
    this
        .groupBy { it.member }
        .map { (member, logs) ->
            AttendanceStatsDto(
                memberPublicId = member.publicId.toString(),
                memberName = "${member.lastName}${member.firstName}",
                attendanceCount = logs.count { it.attended },
            )
        }
        .sortedByDescending { it.attendanceCount }
```

- [ ] **Step 5.2: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/AttendanceMappers.kt
git commit -m "feat(attendance): add AttendanceMappers extension functions"
```

---

## Task 6: Service Tests (Write Failing First)

**Files:**
- Rewrite: `backend/src/test/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceServiceTest.kt`

- [ ] **Step 6.1: Rewrite the test file**

```kotlin
package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AttendanceServiceTest {

    @Mock private lateinit var definitionRepo: AttendanceDefinitionRepository
    @Mock private lateinit var logRepo: AttendanceLogRepository
    @Mock private lateinit var memberRepo: MemberRepository

    private lateinit var service: AttendanceService

    @BeforeEach
    fun setUp() {
        service = AttendanceService(definitionRepo, logRepo, memberRepo)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeDefinition(
        id: Long = 1L,
        dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
        windowStart: LocalTime = LocalTime.of(10, 0),
        windowEnd: LocalTime = LocalTime.of(12, 0),
        isActive: Boolean = true,
    ): AttendanceDefinition {
        val d = AttendanceDefinition(
            title = "주일예배",
            dayOfWeek = dayOfWeek,
            windowStart = windowStart,
            windowEnd = windowEnd,
            isActive = isActive,
        )
        setId(d, id)
        return d
    }

    private fun makeMember(id: Long = 1L, keycloakId: String = "kc-001"): Member {
        val m = Member(lastName = "김", firstName = "철수")
        setId(m, id)
        setField(m, Member::class.java, "keycloakId", keycloakId)
        return m
    }

    private fun setId(entity: Any, id: Long) {
        val f: Field = entity.javaClass.superclass.getDeclaredField("id")
        f.isAccessible = true
        f.set(entity, id)
    }

    private fun setField(entity: Any, clazz: Class<*>, name: String, value: Any?) {
        val f: Field = clazz.getDeclaredField(name)
        f.isAccessible = true
        f.set(entity, value)
    }

    // ─── createDefinition ─────────────────────────────────────────────────────

    @Test
    fun `createDefinition - happy path returns DefinitionDto`() {
        val req = CreateDefinitionRequest(
            title = "주일예배",
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
        )
        val saved = makeDefinition()
        `when`(definitionRepo.save(any())).thenReturn(saved)

        val result = service.createDefinition(req)

        assertEquals("주일예배", result.title)
        verify(definitionRepo).save(any())
    }

    @Test
    fun `createDefinition - 400 when windowEnd is before windowStart`() {
        val req = CreateDefinitionRequest(
            title = "잘못된 시간",
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(12, 0),
            windowEnd = LocalTime.of(10, 0),
        )

        val ex = assertThrows<ResponseStatusException> { service.createDefinition(req) }
        assertEquals(400, ex.statusCode.value())
        verify(definitionRepo, never()).save(any())
    }

    // ─── updateDefinition ─────────────────────────────────────────────────────

    @Test
    fun `updateDefinition - patches non-null fields only`() {
        val def = makeDefinition()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(def.publicId))
            .thenReturn(Optional.of(def))

        val result = service.updateDefinition(def.publicId, UpdateDefinitionRequest(title = "새 예배"))

        assertEquals("새 예배", result.title)
        assertEquals(DayOfWeek.SUNDAY, result.dayOfWeek) // unchanged
    }

    @Test
    fun `updateDefinition - 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.updateDefinition(id, UpdateDefinitionRequest()) }
    }

    // ─── deactivateDefinition ─────────────────────────────────────────────────

    @Test
    fun `deactivateDefinition - sets deletedAt`() {
        val def = makeDefinition()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(def.publicId))
            .thenReturn(Optional.of(def))

        service.deactivateDefinition(def.publicId)

        assertNotNull(def.deletedAt)
    }

    @Test
    fun `deactivateDefinition - 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.deactivateDefinition(id) }
    }

    // ─── checkIn ──────────────────────────────────────────────────────────────

    @Test
    fun `checkIn - happy path creates log and returns dto`() {
        val member = makeMember()
        // Use a fixed clock via the service's clock field — see service implementation
        // For the test, stub a definition that matches "now" (SUNDAY 10:00–12:00)
        val def = makeDefinition(
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.MIN,
            windowEnd = LocalTime.MAX,
        )
        val log = AttendanceLog(definition = def, member = member, attendanceDate = LocalDate.now())
        setId(log, 10L)

        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(any())).thenReturn(listOf(def))
        `when`(logRepo.existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(1L, 1L, any())).thenReturn(false)
        `when`(logRepo.save(any())).thenReturn(log)

        val result = service.checkIn("kc-001")

        assertEquals("주일예배", result.definitionTitle)
        verify(logRepo).save(any())
    }

    @Test
    fun `checkIn - 404 when member not found`() {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("unknown")).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.checkIn("unknown") }
        verify(logRepo, never()).save(any())
    }

    @Test
    fun `checkIn - 400 when no active definition window matches current time`() {
        val member = makeMember()
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        // Return a definition whose window is in the past (already ended)
        val def = makeDefinition(
            windowStart = LocalTime.of(0, 0),
            windowEnd = LocalTime.of(0, 1),
        )
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(any())).thenReturn(listOf(def))

        val ex = assertThrows<ResponseStatusException> { service.checkIn("kc-001") }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `checkIn - 409 when already checked in today`() {
        val member = makeMember()
        val def = makeDefinition(
            windowStart = LocalTime.MIN,
            windowEnd = LocalTime.MAX,
        )

        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(any())).thenReturn(listOf(def))
        `when`(logRepo.existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(1L, 1L, any())).thenReturn(true)

        val ex = assertThrows<ResponseStatusException> { service.checkIn("kc-001") }
        assertEquals(409, ex.statusCode.value())
    }

    // ─── getMyLogs ────────────────────────────────────────────────────────────

    @Test
    fun `getMyLogs - returns own logs ordered by date desc`() {
        val member = makeMember()
        val def = makeDefinition()
        val log = AttendanceLog(definition = def, member = member, attendanceDate = LocalDate.now())
        setId(log, 1L)

        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(logRepo.findAllByMemberIdAndDeletedAtIsNullOrderByAttendanceDateDesc(1L)).thenReturn(listOf(log))

        val result = service.getMyLogs("kc-001")

        assertEquals(1, result.size)
        assertEquals("주일예배", result[0].definitionTitle)
    }

    @Test
    fun `getMyLogs - 404 when member not found`() {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("unknown")).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.getMyLogs("unknown") }
    }

    // ─── getStats ─────────────────────────────────────────────────────────────

    @Test
    fun `getStats - returns attendance count per member`() {
        val member = makeMember()
        val def = makeDefinition()
        val log1 = AttendanceLog(definition = def, member = member, attendanceDate = LocalDate.now())
        val log2 = AttendanceLog(definition = def, member = member, attendanceDate = LocalDate.now().minusDays(7))
        setId(log1, 1L)
        setId(log2, 2L)

        val from = LocalDate.now().minusDays(30)
        val to = LocalDate.now()
        `when`(logRepo.findForStats(from, to)).thenReturn(listOf(log1, log2))

        val result = service.getStats(from, to)

        assertEquals(1, result.size)
        assertEquals(2, result[0].attendanceCount)
        assertEquals("김철수", result[0].memberName)
    }
}
```

- [ ] **Step 6.2: Run tests — verify they fail to compile (service doesn't match yet)**

```bash
cd backend && ./gradlew test --tests "com.hanmaum.dn.app.features.attendance.service.AttendanceServiceTest" 2>&1 | tail -20
```

Expected: compilation errors referencing missing service methods.

- [ ] **Step 6.3: Commit failing tests**

```bash
git add backend/src/test/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceServiceTest.kt
git commit -m "test(attendance): write failing service tests for F3"
```

---

## Task 7: Service Implementation

**Files:**
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceService.kt`

- [ ] **Step 7.1: Rewrite `AttendanceService.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.toDto
import com.hanmaum.dn.app.features.attendance.api.toStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AttendanceService(
    private val definitionRepo: AttendanceDefinitionRepository,
    private val logRepo: AttendanceLogRepository,
    private val memberRepo: MemberRepository,
) {
    // ─── Definition CRUD ───────────────────────────────────────────────────────

    @Transactional
    fun createDefinition(req: CreateDefinitionRequest): DefinitionDto {
        if (!req.windowEnd.isAfter(req.windowStart)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간 이후여야 합니다.")
        }
        val definition = AttendanceDefinition(
            title = req.title,
            dayOfWeek = req.dayOfWeek,
            windowStart = req.windowStart,
            windowEnd = req.windowEnd,
        )
        return definitionRepo.save(definition).toDto()
    }

    @Transactional(readOnly = true)
    fun getDefinitions(activeOnly: Boolean): List<DefinitionDto> =
        definitionRepo.findAll(activeOnly).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getDefinition(publicId: UUID): DefinitionDto =
        definitionRepo
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $publicId") }
            .toDto()

    @Transactional
    fun updateDefinition(publicId: UUID, req: UpdateDefinitionRequest): DefinitionDto {
        val definition = definitionRepo
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $publicId") }

        req.title?.let { definition.title = it }
        req.dayOfWeek?.let { definition.dayOfWeek = it }
        req.windowStart?.let { definition.windowStart = it }
        req.windowEnd?.let { definition.windowEnd = it }
        req.isActive?.let { definition.isActive = it }

        return definition.toDto()
    }

    @Transactional
    fun deactivateDefinition(publicId: UUID) {
        val definition = definitionRepo
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $publicId") }
        definition.deletedAt = LocalDateTime.now()
    }

    // ─── Check-in ─────────────────────────────────────────────────────────────

    /**
     * Check in the calling member.
     * Server validates: current time is within an active definition's window on the correct day.
     * 400 if outside window. 409 if already checked in today for that definition.
     */
    @Transactional
    fun checkIn(keycloakSubject: String): AttendanceLogDto {
        val member = memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek

        val matchingDefinition = definitionRepo
            .findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(currentDay)
            .firstOrNull { def ->
                currentTime.isAfter(def.windowStart) && currentTime.isBefore(def.windowEnd)
            } ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "현재 활성화된 출석 체크인 시간이 없습니다.",
        )

        val duplicate = logRepo.existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(
            member.id!!,
            matchingDefinition.id!!,
            today,
        )
        if (duplicate) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 오늘 출석 체크인 했습니다.")
        }

        val log = logRepo.save(
            AttendanceLog(
                definition = matchingDefinition,
                member = member,
                attendanceDate = today,
                attended = true,
            ),
        )
        return log.toDto()
    }

    // ─── Logs ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getLogs(
        memberPublicId: UUID?,
        definitionPublicId: UUID?,
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceLogDto> {
        val memberId = memberPublicId?.let {
            memberRepo.findByPublicIdAndDeletedAtIsNull(it)
                .orElseThrow { EntityNotFoundException("Member not found: $it") }
                .id
        }
        val definitionId = definitionPublicId?.let {
            definitionRepo.findByPublicIdAndDeletedAtIsNull(it)
                .orElseThrow { EntityNotFoundException("Definition not found: $it") }
                .id
        }
        return logRepo.findForAdmin(memberId, definitionId, from, to).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getMyLogs(keycloakSubject: String): List<AttendanceLogDto> {
        val member = memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")
        return logRepo.findAllByMemberIdAndDeletedAtIsNullOrderByAttendanceDateDesc(member.id!!)
            .map { it.toDto() }
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getStats(from: LocalDate, to: LocalDate): List<AttendanceStatsDto> =
        logRepo.findForStats(from, to).toStatsDto()
}
```

- [ ] **Step 7.2: Run the tests — verify they pass**

```bash
cd backend && ./gradlew test --tests "com.hanmaum.dn.app.features.attendance.service.AttendanceServiceTest" 2>&1 | tail -30
```

Expected: All 10 tests PASS. If any fail, fix the service before continuing.

- [ ] **Step 7.3: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/service/AttendanceService.kt
git commit -m "feat(attendance): implement AttendanceService — all guards + 8 methods"
```

---

## Task 8: Controller

**Files:**
- Rewrite: `backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/AttendanceController.kt`

- [ ] **Step 8.1: Rewrite `AttendanceController.kt`**

```kotlin
package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    // ─── Definitions ───────────────────────────────────────────────────────────

    /** POST /api/v1/attendance/definitions — ADMIN create */
    @PostMapping("/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    fun createDefinition(
        @Valid @RequestBody request: CreateDefinitionRequest,
    ): ResponseEntity<ApiResponse<DefinitionDto>> {
        val created = attendanceService.createDefinition(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = created, message = "출석 정의가 생성되었습니다."))
    }

    /** GET /api/v1/attendance/definitions?active=true — MEMBER list */
    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    fun getDefinitions(
        @RequestParam(defaultValue = "false") active: Boolean,
    ): ResponseEntity<ApiResponse<List<DefinitionDto>>> {
        val definitions = attendanceService.getDefinitions(active)
        return ResponseEntity.ok(ApiResponse.success(data = definitions))
    }

    /** PATCH /api/v1/attendance/definitions/{publicId} — ADMIN update */
    @PatchMapping("/definitions/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateDefinition(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateDefinitionRequest,
    ): ResponseEntity<ApiResponse<DefinitionDto>> {
        val updated = attendanceService.updateDefinition(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /** DELETE /api/v1/attendance/definitions/{publicId} — ADMIN deactivate (soft delete) */
    @DeleteMapping("/definitions/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateDefinition(
        @PathVariable publicId: UUID,
    ) {
        attendanceService.deactivateDefinition(publicId)
    }

    // ─── Check-in ─────────────────────────────────────────────────────────────

    /** POST /api/v1/attendance/check-in — MEMBER; no request body; member from JWT */
    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    fun checkIn(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<AttendanceLogDto>> {
        val log = attendanceService.checkIn(jwt.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = log, message = "출석 체크인 완료."))
    }

    // ─── Logs ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/attendance/logs — ADMIN
     * Optional query params: memberId, definitionId, from, to
     * Defaults: from = 30 days ago, to = today
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    fun getLogs(
        @RequestParam(required = false) memberId: UUID?,
        @RequestParam(required = false) definitionId: UUID?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
    ): ResponseEntity<ApiResponse<List<AttendanceLogDto>>> {
        val effectiveFrom = from ?: LocalDate.now().minusDays(30)
        val effectiveTo = to ?: LocalDate.now()
        val logs = attendanceService.getLogs(memberId, definitionId, effectiveFrom, effectiveTo)
        return ResponseEntity.ok(ApiResponse.success(data = logs))
    }

    /** GET /api/v1/attendance/logs/me — MEMBER own history */
    @GetMapping("/logs/me")
    @PreAuthorize("isAuthenticated()")
    fun getMyLogs(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<List<AttendanceLogDto>>> {
        val logs = attendanceService.getMyLogs(jwt.subject)
        return ResponseEntity.ok(ApiResponse.success(data = logs))
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/attendance/stats?from=2026-01-01&to=2026-03-27 — ADMIN
     * Returns attendance count per member for the given date range.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    fun getStats(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
    ): ResponseEntity<ApiResponse<List<AttendanceStatsDto>>> {
        val effectiveFrom = from ?: LocalDate.now().minusDays(30)
        val effectiveTo = to ?: LocalDate.now()
        val stats = attendanceService.getStats(effectiveFrom, effectiveTo)
        return ResponseEntity.ok(ApiResponse.success(data = stats))
    }
}
```

- [ ] **Step 8.2: Commit**

```bash
git add backend/src/main/kotlin/com/hanmaum/dn/app/features/attendance/api/v1/AttendanceController.kt
git commit -m "feat(attendance): rewrite controller — /api/v1/attendance, all 8 endpoints"
```

---

## Task 9: Build Verify + Final Commit

- [ ] **Step 9.1: Run full build**

```bash
cd backend && ./gradlew build 2>&1 | tail -40
```

Expected: `BUILD SUCCESSFUL` with 0 errors. If there are compilation errors, fix them before proceeding.

- [ ] **Step 9.2: Run all attendance tests specifically**

```bash
cd backend && ./gradlew test --tests "com.hanmaum.dn.app.features.attendance.*" 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 9.3: Update MVP.md — mark F3 Backend ✅**

In `MVP.md`, find the status table and update F3 backend from 🔄 to ✅:

```
| 3 | Attendance Tracking | ✅ | 🔲 | 🔲 |
```

- [ ] **Step 9.4: Commit MVP status + spec doc update**

```bash
cd /path/to/dn-app
git add MVP.md
git commit -m "docs: mark F3 attendance backend ✅ in MVP status"
```

- [ ] **Step 9.5: Push branch**

```bash
git push origin feature/attendance-backend
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ `AttendanceDefinition` — title, dayOfWeek, windowStart, windowEnd, isActive
- ✅ `AttendanceLog` — definition FK, member FK, attendanceDate, attended bool, unique constraint
- ✅ Flyway migration — public_id on definitions, column renames, definition_id FK, unique constraint
- ✅ POST /definitions (ADMIN, @Valid, 400 windowEnd guard)
- ✅ GET /definitions (MEMBER, ?active filter)
- ✅ PATCH /definitions/{id} (ADMIN, 404)
- ✅ DELETE /definitions/{id} (ADMIN, soft delete, 404)
- ✅ POST /check-in (MEMBER, JWT sub, 400 outside window, 409 duplicate)
- ✅ GET /logs (ADMIN, filters)
- ✅ GET /logs/me (MEMBER, JWT sub, 404)
- ✅ GET /stats (ADMIN, count per member)
- ✅ Mappers as extension functions in `AttendanceMappers.kt`
- ✅ All responses: `ApiResponse<T>`
- ✅ 10 unit tests covering all guards

**Type consistency check:**
- `AttendanceService.checkIn(keycloakSubject: String)` ← controller passes `jwt.subject` ✅
- `AttendanceService.getLogs(memberPublicId: UUID?, definitionPublicId: UUID?, from, to)` ← controller passes `UUID?` ✅
- `logRepo.findForStats(from, to)` → `List<AttendanceLog>` → `.toStatsDto()` ✅
- `definitionRepo.findAll(activeOnly: Boolean)` ← controller passes `active: Boolean` (default false) ✅
