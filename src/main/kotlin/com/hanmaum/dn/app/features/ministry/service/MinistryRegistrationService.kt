package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.ministry.api.toDto
import com.hanmaum.dn.app.features.ministry.api.toRegistrationDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDecision
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDto
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class MinistryRegistrationService(
    private val ministryRepository: MinistryRepository,
    private val assignmentRepository: MinistryAssignmentRepository,
    private val currentMemberResolver: CurrentMemberResolver,
    private val leaderEmailSender: MinistryLeaderEmailSender,
    private val notificationRepository: AppNotificationRepository,
    private val clock: Clock,
) {
    /** Register the authenticated active member as pending and email their introduction to the leader. */
    @Transactional
    fun apply(
        ministryPublicId: UUID,
        selfIntroduction: String,
        keycloakSubject: String,
    ): MinistryRegistrationDto {
        val member = currentMemberResolver.require(keycloakSubject)
        if (member.memberStatus != MemberStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only active members can apply to a ministry")
        }
        val ministry =
            ministryRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(ministryPublicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }
        if (!ministry.isMinistryActive) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "This ministry is not accepting applications")
        }
        if (assignmentRepository.existsActiveAssignment(ministry.id!!, member.id!!)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "The member already has a current team entry")
        }
        val leader =
            currentLeader(ministry)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "This ministry has no assigned leader")
        val leaderEmail =
            leader.member.email?.takeIf { it.isNotBlank() }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "The ministry leader has no email address")
        val assignment =
            assignmentRepository.saveAndFlush(
                MinistryAssignment(
                    ministry = ministry,
                    member = member,
                    startDate = LocalDate.now(clock).withDayOfMonth(1),
                    status = MinistryAssignmentStatus.PENDING,
                    selfIntroduction = selfIntroduction.trim(),
                ),
            )
        leaderEmailSender.sendApplication(
            leaderEmail = leaderEmail,
            ministryName = ministry.name,
            applicantName = member.getFullName(),
            selfIntroduction = assignment.selfIntroduction!!,
        )
        assignment.leaderNotifiedAt = clock.instant()
        return assignment.toRegistrationDto()
    }

    @Transactional(readOnly = true)
    fun mine(keycloakSubject: String): List<MinistryRegistrationDto> {
        val member = currentMemberResolver.require(keycloakSubject)
        return assignmentRepository.findSelfRegistrationsByMemberId(member.id!!).map { it.toRegistrationDto() }
    }

    @Transactional(readOnly = true)
    fun mineForMinistry(
        ministryPublicId: UUID,
        keycloakSubject: String,
    ): MinistryRegistrationDto? {
        val member = currentMemberResolver.require(keycloakSubject)
        val ministry = findMinistry(ministryPublicId)
        return assignmentRepository
            .findSelfRegistrationsByMinistryAndMember(ministry.id!!, member.id!!)
            .firstOrNull()
            ?.toRegistrationDto()
    }

    /** Pending team rows are visible only to an admin or this ministry's assigned leader. */
    @Transactional(readOnly = true)
    fun pendingMembers(
        ministryPublicId: UUID,
        reviewerSubject: String,
        isAdmin: Boolean,
    ): List<ActiveMinistryMemberDto> {
        val ministry = findMinistry(ministryPublicId)
        requireReviewer(ministry, reviewerSubject, isAdmin)
        return assignmentRepository.findPendingByMinistryPublicId(ministryPublicId).map { it.toDto() }
    }

    /** Decide one pending self-application from the team table. Rejected members may apply again. */
    @Transactional
    fun review(
        ministryPublicId: UUID,
        memberPublicId: UUID,
        decision: MinistryRegistrationDecision,
        message: String?,
        reviewerSubject: String,
        isAdmin: Boolean,
    ): MinistryRegistrationDto {
        val ministry = findMinistry(ministryPublicId)
        requireReviewer(ministry, reviewerSubject, isAdmin)
        val assignment =
            assignmentRepository
                .findCurrentForDecision(ministry.id!!, memberPublicId)
                .orElseThrow { EntityNotFoundException("Pending ministry application not found: $memberPublicId") }
        if (assignment.status != MinistryAssignmentStatus.PENDING || assignment.selfIntroduction == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This team entry is not a pending self-application")
        }
        when (decision) {
            MinistryRegistrationDecision.APPROVE -> {
                assignment.status = MinistryAssignmentStatus.ACTIVE
                assignment.startDate = LocalDate.now(clock).withDayOfMonth(1)
            }
            MinistryRegistrationDecision.REJECT -> {
                val reason =
                    message?.trim()?.takeIf { it.isNotEmpty() }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection message is required")
                assignment.status = MinistryAssignmentStatus.REJECTED
                assignment.endDate = LocalDate.now(clock)
                assignment.rejectionMessage = reason
            }
        }
        notificationRepository.save(
            AppNotification(
                member = assignment.member,
                type = NotificationType.MINISTRY,
                title = "사역 신청 결과",
                body = if (decision == MinistryRegistrationDecision.APPROVE) "사역 참여가 승인되었습니다." else "사역 신청 결과와 메시지를 확인해 주세요.",
                referenceType = NotificationReferenceType.MINISTRY,
                referencePublicId = ministry.publicId,
            ),
        )
        return assignment.toRegistrationDto()
    }

    private fun findMinistry(publicId: UUID): Ministry =
        ministryRepository
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("Ministry not found: $publicId") }

    private fun currentLeader(ministry: Ministry): MinistryAssignment? =
        assignmentRepository
            .findCurrentByMinistryIds(listOf(ministry.id!!))
            .firstOrNull { it.role == MinistryAssignmentRole.LEADER }

    private fun requireReviewer(
        ministry: Ministry,
        reviewerSubject: String,
        isAdmin: Boolean,
    ) {
        if (isAdmin) return
        val reviewer = currentMemberResolver.require(reviewerSubject)
        if (currentLeader(ministry)?.member?.id != reviewer.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only this ministry's leader can review applications")
        }
    }
}
