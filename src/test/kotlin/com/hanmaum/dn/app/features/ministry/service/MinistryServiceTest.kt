package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.AddMinistryMemberRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryContactRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryContact
import com.hanmaum.dn.app.features.ministry.domain.MinistrySchedule
import com.hanmaum.dn.app.features.ministry.repository.ActiveMemberView
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MinistryServiceTest {
    @Mock private lateinit var ministryRepository: MinistryRepository

    @Mock private lateinit var ministryAssignmentRepository: MinistryAssignmentRepository

    @Mock private lateinit var memberRepository: MemberRepository

    private lateinit var service: MinistryService

    // Fixed clock: 2026-06-22 (Berlin) → assignments start on the first of the month.
    private val clock = Clock.fixed(Instant.parse("2026-06-22T08:00:00Z"), ZoneId.of("Europe/Berlin"))

    @BeforeEach
    fun setUp() {
        service = MinistryService(ministryRepository, ministryAssignmentRepository, memberRepository, clock)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeMinistry(
        id: Long = 1L,
        name: String = "찬양팀",
        shortDescription: String = "예배 찬양을 담당합니다.",
        isActive: Boolean = true,
    ): Ministry {
        val m =
            Ministry(
                name = name,
                shortDescription = shortDescription,
                longDescription = "찬양으로 예배를 섬기는 사역입니다.",
                isMinistryActive = isActive,
            ).also {
                it.replaceContacts(listOf(MinistryContact(role = "팀장", name = "김민준 집사님")))
            }
        setId(m, id)
        return m
    }

    private fun makeMember(
        id: Long = 100L,
        lastName: String = "김",
        firstName: String = "철수",
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        setId(m, id)
        return m
    }

    /** Reflectively set BaseEntity.id (private var). BaseEntity is the direct superclass. */
    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val field: Field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    // ─── createMinistry ───────────────────────────────────────────────────────

    @Test
    fun `createMinistry - success when name is unique`() {
        val req =
            CreateMinistryRequest(
                title = "난민 사역",
                subtitle = "하나님의 사랑을 나누고 복음을 전합니다.",
                about = "한 달에 한 번 난민 아이들을 섬깁니다.",
                requirements = listOf("아이들을 섬기려는 마음이 있으신 분"),
                schedules =
                    listOf(
                        MinistryScheduleRequest(
                            description = "매달 넷째 주 토요일: 새벽기도 후 준비모임",
                            startTime = LocalTime.of(7, 0),
                            endTime = LocalTime.of(9, 0),
                        ),
                    ),
                contacts =
                    listOf(
                        MinistryContactRequest(role = "팀장", name = "김영원 권사님"),
                        MinistryContactRequest(role = "간사", name = "최혜령 자매님"),
                    ),
            )
        val saved =
            Ministry(
                name = req.title,
                shortDescription = req.subtitle,
                longDescription = req.about,
            ).also {
                it.replaceRequirements(req.requirements)
                it.replaceSchedules(
                    listOf(
                        MinistrySchedule(
                            description = req.schedules.single().description,
                            startTime = req.schedules.single().startTime,
                            endTime = req.schedules.single().endTime,
                        ),
                    ),
                )
                it.replaceContacts(
                    req.contacts.map { contact ->
                        MinistryContact(role = contact.role, name = contact.name)
                    },
                )
            }
        `when`(ministryRepository.existsByNameAndDeletedAtIsNull("난민 사역")).thenReturn(false)
        `when`(ministryRepository.save(any())).thenReturn(saved)

        val result = service.createMinistry(req)

        assertEquals("난민 사역", result.title)
        assertEquals(req.requirements, result.requirements)
        assertEquals(
            "07:00",
            result.schedules
                .single()
                .startTime
                .toString(),
        )
        assertEquals("팀장", result.contacts[0].role)
        assertEquals("김영원 권사님", result.contacts[0].name)
        assertEquals("간사", result.contacts[1].role)
        assertEquals("최혜령 자매님", result.contacts[1].name)
        verify(ministryRepository).save(any())
    }

    @Test
    fun `createMinistry - 409 when name already taken`() {
        val req =
            CreateMinistryRequest(
                title = "찬양팀",
                subtitle = "예배 찬양 담당",
                about = "찬양으로 예배를 섬깁니다.",
            )
        `when`(ministryRepository.existsByNameAndDeletedAtIsNull("찬양팀")).thenReturn(true)

        assertThrows<ResponseStatusException> { service.createMinistry(req) }
        verify(ministryRepository, never()).save(any())
    }

    // ─── getMinistries ────────────────────────────────────────────────────────

    @Test
    fun `getMinistries - returns summary list filtered by active`() {
        val active = makeMinistry(isActive = true)
        `when`(ministryRepository.findAllActive(true)).thenReturn(listOf(active))

        val result = service.getMinistries(active = true)

        assertEquals(1, result.size)
        assertEquals("찬양팀", result[0].title)
    }

    @Test
    fun `getMinistries - null active returns all`() {
        `when`(ministryRepository.findAllActive(null)).thenReturn(emptyList())

        val result = service.getMinistries(null)

        assertEquals(0, result.size)
    }

    // ─── getMinistry ──────────────────────────────────────────────────────────

    @Test
    fun `getMinistry - returns dto when found`() {
        val ministry = makeMinistry()
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))

        val result = service.getMinistry(publicId)

        assertEquals(publicId.toString(), result.publicId)
        assertEquals("찬양팀", result.title)
        assertEquals("팀장", result.contacts.single().role)
        assertEquals("김민준 집사님", result.contacts.single().name)
    }

    @Test
    fun `getMinistry - throws EntityNotFoundException when not found`() {
        val id = UUID.randomUUID()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getMinistry(id) }
    }

    // ─── updateMinistry ───────────────────────────────────────────────────────

    @Test
    fun `updateMinistry - patches only non-null fields`() {
        val ministry = makeMinistry()
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))

        val req = UpdateMinistryRequest(title = "새 이름")
        val result = service.updateMinistry(publicId, req)

        assertEquals("새 이름", result.title)
        assertEquals("예배 찬양을 담당합니다.", result.subtitle)
    }

    @Test
    fun `updateMinistry - replaces structured detail lists and contacts`() {
        val ministry =
            makeMinistry().also {
                it.replaceRequirements(listOf("기존 자격"))
                it.replaceSchedules(
                    listOf(
                        MinistrySchedule(
                            description = "기존 일정",
                            startTime = LocalTime.of(10, 0),
                            endTime = LocalTime.of(11, 0),
                        ),
                    ),
                )
                it.replaceContacts(
                    listOf(
                        MinistryContact(role = "팀장", name = "기존 팀장님"),
                        MinistryContact(role = "간사", name = "기존 간사님"),
                    ),
                )
            }
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))

        val result =
            service.updateMinistry(
                publicId,
                UpdateMinistryRequest(
                    requirements = listOf("새 자격 1", "새 자격 2"),
                    schedules =
                        listOf(
                            MinistryScheduleRequest(
                                description = "새 일정",
                                startTime = LocalTime.of(16, 0),
                                endTime = LocalTime.of(18, 0),
                            ),
                        ),
                    contacts =
                        listOf(
                            MinistryContactRequest(role = "담당 교역자", name = "새 담당자님"),
                        ),
                ),
            )

        assertEquals(listOf("새 자격 1", "새 자격 2"), result.requirements)
        assertEquals("새 일정", result.schedules.single().description)
        assertEquals("담당 교역자", result.contacts.single().role)
        assertEquals("새 담당자님", result.contacts.single().name)
    }

    @Test
    fun `createMinistry - rejects schedule whose end is not after start`() {
        val request =
            CreateMinistryRequest(
                title = "난민 사역",
                subtitle = "아이들을 섬깁니다.",
                about = "난민 아이들과 함께합니다.",
                schedules =
                    listOf(
                        MinistryScheduleRequest(
                            description = "준비모임",
                            startTime = LocalTime.of(9, 0),
                            endTime = LocalTime.of(9, 0),
                        ),
                    ),
            )
        `when`(ministryRepository.existsByNameAndDeletedAtIsNull(request.title)).thenReturn(false)

        val exception = assertThrows<ResponseStatusException> { service.createMinistry(request) }

        assertEquals(400, exception.statusCode.value())
        verify(ministryRepository, never()).save(any())
    }

    @Test
    fun `updateMinistry - can deactivate via isActive flag`() {
        val ministry = makeMinistry(isActive = true)
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))

        service.updateMinistry(publicId, UpdateMinistryRequest(isActive = false))

        assertFalse(ministry.isMinistryActive)
    }

    // ─── deactivateMinistry ───────────────────────────────────────────────────

    @Test
    fun `deactivateMinistry - sets isMinistryActive false`() {
        val ministry = makeMinistry(isActive = true)
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))

        service.deactivateMinistry(publicId)

        assertFalse(ministry.isMinistryActive)
    }

    @Test
    fun `deactivateMinistry - 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.deactivateMinistry(id) }
    }

    // ─── getActiveMembers ─────────────────────────────────────────────────────

    @Test
    fun `getActiveMembers - returns active member dtos for ministry`() {
        val ministry = makeMinistry()
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))
        val memberPublicId = UUID.randomUUID()
        val view =
            ActiveMemberView(
                memberPublicId = memberPublicId,
                fullName = "김철수",
                startDate = LocalDate.of(2025, 1, 1),
                note = null,
                gender = null,
            )
        `when`(ministryAssignmentRepository.findActiveByMinistryPublicId(publicId))
            .thenReturn(listOf(view))

        val result = service.getActiveMembers(publicId)

        assertEquals(1, result.size)
        assertEquals(memberPublicId.toString(), result[0].publicId)
        assertEquals("김철수", result[0].fullName)
        assertEquals("2025-01-01", result[0].startDate)
        assertNull(result[0].note)
    }

    @Test
    fun `getActiveMembers - returns empty list when ministry has no active members`() {
        val ministry = makeMinistry()
        val publicId = ministry.publicId
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(publicId))
            .thenReturn(Optional.of(ministry))
        `when`(ministryAssignmentRepository.findActiveByMinistryPublicId(publicId))
            .thenReturn(emptyList())

        val result = service.getActiveMembers(publicId)

        assertEquals(0, result.size)
    }

    @Test
    fun `getActiveMembers - throws EntityNotFoundException when ministry not found`() {
        val id = UUID.randomUUID()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getActiveMembers(id) }
    }

    // ─── addMember ────────────────────────────────────────────────────────────

    @Test
    fun `addMember - binds existing member and returns active-member dto`() {
        val ministry = makeMinistry()
        val member = makeMember()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(ministryAssignmentRepository.existsActiveAssignment(ministry.id!!, member.id!!))
            .thenReturn(false)
        `when`(ministryAssignmentRepository.save(any()))
            .thenAnswer { it.getArgument<MinistryAssignment>(0) }

        val result = service.addMember(ministry.publicId, AddMinistryMemberRequest(memberId = member.publicId, note = "신입"))

        assertEquals(member.publicId.toString(), result.publicId)
        assertEquals("김철수", result.fullName)
        assertEquals("2026-06-01", result.startDate) // first of current month per fixed clock
        assertEquals("신입", result.note)
        verify(ministryAssignmentRepository).save(any())
    }

    @Test
    fun `addMember - 409 when member already active in this ministry`() {
        val ministry = makeMinistry()
        val member = makeMember()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(ministryAssignmentRepository.existsActiveAssignment(ministry.id!!, member.id!!))
            .thenReturn(true)

        val ex =
            assertThrows<ResponseStatusException> {
                service.addMember(ministry.publicId, AddMinistryMemberRequest(memberId = member.publicId))
            }

        assertEquals(409, ex.statusCode.value())
        verify(ministryAssignmentRepository, never()).save(any())
    }

    @Test
    fun `addMember - 404 when ministry not found`() {
        val ministryId = UUID.randomUUID()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministryId))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.addMember(ministryId, AddMinistryMemberRequest(memberId = UUID.randomUUID()))
        }
        verify(ministryAssignmentRepository, never()).save(any())
    }

    @Test
    fun `addMember - 404 when member not found`() {
        val ministry = makeMinistry()
        val unknownMemberId = UUID.randomUUID()
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(unknownMemberId))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.addMember(ministry.publicId, AddMinistryMemberRequest(memberId = unknownMemberId))
        }
        verify(ministryAssignmentRepository, never()).save(any())
    }
}
