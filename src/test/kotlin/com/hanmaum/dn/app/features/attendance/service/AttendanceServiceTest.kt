package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.attendance.repository.ChurchGroupAttendanceCountView
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AttendanceServiceTest {
    @Mock private lateinit var definitionRepo: AttendanceDefinitionRepository

    @Mock private lateinit var logRepo: AttendanceLogRepository

    @Mock private lateinit var memberRepo: MemberRepository

    private lateinit var service: AttendanceService

    private val berlinZone = ZoneId.of("Europe/Berlin")
    private val attendanceDate = LocalDate.of(2026, 6, 14)
    private val clock = Clock.fixed(Instant.parse("2026-06-14T08:30:00Z"), berlinZone)

    @BeforeEach
    fun setUp() {
        service = AttendanceService(definitionRepo, logRepo, memberRepo, clock)
    }

    private fun makeDefinition(
        id: Long = 1L,
        dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
        windowStart: LocalTime = LocalTime.of(10, 0),
        windowEnd: LocalTime = LocalTime.of(12, 0),
        isActive: Boolean = true,
    ): AttendanceDefinition {
        val definition =
            AttendanceDefinition(
                title = "주일예배",
                dayOfWeek = dayOfWeek,
                windowStart = windowStart,
                windowEnd = windowEnd,
                isActive = isActive,
            )
        setId(definition, id)
        return definition
    }

    private fun makeGroup(
        id: Long = 7L,
        name: String = "다니엘조",
    ): ChurchGroup {
        val group = ChurchGroup(division = "청년부", name = name)
        setId(group, id)
        return group
    }

    private fun makeMember(
        id: Long = 1L,
        keycloakId: String = "kc-001",
        group: ChurchGroup? = null,
    ): Member {
        val member = Member(lastName = "김", firstName = "철수", group = group)
        setId(member, id)
        setField(member, Member::class.java, "keycloakId", keycloakId)
        return member
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
        entity: Any,
        clazz: Class<*>,
        name: String,
        value: Any?,
    ) {
        val field: Field = clazz.getDeclaredField(name)
        field.isAccessible = true
        field.set(entity, value)
    }

    @Test
    fun `createDefinition returns created definition`() {
        val request =
            CreateDefinitionRequest(
                title = "주일예배",
                dayOfWeek = DayOfWeek.SUNDAY,
                windowStart = LocalTime.of(10, 0),
                windowEnd = LocalTime.of(12, 0),
            )
        `when`(definitionRepo.save(any())).thenReturn(makeDefinition())

        val result = service.createDefinition(request)

        assertEquals("주일예배", result.title)
        verify(definitionRepo).save(any())
    }

    @Test
    fun `createDefinition rejects invalid window`() {
        val request =
            CreateDefinitionRequest(
                title = "잘못된 시간",
                dayOfWeek = DayOfWeek.SUNDAY,
                windowStart = LocalTime.of(12, 0),
                windowEnd = LocalTime.of(10, 0),
            )

        val exception = assertThrows<ResponseStatusException> { service.createDefinition(request) }

        assertEquals(400, exception.statusCode.value())
        verify(definitionRepo, never()).save(any())
    }

    @Test
    fun `updateDefinition patches non-null fields`() {
        val definition = makeDefinition()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(definition.publicId))
            .thenReturn(Optional.of(definition))

        val result = service.updateDefinition(definition.publicId, UpdateDefinitionRequest(title = "새 예배"))

        assertEquals("새 예배", result.title)
        assertEquals(DayOfWeek.SUNDAY, result.dayOfWeek)
    }

    @Test
    fun `deactivateDefinition keeps history addressable and marks definition inactive`() {
        val definition = makeDefinition()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(definition.publicId))
            .thenReturn(Optional.of(definition))

        service.deactivateDefinition(definition.publicId)

        assertEquals(false, definition.isActive)
        assertEquals(null, definition.deletedAt)
    }

    @Test
    fun `checkIn stores only the group snapshot needed for aggregation`() {
        val group = makeGroup()
        val member = makeMember(group = group)
        val definition = makeDefinition()
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(DayOfWeek.SUNDAY))
            .thenReturn(listOf(definition))
        `when`(
            logRepo.insertIfAbsent(
                publicId = any(),
                definitionId = eq(1L),
                memberId = eq(1L),
                groupId = eq(7L),
                attendanceDate = eq(attendanceDate),
            ),
        ).thenReturn(1)

        val result = service.checkIn("kc-001")

        assertEquals(attendanceDate, result.attendanceDate)
        assertEquals(definition.publicId.toString(), result.definitionPublicId)
        val publicIdCaptor = argumentCaptor<UUID>()
        verify(logRepo).insertIfAbsent(
            publicIdCaptor.capture(),
            eq(1L),
            eq(1L),
            eq(7L),
            eq(attendanceDate),
        )
        assertNotNull(publicIdCaptor.firstValue)
    }

    @Test
    fun `checkIn supports members without a church group`() {
        val member = makeMember()
        val definition = makeDefinition()
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(DayOfWeek.SUNDAY))
            .thenReturn(listOf(definition))
        `when`(logRepo.insertIfAbsent(any(), eq(1L), eq(1L), eq(null), eq(attendanceDate))).thenReturn(1)

        service.checkIn("kc-001")

        verify(logRepo).insertIfAbsent(any(), eq(1L), eq(1L), eq(null), eq(attendanceDate))
    }

    @Test
    fun `checkIn returns conflict when atomic insert detects duplicate`() {
        val member = makeMember()
        val definition = makeDefinition()
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(DayOfWeek.SUNDAY))
            .thenReturn(listOf(definition))
        `when`(logRepo.insertIfAbsent(any(), eq(1L), eq(1L), eq(null), eq(attendanceDate))).thenReturn(0)

        val exception = assertThrows<ResponseStatusException> { service.checkIn("kc-001") }

        assertEquals(409, exception.statusCode.value())
    }

    @Test
    fun `checkIn rejects request outside active definition window`() {
        val member = makeMember()
        val definition = makeDefinition(windowStart = LocalTime.of(12, 0), windowEnd = LocalTime.of(13, 0))
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(definitionRepo.findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(DayOfWeek.SUNDAY))
            .thenReturn(listOf(definition))

        val exception = assertThrows<ResponseStatusException> { service.checkIn("kc-001") }

        assertEquals(400, exception.statusCode.value())
        verify(logRepo, never()).insertIfAbsent(any(), any(), any(), any(), any())
    }

    @Test
    fun `checkIn rejects unknown member`() {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("unknown")).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.checkIn("unknown") }

        verify(logRepo, never()).insertIfAbsent(any(), any(), any(), any(), any())
    }

    @Test
    fun `getGroupCounts returns groups with zero and sums total`() {
        val definition = makeDefinition()
        `when`(definitionRepo.findByPublicIdAndDeletedAtIsNull(definition.publicId))
            .thenReturn(Optional.of(definition))
        `when`(logRepo.countByChurchGroup(1L, attendanceDate))
            .thenReturn(
                listOf(
                    countView(UUID.randomUUID(), "청년부", "다니엘조", 4),
                    countView(UUID.randomUUID(), "청년부", "요셉조", 0),
                    countView(null, null, null, 1),
                ),
            )

        val result = service.getGroupCounts(definition.publicId, attendanceDate)

        assertEquals(5, result.totalCount)
        assertEquals(3, result.groups.size)
        assertEquals(0, result.groups[1].attendanceCount)
        assertEquals(null, result.groups[2].groupPublicId)
    }

    private fun countView(
        publicId: UUID?,
        division: String?,
        name: String?,
        count: Long,
    ): ChurchGroupAttendanceCountView =
        object : ChurchGroupAttendanceCountView {
            override val groupPublicId = publicId
            override val groupDivision = division
            override val groupName = name
            override val attendanceCount = count
        }
}
