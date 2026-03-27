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
        assertEquals(DayOfWeek.SUNDAY, result.dayOfWeek)
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
