package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.GraduationReason
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import com.hanmaum.dn.app.features.members.repository.MemberGraduationRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Records and reverses a member's departure from the DN community.
 *
 * Graduating deactivates the member, which removes app access through the existing
 * MemberStatusInterceptor. It does not soft-delete them: a graduate stays visible in
 * admin listings and keeps their training history.
 */
@Service
class MemberGraduationService(
    private val memberRepository: MemberRepository,
    private val graduationRepository: MemberGraduationRepository,
) {
    private val log = LoggerFactory.getLogger(MemberGraduationService::class.java)

    @Transactional
    fun graduate(
        publicId: UUID,
        graduatedOn: LocalDate,
        reason: GraduationReason,
        note: String?,
        actorSubject: String,
    ): MemberGraduation {
        val member = requireMember(publicId)
        val memberId = member.id!!

        if (member.memberStatus == MemberStatus.DELETED) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "A deleted member cannot be graduated.",
            )
        }
        if (graduationRepository.findOpenByMemberId(memberId) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "This member already has an open graduation.",
            )
        }

        val graduation =
            graduationRepository.save(
                MemberGraduation(
                    member = member,
                    graduatedOn = graduatedOn,
                    reason = reason,
                    graduatedBy = actorSubject,
                    previousMemberStatus = member.memberStatus,
                    note = note,
                ),
            )
        member.memberStatus = MemberStatus.INACTIVE

        // Counters and identifiers only — never the member's name or the note.
        log.info("Graduated member memberId={} reason={} on={}", memberId, reason, graduatedOn)
        return graduation
    }

    @Transactional
    fun reinstate(
        publicId: UUID,
        actorSubject: String,
    ): MemberGraduation {
        val member = requireMember(publicId)
        val memberId = member.id!!

        val open =
            graduationRepository.findOpenByMemberId(memberId)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "This member has no open graduation.",
                )

        open.revertedAt = Instant.now()
        open.revertedBy = actorSubject
        member.memberStatus = restoredStatus(open, memberId)
        val saved = graduationRepository.save(open)

        log.info("Reinstated member memberId={} restoredStatus={}", memberId, member.memberStatus)
        return saved
    }

    @Transactional(readOnly = true)
    fun history(publicId: UUID): List<MemberGraduation> =
        graduationRepository.findAllByMemberIdOrderByGraduatedOnDesc(requireMember(publicId).id!!)

    @Transactional(readOnly = true)
    fun openGraduation(publicId: UUID): MemberGraduation? = graduationRepository.findOpenByMemberId(requireMember(publicId).id!!)

    private fun requireMember(publicId: UUID): Member =
        memberRepository
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("Member not found: $publicId") }

    /**
     * The status to restore on reinstatement. The snapshot is stored as free text so old
     * rows survive enum changes, so a value that no longer exists has to be handled here
     * rather than blowing up the request — falling back to ACTIVE, which is what an
     * admin reinstating someone means.
     */
    private fun restoredStatus(
        graduation: MemberGraduation,
        memberId: Long,
    ): MemberStatus =
        runCatching { MemberStatus.valueOf(graduation.previousMemberStatus) }
            .getOrElse {
                log.warn(
                    "Unknown previous status on graduation, restoring active memberId={} value={}",
                    memberId,
                    graduation.previousMemberStatus,
                )
                MemberStatus.ACTIVE
            }
}
