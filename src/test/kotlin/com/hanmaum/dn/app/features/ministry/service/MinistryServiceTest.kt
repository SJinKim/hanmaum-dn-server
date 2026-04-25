package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateRegistrationRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateRegistrationStatusRequest
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import com.hanmaum.dn.app.features.ministry.domain.RegistrationStatus
import com.hanmaum.dn.app.features.ministry.repository.MinistryRegistrationRepository
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MinistryServiceTest {
    @Mock private lateinit var ministryRepository: MinistryRepository

    @Mock private lateinit var registrationRepository: MinistryRegistrationRepository

    @Mock private lateinit var memberRepository: MemberRepository

    private lateinit var service: MinistryService

    @BeforeEach
    fun setUp() {
        service = MinistryService(ministryRepository, registrationRepository, memberRepository)
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

    private fun makeRegistration(
        id: Long = 10L,
        ministry: Ministry = makeMinistry(),
        member: Member = makeMember(),
        period: String = "2026",
        status: RegistrationStatus = RegistrationStatus.PENDING,
    ): MinistryRegistration {
        val r = MinistryRegistration(ministry = ministry, member = member, registrationPeriod = period, status = status)
        setId(r, id)
        return r
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

    // ─── registerSelf ─────────────────────────────────────────────────────────

    @Test
    fun `registerSelf - success when no duplicate`() {
        val ministry = makeMinistry()
        val member = makeMember()
        val ministryPublicId = ministry.publicId
        val req = CreateRegistrationRequest(period = "2026", note = null)
        val saved = MinistryRegistration(ministry = ministry, member = member, registrationPeriod = "2026")
        setId(saved, 10L)

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministryPublicId))
            .thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001"))
            .thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.empty())
        `when`(registrationRepository.save(any())).thenReturn(saved)

        val result = service.registerSelf(ministryPublicId, "kc-sub-001", req)

        assertEquals("2026", result.registrationPeriod)
        verify(registrationRepository).save(any())
    }

    @Test
    fun `registerSelf - 409 on duplicate registration`() {
        val ministry = makeMinistry()
        val member = makeMember()
        val ministryPublicId = ministry.publicId
        val req = CreateRegistrationRequest(period = "2026")
        val approved = makeRegistration(ministry = ministry, member = member, status = RegistrationStatus.APPROVED)

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministryPublicId))
            .thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001"))
            .thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.of(approved))

        assertThrows<ResponseStatusException> {
            service.registerSelf(ministryPublicId, "kc-sub-001", req)
        }
        verify(registrationRepository, never()).save(any())
    }

    @Test
    fun `registerSelf - 400 when ministry is inactive`() {
        val ministry = makeMinistry(isActive = false)
        val ministryPublicId = ministry.publicId
        val req = CreateRegistrationRequest(period = "2026")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministryPublicId))
            .thenReturn(Optional.of(ministry))
        // memberRepository is NOT called — service short-circuits on inactive check

        val ex =
            assertThrows<ResponseStatusException> {
                service.registerSelf(ministryPublicId, "kc-sub-001", req)
            }
        assertEquals(400, ex.statusCode.value())
    }

    // ─── withdrawRegistration ─────────────────────────────────────────────────

    @Test
    fun `withdrawRegistration - 403 when registration belongs to another member`() {
        val ministry = makeMinistry()
        val owner = makeMember(id = 1L, keycloakId = "kc-owner")
        val caller = makeMember(id = 2L, keycloakId = "kc-caller")

        val registration = MinistryRegistration(ministry = ministry, member = owner, registrationPeriod = "2026")
        setId(registration, 5L)

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(registration.publicId))
            .thenReturn(Optional.of(registration))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-caller"))
            .thenReturn(caller)

        val ex =
            assertThrows<ResponseStatusException> {
                service.withdrawRegistration(ministry.publicId, registration.publicId, "kc-caller")
            }
        assertEquals(403, ex.statusCode.value())
    }

    @Test
    fun `withdrawRegistration - soft deletes own registration`() {
        val ministry = makeMinistry()
        val member = makeMember(keycloakId = "kc-sub-001")
        val registration = MinistryRegistration(ministry = ministry, member = member, registrationPeriod = "2026")
        setId(registration, 5L)

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(registration.publicId))
            .thenReturn(Optional.of(registration))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001"))
            .thenReturn(member)

        service.withdrawRegistration(ministry.publicId, registration.publicId, "kc-sub-001")

        org.junit.jupiter.api.Assertions
            .assertNotNull(registration.deletedAt)
    }

    // ─── registerSelf re-apply ────────────────────────────────────────────────

    @Test
    fun `registerSelf - re-applies when existing registration is REJECTED`() {
        val ministry = makeMinistry()
        val member = makeMember()
        val rejected = makeRegistration(ministry = ministry, member = member, status = RegistrationStatus.REJECTED)
        val req = CreateRegistrationRequest(period = "2026")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001")).thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.of(rejected))
        val newReg = makeRegistration(id = 11L, ministry = ministry, member = member, status = RegistrationStatus.PENDING)
        `when`(registrationRepository.save(any())).thenReturn(newReg)

        val result = service.registerSelf(ministry.publicId, "kc-sub-001", req)

        assertEquals("PENDING", result.status)
        verify(registrationRepository).flush()
        verify(registrationRepository).save(any())
    }

    @Test
    fun `registerSelf - 409 when existing registration is PENDING`() {
        val ministry = makeMinistry()
        val member = makeMember()
        val pending = makeRegistration(ministry = ministry, member = member, status = RegistrationStatus.PENDING)
        val req = CreateRegistrationRequest(period = "2026")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001")).thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.of(pending))

        assertThrows<ResponseStatusException> { service.registerSelf(ministry.publicId, "kc-sub-001", req) }
        verify(registrationRepository, never()).save(any())
    }

    // ─── approveOrRejectRegistration ─────────────────────────────────────────

    @Test
    fun `approveOrRejectRegistration - approves pending registration`() {
        val ministry = makeMinistry()
        val reg = makeRegistration(ministry = ministry)
        val req = UpdateRegistrationStatusRequest(status = "APPROVED")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(reg.publicId)).thenReturn(Optional.of(reg))

        val result = service.approveOrRejectRegistration(ministry.publicId, reg.publicId, req)

        assertEquals("APPROVED", result.status)
    }

    @Test
    fun `approveOrRejectRegistration - rejects pending registration`() {
        val ministry = makeMinistry()
        val reg = makeRegistration(ministry = ministry)
        val req = UpdateRegistrationStatusRequest(status = "REJECTED")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(reg.publicId)).thenReturn(Optional.of(reg))

        val result = service.approveOrRejectRegistration(ministry.publicId, reg.publicId, req)

        assertEquals("REJECTED", result.status)
    }

    @Test
    fun `approveOrRejectRegistration - 409 when registration is not PENDING`() {
        val ministry = makeMinistry()
        val reg = makeRegistration(ministry = ministry, status = RegistrationStatus.APPROVED)
        val req = UpdateRegistrationStatusRequest(status = "REJECTED")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(reg.publicId)).thenReturn(Optional.of(reg))

        assertThrows<ResponseStatusException> {
            service.approveOrRejectRegistration(ministry.publicId, reg.publicId, req)
        }
    }

    @Test
    fun `approveOrRejectRegistration - 400 on invalid status value`() {
        val ministry = makeMinistry()
        val reg = makeRegistration(ministry = ministry)
        val req = UpdateRegistrationStatusRequest(status = "GARBAGE")

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(registrationRepository.findByPublicIdAndDeletedAtIsNull(reg.publicId)).thenReturn(Optional.of(reg))

        assertThrows<ResponseStatusException> {
            service.approveOrRejectRegistration(ministry.publicId, reg.publicId, req)
        }
    }

    // ─── getMyRegistration ────────────────────────────────────────────────────

    @Test
    fun `getMyRegistration - returns dto when found`() {
        val ministry = makeMinistry()
        val member = makeMember()
        val reg = makeRegistration(ministry = ministry, member = member, status = RegistrationStatus.PENDING)

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001")).thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.of(reg))

        val result = service.getMyRegistration(ministry.publicId, "kc-sub-001", "2026")

        assertEquals("PENDING", result?.status)
    }

    @Test
    fun `getMyRegistration - returns null when no record`() {
        val ministry = makeMinistry()
        val member = makeMember()

        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-sub-001")).thenReturn(member)
        `when`(registrationRepository.findByMinistryIdAndMemberIdAndPeriod(1L, 1L, "2026"))
            .thenReturn(Optional.empty())

        val result = service.getMyRegistration(ministry.publicId, "kc-sub-001", "2026")

        assertNull(result)
    }
}
