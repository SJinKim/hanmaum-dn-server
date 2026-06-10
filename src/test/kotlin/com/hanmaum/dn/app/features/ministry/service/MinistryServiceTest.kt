package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.domain.Ministry
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
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MinistryServiceTest {
    @Mock private lateinit var ministryRepository: MinistryRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var ministryAssignmentRepository: MinistryAssignmentRepository

    private lateinit var service: MinistryService

    @BeforeEach
    fun setUp() {
        service = MinistryService(ministryRepository, memberRepository, ministryAssignmentRepository)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeMinistry(
        id: Long = 1L,
        name: String = "찬양팀",
        shortDescription: String = "예배 찬양을 담당합니다.",
        isActive: Boolean = true,
    ): Ministry {
        val m = Ministry(name = name, shortDescription = shortDescription, isMinistryActive = isActive)
        setId(m, id)
        return m
    }

    private fun makeMember(
        id: Long = 1L,
        keycloakId: String = "kc-sub-001",
        lastName: String = "김",
        firstName: String = "철수",
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        setId(m, id)
        setKeycloakId(m, keycloakId)
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

    private fun setKeycloakId(
        member: Member,
        keycloakId: String,
    ) {
        val field: Field = member.javaClass.getDeclaredField("keycloakId")
        field.isAccessible = true
        field.set(member, keycloakId)
    }

    // ─── createMinistry ───────────────────────────────────────────────────────

    @Test
    fun `createMinistry - success when name is unique`() {
        val req = CreateMinistryRequest(name = "찬양팀", shortDescription = "예배 찬양 담당")
        val saved = makeMinistry()
        `when`(ministryRepository.existsByNameAndDeletedAtIsNull("찬양팀")).thenReturn(false)
        `when`(ministryRepository.save(any())).thenReturn(saved)

        val result = service.createMinistry(req)

        assertEquals("찬양팀", result.name)
        verify(ministryRepository).save(any())
    }

    @Test
    fun `createMinistry - 409 when name already taken`() {
        val req = CreateMinistryRequest(name = "찬양팀", shortDescription = "...")
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
        assertEquals("찬양팀", result[0].name)
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
        assertEquals("찬양팀", result.name)
        assertNull(result.leader)
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

        val req = UpdateMinistryRequest(name = "새 이름")
        val result = service.updateMinistry(publicId, req)

        assertEquals("새 이름", result.name)
        // shortDescription unchanged
        assertEquals("예배 찬양을 담당합니다.", result.shortDescription)
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
}
