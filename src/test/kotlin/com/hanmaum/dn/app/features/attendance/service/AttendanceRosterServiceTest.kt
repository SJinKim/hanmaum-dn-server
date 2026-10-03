package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateAttendanceLogRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AttendanceRosterServiceTest {
    @Mock private lateinit var definitionRepo: AttendanceDefinitionRepository

    @Mock private lateinit var logRepo: AttendanceLogRepository

    @Mock private lateinit var memberRepo: MemberRepository

    private lateinit var service: AttendanceRosterService

    // Sunday 2026-06-14, 10:30 in Berlin.
    private val sunday = LocalDate.of(2026, 6, 14)
    private val clock = Clock.fixed(Instant.parse("2026-06-14T08:30:00Z"), ZoneId.of("Europe/Berlin"))

    private val group = ChurchGroup(division = "청년부", name = "1순").also { setId(it, 7L) }
    private val member = Member(lastName = "홍", firstName = "길동", group = group).also { setId(it, 1L) }
    private val definition =
        AttendanceDefinition(
            title = "주일예배",
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
        ).also { setId(it, 3L) }

    @BeforeEach
    fun setUp() {
        service = AttendanceRosterService(definitionRepo, logRepo, memberRepo, clock)
    }

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private fun logOf(
        member: Member?,
        group: ChurchGroup?,
        presence: CheckInPresence = CheckInPresence.IN_PLACE,
    ) = AttendanceLog(
        definition = definition,
        member = member,
        attendanceDate = sunday,
        groupAtCheckIn = group,
        presence = presence,
    ).also { it.createdAt = Instant.parse("2026-06-14T08:05:00Z") }

    private fun request(date: LocalDate = sunday) = CreateAttendanceLogRequest(member.publicId, definition.publicId, date)

    // ─── getRoster ────────────────────────────────────────────────────────────

    @Test
    fun `getRoster maps name, 순 at check-in and presence per row`() {
        `when`(logRepo.findRoster(sunday, null)).thenReturn(listOf(logOf(member, group)))

        val row = service.getRoster(sunday, null).single()

        assertEquals("홍길동", row.fullName)
        assertEquals(member.publicId.toString(), row.memberPublicId)
        assertEquals("1순", row.groupName)
        assertEquals("주일예배", row.definitionTitle)
        assertEquals(Instant.parse("2026-06-14T08:05:00Z"), row.checkedInAt)
        assertEquals(CheckInPresence.IN_PLACE, row.presence)
    }

    @Test
    fun `getRoster keeps a purged member's row, only without a name`() {
        `when`(logRepo.findRoster(sunday, null)).thenReturn(listOf(logOf(null, group)))

        val row = service.getRoster(sunday, null).single()

        assertNull(row.memberPublicId)
        assertNull(row.fullName)
        assertEquals("1순", row.groupName)
    }

    @Test
    fun `getRoster resolves the definition filter to its internal id`() {
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(definition.publicId)).thenReturn(Optional.of(definition))
        `when`(logRepo.findRoster(sunday, 3L)).thenReturn(emptyList())

        service.getRoster(sunday, definition.publicId)

        verify(logRepo).findRoster(sunday, 3L)
    }

    @Test
    fun `getRoster with an unknown definition is a 404`() {
        val unknown = UUID.randomUUID()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(unknown)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getRoster(sunday, unknown) }
    }

    // ─── addLog ───────────────────────────────────────────────────────────────

    private fun givenMemberAndDefinition() {
        `when`(memberRepo.findByPublicIdAndDeletedAtIsNull(member.publicId)).thenReturn(Optional.of(member))
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(definition.publicId)).thenReturn(Optional.of(definition))
    }

    @Test
    fun `addLog records the current 순 and UNCONFIRMED`() {
        givenMemberAndDefinition()
        `when`(logRepo.insertIfAbsent(any(), eq(3L), eq(1L), eq(7L), eq(sunday), eq("UNCONFIRMED"))).thenReturn(1)
        `when`(logRepo.findByPublicId(any()))
            .thenReturn(Optional.of(logOf(member, group, CheckInPresence.UNCONFIRMED)))

        val row = service.addLog(request())

        val publicId = argumentCaptor<UUID>()
        verify(logRepo).insertIfAbsent(publicId.capture(), eq(3L), eq(1L), eq(7L), eq(sunday), eq("UNCONFIRMED"))
        verify(logRepo).findByPublicId(publicId.firstValue)
        assertEquals(CheckInPresence.UNCONFIRMED, row.presence)
        assertEquals("홍길동", row.fullName)
    }

    @Test
    fun `addLog for a member who already has a log is a 409`() {
        givenMemberAndDefinition()
        `when`(logRepo.insertIfAbsent(any(), any(), any(), anyOrNull(), any(), any())).thenReturn(0)

        val ex = assertThrows<ResponseStatusException> { service.addLog(request()) }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `addLog for a future date is a 400 and writes nothing`() {
        givenMemberAndDefinition()

        val ex = assertThrows<ResponseStatusException> { service.addLog(request(sunday.plusWeeks(1))) }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(logRepo, never()).insertIfAbsent(any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `addLog on a weekday the definition does not run is a 400`() {
        givenMemberAndDefinition()

        val ex = assertThrows<ResponseStatusException> { service.addLog(request(sunday.minusDays(1))) }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(logRepo, never()).insertIfAbsent(any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `addLog for an unknown member is a 404`() {
        `when`(memberRepo.findByPublicIdAndDeletedAtIsNull(member.publicId)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.addLog(request()) }
    }
}
