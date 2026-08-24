package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.GraduationReason
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import com.hanmaum.dn.app.features.members.repository.MemberGraduationRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberGraduationServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var graduationRepository: MemberGraduationRepository

    @InjectMocks
    private lateinit var service: MemberGraduationService

    private val publicId: UUID = UUID.randomUUID()
    private val graduatedOn = LocalDate.of(2026, 5, 1)
    private val actor = "admin-sub-1"

    private fun member(status: MemberStatus = MemberStatus.ACTIVE): Member {
        val m = Member(lastName = "김", firstName = "철수", memberStatus = status)
        m.id = 1L
        return m
    }

    private fun stubMember(m: Member) {
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(publicId)).thenReturn(Optional.of(m))
    }

    @Test
    fun `graduating records the event and deactivates the member`() {
        val m = member()
        stubMember(m)
        `when`(graduationRepository.findOpenByMemberId(1L)).thenReturn(null)
        `when`(graduationRepository.save(any<MemberGraduation>())).thenAnswer { it.arguments[0] }

        val result = service.graduate(publicId, graduatedOn, GraduationReason.MARRIAGE, null, actor)

        assertEquals(MemberStatus.INACTIVE, m.memberStatus)
        assertEquals(GraduationReason.MARRIAGE, result.reason)
        assertEquals(actor, result.graduatedBy)
        assertEquals(graduatedOn, result.graduatedOn)
    }

    @Test
    fun `graduating snapshots the status that was actually there`() {
        val m = member(MemberStatus.PENDING)
        stubMember(m)
        `when`(graduationRepository.findOpenByMemberId(1L)).thenReturn(null)
        `when`(graduationRepository.save(any<MemberGraduation>())).thenAnswer { it.arguments[0] }

        val result = service.graduate(publicId, graduatedOn, GraduationReason.MARRIAGE, null, actor)

        assertEquals(MemberStatus.PENDING.name, result.previousMemberStatus)
    }

    @Test
    fun `graduating a member who already has an open graduation is rejected`() {
        val m = member()
        stubMember(m)
        `when`(graduationRepository.findOpenByMemberId(1L)).thenReturn(
            MemberGraduation(m, graduatedOn, GraduationReason.MARRIAGE, actor, MemberStatus.ACTIVE),
        )

        val error =
            assertThrows<ResponseStatusException> {
                service.graduate(publicId, graduatedOn, GraduationReason.MARRIAGE, null, actor)
            }

        assertEquals(409, error.statusCode.value())
        assertEquals(MemberStatus.ACTIVE, m.memberStatus)
    }

    @Test
    fun `graduating a deleted member is rejected`() {
        val m = member(MemberStatus.DELETED)
        stubMember(m)

        val error =
            assertThrows<ResponseStatusException> {
                service.graduate(publicId, graduatedOn, GraduationReason.MARRIAGE, null, actor)
            }

        assertEquals(422, error.statusCode.value())
    }

    @Test
    fun `graduating an unknown member is rejected`() {
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(publicId)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.graduate(publicId, graduatedOn, GraduationReason.MARRIAGE, null, actor)
        }
    }

    // The important one: reinstating must restore the snapshot, not a hardcoded ACTIVE.
    @Test
    fun `reinstating restores the status the member had before graduating`() {
        val m = member(MemberStatus.INACTIVE)
        stubMember(m)
        val open = MemberGraduation(m, graduatedOn, GraduationReason.MARRIAGE, actor, MemberStatus.PENDING)
        `when`(graduationRepository.findOpenByMemberId(1L)).thenReturn(open)
        `when`(graduationRepository.save(any<MemberGraduation>())).thenAnswer { it.arguments[0] }

        val result = service.reinstate(publicId, "admin-sub-2")

        assertEquals(MemberStatus.PENDING, m.memberStatus)
        assertNotNull(result.revertedAt)
        assertEquals("admin-sub-2", result.revertedBy)
    }

    @Test
    fun `reinstating a member with no open graduation is rejected`() {
        val m = member()
        stubMember(m)
        `when`(graduationRepository.findOpenByMemberId(1L)).thenReturn(null)

        val error = assertThrows<ResponseStatusException> { service.reinstate(publicId, actor) }

        assertEquals(404, error.statusCode.value())
    }
}
