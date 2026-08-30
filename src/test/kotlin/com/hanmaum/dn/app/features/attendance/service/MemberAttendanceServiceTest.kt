package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class MemberAttendanceServiceTest {
    @Mock private lateinit var definitionRepo: AttendanceDefinitionRepository

    @Mock private lateinit var logRepo: AttendanceLogRepository

    @Mock private lateinit var memberRepo: MemberRepository

    private lateinit var service: MemberAttendanceService

    private val berlinZone = ZoneId.of("Europe/Berlin")

    // Fixed clock: Sunday 2026-08-30, 12:00 Berlin. August 2026 has five Sundays
    // (2, 9, 16, 23, 30) and 35 Sundays have passed since 1 January.
    private val clock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), berlinZone)
    private val today = LocalDate.of(2026, 8, 30)

    private lateinit var sundayService: AttendanceDefinition

    @BeforeEach
    fun setUp() {
        service = MemberAttendanceService(definitionRepo, logRepo, memberRepo, clock)
        sundayService = makeDefinition(id = 1L, title = "주일예배", dayOfWeek = DayOfWeek.SUNDAY)
    }

    // ─── getHistory ───────────────────────────────────────────────────────────

    @Test
    fun `getHistory marks every scheduled sunday attended or missed, newest first`() {
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(
            LocalDate.of(2026, 8, 1),
            today,
            log(sundayService, LocalDate.of(2026, 8, 2)),
            log(sundayService, LocalDate.of(2026, 8, 9)),
            log(sundayService, LocalDate.of(2026, 8, 16)),
        )

        val result = service.getHistory("kc-001", LocalDate.of(2026, 8, 1), today)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 23),
                LocalDate.of(2026, 8, 16),
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 2),
            ),
            result.entries.map { it.date },
        )
        assertEquals(listOf(false, false, true, true, true), result.entries.map { it.checkedIn })
        assertEquals("주일예배", result.entries[0].definitionTitle)
        assertEquals(sundayService.publicId.toString(), result.entries[0].definitionPublicId)
    }

    @Test
    fun `getHistory exposes the check-in timestamp only for occurrences that happened`() {
        val checkedInAt = Instant.parse("2026-08-16T08:05:00Z")
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(
            LocalDate.of(2026, 8, 16),
            today,
            log(sundayService, LocalDate.of(2026, 8, 16), createdAt = checkedInAt),
        )

        val result = service.getHistory("kc-001", LocalDate.of(2026, 8, 16), today)

        val attended = result.entries.single { it.checkedIn }
        val missed = result.entries.first { !it.checkedIn }
        assertEquals(checkedInAt, attended.checkedInAt)
        assertNull(missed.checkedInAt)
    }

    @Test
    fun `getHistory defaults to the last 90 days ending today`() {
        givenMember()
        givenActiveDefinitions(sundayService)
        val expectedFrom = today.minusDays(MemberAttendanceService.DEFAULT_WINDOW_DAYS)
        givenLogs(expectedFrom, today)

        val result = service.getHistory("kc-001", null, null)

        assertEquals(expectedFrom, result.from)
        assertEquals(today, result.to)
    }

    @Test
    fun `getHistory never reports a future occurrence as missed`() {
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(LocalDate.of(2026, 8, 1), today)

        val result = service.getHistory("kc-001", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31))

        assertEquals(today, result.to)
        assertTrue(result.entries.none { it.date.isAfter(today) })
    }

    @Test
    fun `getHistory keeps a check-in against a since-deactivated definition`() {
        val retiredWeeknight = makeDefinition(id = 2L, title = "수요예배", dayOfWeek = DayOfWeek.WEDNESDAY)
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(
            LocalDate.of(2026, 8, 1),
            today,
            log(retiredWeeknight, LocalDate.of(2026, 8, 5)),
        )

        val result = service.getHistory("kc-001", LocalDate.of(2026, 8, 1), today)

        val wednesday = result.entries.single { it.date == LocalDate.of(2026, 8, 5) }
        assertTrue(wednesday.checkedIn)
        assertEquals("수요예배", wednesday.definitionTitle)
        // The retired definition contributes no 미출석 rows for the Wednesdays not attended.
        assertEquals(1, result.entries.count { it.definitionTitle == "수요예배" })
    }

    @Test
    fun `getHistory returns nothing when no definition is active and nothing was logged`() {
        givenMember()
        givenActiveDefinitions()
        givenLogs(LocalDate.of(2026, 8, 1), today)

        val result = service.getHistory("kc-001", LocalDate.of(2026, 8, 1), today)

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `getHistory rejects a start date after the end date`() {
        givenMember()

        val ex =
            assertThrows<ResponseStatusException> {
                service.getHistory("kc-001", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 10))
            }

        assertEquals(400, ex.statusCode.value())
        assertEquals("조회 시작일은 종료일보다 늦을 수 없습니다.", ex.reason)
    }

    @Test
    fun `getHistory rejects a range longer than a year`() {
        givenMember()

        val ex =
            assertThrows<ResponseStatusException> {
                service.getHistory("kc-001", today.minusDays(MemberAttendanceService.MAX_RANGE_DAYS + 1), today)
            }

        assertEquals(400, ex.statusCode.value())
        assertEquals("조회 기간은 최대 366일입니다.", ex.reason)
    }

    @Test
    fun `getHistory fails when the token has no member row`() {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-unknown")).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.getHistory("kc-unknown", null, null) }
    }

    // ─── getSummary ───────────────────────────────────────────────────────────

    @Test
    fun `getSummary counts the calendar month in full and the year only up to today`() {
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(
            LocalDate.of(2026, 1, 1),
            today,
            log(sundayService, LocalDate.of(2026, 8, 2)),
            log(sundayService, LocalDate.of(2026, 8, 9)),
            log(sundayService, LocalDate.of(2026, 8, 16)),
        )

        val result = service.getSummary("kc-001")

        assertEquals(3, result.monthAttended)
        // August 2026 has five Sundays; the two still ahead are part of the tile's total.
        assertEquals(5, result.monthTotal)
        assertEquals(3, result.yearAttended)
        // 35 Sundays between 1 January and today — the future ones must not dilute the rate.
        assertEquals(35, result.yearToDateTotal)
        assertEquals(0.086, result.rate)
    }

    @Test
    fun `getSummary reports zero rather than dividing by zero when nothing is scheduled`() {
        givenMember()
        givenActiveDefinitions()
        givenLogs(LocalDate.of(2026, 1, 1), today)

        val result = service.getSummary("kc-001")

        assertEquals(0, result.monthTotal)
        assertEquals(0, result.yearToDateTotal)
        assertEquals(0.0, result.rate)
    }

    @Test
    fun `getSummary ignores a check-in that no active definition schedules`() {
        val retiredWeeknight = makeDefinition(id = 2L, title = "수요예배", dayOfWeek = DayOfWeek.WEDNESDAY)
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(
            LocalDate.of(2026, 1, 1),
            today,
            log(sundayService, LocalDate.of(2026, 8, 2)),
            log(retiredWeeknight, LocalDate.of(2026, 8, 5)),
        )

        val result = service.getSummary("kc-001")

        // A numerator may never exceed its denominator, so the retired midweek service
        // is left out of the counters even though the history still lists it.
        assertEquals(1, result.monthAttended)
        assertEquals(1, result.yearAttended)
        assertTrue(result.yearAttended <= result.yearToDateTotal)
    }

    @Test
    fun `getSummary counts nothing when the member never checked in`() {
        givenMember()
        givenActiveDefinitions(sundayService)
        givenLogs(LocalDate.of(2026, 1, 1), today)

        val result = service.getSummary("kc-001")

        assertEquals(0, result.monthAttended)
        assertEquals(0, result.yearAttended)
        assertEquals(5, result.monthTotal)
        assertEquals(0.0, result.rate)
        assertFalse(result.rate > 0.0)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun givenMember(
        id: Long = 1L,
        keycloakId: String = "kc-001",
    ) {
        val member = Member(lastName = "김", firstName = "철수")
        setId(member, id)
        setField(member, Member::class.java, "keycloakId", keycloakId)
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakId)).thenReturn(member)
    }

    private fun givenActiveDefinitions(vararg definitions: AttendanceDefinition) {
        `when`(definitionRepo.findAll(eq(true))).thenReturn(definitions.toList())
    }

    private fun givenLogs(
        from: LocalDate,
        to: LocalDate,
        vararg logs: AttendanceLog,
    ) {
        `when`(logRepo.findMemberLogsBetween(any(), eq(from), eq(to))).thenReturn(logs.toList())
    }

    private fun log(
        definition: AttendanceDefinition,
        date: LocalDate,
        createdAt: Instant = Instant.parse("2026-08-16T08:00:00Z"),
    ): AttendanceLog {
        val entry = AttendanceLog(definition = definition, member = null, attendanceDate = date)
        entry.createdAt = createdAt
        return entry
    }

    private fun makeDefinition(
        id: Long,
        title: String,
        dayOfWeek: DayOfWeek,
    ): AttendanceDefinition {
        val definition =
            AttendanceDefinition(
                title = title,
                dayOfWeek = dayOfWeek,
                windowStart = LocalTime.of(9, 0),
                windowEnd = LocalTime.of(12, 0),
            )
        setId(definition, id)
        return definition
    }

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val field: Field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private fun setField(
        target: Any,
        declaringClass: Class<*>,
        name: String,
        value: Any?,
    ) {
        val field: Field = declaringClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
