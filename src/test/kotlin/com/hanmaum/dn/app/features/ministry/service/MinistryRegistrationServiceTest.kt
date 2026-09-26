package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDecision
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MinistryRegistrationServiceTest {
    @Mock private lateinit var ministries: MinistryRepository

    @Mock private lateinit var assignments: MinistryAssignmentRepository

    @Mock private lateinit var resolver: CurrentMemberResolver

    @Mock private lateinit var email: MinistryLeaderEmailSender

    @Mock private lateinit var notifications: AppNotificationRepository

    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ministry = Ministry("찬양팀", "찬양", "찬양 사역").also { it.id = 1L }
    private val applicant = Member(lastName = "김", firstName = "지원", memberStatus = MemberStatus.ACTIVE).also { it.id = 2L }
    private val leader =
        Member(lastName = "이", firstName = "리더", email = "leader@example.org", memberStatus = MemberStatus.ACTIVE)
            .also { it.id = 3L }
    private val leaderAssignment = MinistryAssignment(ministry, leader, LocalDate.of(2026, 1, 1), role = MinistryAssignmentRole.LEADER)

    private fun service() = MinistryRegistrationService(ministries, assignments, resolver, email, notifications, clock)

    @Test
    fun `my applications are resolved from the subject and keep ministry date and status`() {
        val application =
            MinistryAssignment(
                ministry,
                applicant,
                LocalDate.of(2026, 9, 25),
                status = MinistryAssignmentStatus.PENDING,
                selfIntroduction = "소개",
            ).also { it.createdAt = Instant.parse("2026-09-25T09:00:00Z") }
        `when`(resolver.require("applicant-sub")).thenReturn(applicant)
        `when`(assignments.findSelfRegistrationsByMemberId(2L)).thenReturn(listOf(application))

        val result = service().mine("applicant-sub").single()

        assertEquals(ministry.publicId.toString(), result.ministryPublicId)
        assertEquals("찬양팀", result.ministryName)
        assertEquals(Instant.parse("2026-09-25T09:00:00Z"), result.appliedAt)
        assertEquals(MinistryAssignmentStatus.PENDING, result.status)
        verify(assignments).findSelfRegistrationsByMemberId(2L)
    }

    @Test
    fun `active member applies as pending and leader receives introduction`() {
        `when`(resolver.require("applicant-sub")).thenReturn(applicant)
        `when`(ministries.findForUpdateByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(assignments.findCurrentByMinistryIds(listOf(1L))).thenReturn(listOf(leaderAssignment))
        `when`(assignments.saveAndFlush(any())).thenAnswer { it.getArgument<MinistryAssignment>(0) }

        val result = service().apply(ministry.publicId, "  저는 노래를 좋아합니다  ", "applicant-sub")

        assertEquals(MinistryAssignmentStatus.PENDING, result.status)
        assertEquals(true, result.leaderNotified)
        val saved = argumentCaptor<MinistryAssignment>()
        verify(assignments).saveAndFlush(saved.capture())
        assertEquals("저는 노래를 좋아합니다", saved.firstValue.selfIntroduction)
        assertEquals(LocalDate.of(2026, 9, 25), saved.firstValue.startDate)
        verify(email).sendApplication("leader@example.org", "찬양팀", applicant.getFullName(), "저는 노래를 좋아합니다")
    }

    @Test
    fun `pending application prevents duplicate and another email`() {
        `when`(resolver.require("applicant-sub")).thenReturn(applicant)
        `when`(ministries.findForUpdateByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(assignments.existsActiveAssignment(1L, 2L)).thenReturn(true)

        assertEquals(
            409,
            assertThrows<ResponseStatusException> { service().apply(ministry.publicId, "소개", "applicant-sub") }.statusCode.value(),
        )
        verify(email, never()).sendApplication(any(), any(), any(), any())
    }

    @Test
    fun `leader rejects with a private reason and ended row allows another application`() {
        val pending =
            MinistryAssignment(
                ministry,
                applicant,
                LocalDate.of(2026, 9, 1),
                status = MinistryAssignmentStatus.PENDING,
                selfIntroduction = "소개",
            )
        `when`(ministries.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(resolver.require("leader-sub")).thenReturn(leader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L))).thenReturn(listOf(leaderAssignment))
        `when`(assignments.findCurrentForDecision(1L, applicant.publicId)).thenReturn(Optional.of(pending))

        val result =
            service().review(
                ministry.publicId,
                applicant.publicId,
                MinistryRegistrationDecision.REJECT,
                "  다음에는 일정 가능 여부를 알려주세요  ",
                "leader-sub",
                false,
            )

        assertEquals(MinistryAssignmentStatus.REJECTED, result.status)
        assertEquals("다음에는 일정 가능 여부를 알려주세요", result.rejectionMessage)
        assertEquals(LocalDate.of(2026, 9, 25), pending.endDate)
        val notification = argumentCaptor<com.hanmaum.dn.app.features.notifications.domain.AppNotification>()
        verify(notifications).save(notification.capture())
        assertEquals(false, notification.firstValue.body.contains(result.rejectionMessage!!))
    }

    @Test
    fun `leader approves pending application as active`() {
        val pending =
            MinistryAssignment(
                ministry,
                applicant,
                LocalDate.of(2026, 9, 1),
                status = MinistryAssignmentStatus.PENDING,
                selfIntroduction = "소개",
            )
        `when`(ministries.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(resolver.require("leader-sub")).thenReturn(leader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L))).thenReturn(listOf(leaderAssignment))
        `when`(assignments.findCurrentForDecision(1L, applicant.publicId)).thenReturn(Optional.of(pending))

        val result =
            service().review(
                ministry.publicId,
                applicant.publicId,
                MinistryRegistrationDecision.APPROVE,
                null,
                "leader-sub",
                false,
            )

        assertEquals(MinistryAssignmentStatus.ACTIVE, result.status)
        assertEquals(LocalDate.of(2026, 9, 25), pending.startDate)
        assertNull(result.rejectionMessage)
        assertNotNull(pending.createdAt)
        verify(notifications).save(any())
    }

    @Test
    fun `another ministry leader cannot review this ministry's applicant`() {
        `when`(ministries.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(resolver.require("other-leader-sub")).thenReturn(
            Member(lastName = "박", firstName = "다른", memberStatus = MemberStatus.ACTIVE)
                .also { it.id = 4L },
        )
        `when`(assignments.findCurrentByMinistryIds(listOf(1L))).thenReturn(listOf(leaderAssignment))

        assertEquals(
            403,
            assertThrows<ResponseStatusException> {
                service().review(
                    ministry.publicId,
                    applicant.publicId,
                    MinistryRegistrationDecision.APPROVE,
                    null,
                    "other-leader-sub",
                    false,
                )
            }.statusCode.value(),
        )
        verify(assignments, never()).findCurrentForDecision(any(), any())
    }

    @Test
    fun `rejection without leader message leaves the application pending`() {
        val pending =
            MinistryAssignment(
                ministry,
                applicant,
                LocalDate.of(2026, 9, 1),
                status = MinistryAssignmentStatus.PENDING,
                selfIntroduction = "소개",
            )
        `when`(ministries.findByPublicIdAndDeletedAtIsNull(ministry.publicId)).thenReturn(Optional.of(ministry))
        `when`(resolver.require("leader-sub")).thenReturn(leader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L))).thenReturn(listOf(leaderAssignment))
        `when`(assignments.findCurrentForDecision(1L, applicant.publicId)).thenReturn(Optional.of(pending))

        assertEquals(
            400,
            assertThrows<ResponseStatusException> {
                service().review(
                    ministry.publicId,
                    applicant.publicId,
                    MinistryRegistrationDecision.REJECT,
                    "  ",
                    "leader-sub",
                    false,
                )
            }.statusCode.value(),
        )
        assertEquals(MinistryAssignmentStatus.PENDING, pending.status)
        verify(notifications, never()).save(any())
    }
}
