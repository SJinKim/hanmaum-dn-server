package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.GraduateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerGraduationResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerGraduation
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerGraduationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class NewcomerGraduationService(
    private val profileRepository: NewcomerProfileRepository,
    private val graduationRepository: NewcomerGraduationRepository,
    private val groupRepository: ChurchGroupRepository,
    private val memberRepository: MemberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun graduate(
        newcomerPublicId: UUID,
        request: GraduateNewcomerRequest,
        actorSubject: String,
    ): NewcomerGraduationResponse {
        val profile = profileRepository.findForUpdate(newcomerPublicId) ?: throw EntityNotFoundException("Newcomer not found")
        val date = request.graduatedAt ?: LocalDate.now()
        val group = requireAssignableGroup(request.groupPublicId)
        val existing = profile.id?.let(graduationRepository::findByNewcomerProfileIdAndDeletedAtIsNull)
        if (existing != null) {
            if (existing.group.publicId == group.publicId &&
                existing.cohortNumber == request.cohortNumber &&
                existing.graduatedOn == date
            ) {
                return existing.toResponse()
            }
            throw NewcomerException(HttpStatus.CONFLICT, "The newcomer has already graduated with different parameters.")
        }

        val member = profile.member
        member.group = group
        memberRepository.save(member)
        profile.lifecycleStatus = NewcomerLifecycle.GRADUATED
        profile.assignedGroup = group
        request.assignmentReason?.let { profile.assignmentReason = it }
        profileRepository.save(profile)
        val graduation =
            graduationRepository.save(
                NewcomerGraduation(
                    newcomerProfile = profile,
                    member = member,
                    group = group,
                    cohortNumber = request.cohortNumber,
                    cohortLabel = "${date.year}-${request.cohortNumber}기",
                    graduatedOn = date,
                    assignmentReason = request.assignmentReason,
                    graduatedBy = actorSubject,
                ),
            )
        log.info(
            "Graduated newcomer profileId={} memberId={} groupId={} cohort={}",
            profile.id,
            member.id,
            group.id,
            graduation.cohortLabel,
        )
        return graduation.toResponse()
    }

    private fun requireAssignableGroup(publicId: String): ChurchGroup {
        val uuid =
            try {
                UUID.fromString(publicId)
            } catch (_: IllegalArgumentException) {
                throw NewcomerException(HttpStatus.BAD_REQUEST, "groupPublicId must be a UUID.")
            }
        val group =
            groupRepository
                .findByPublicIdAndDeletedAtIsNull(uuid)
                .orElseThrow { NewcomerException(HttpStatus.BAD_REQUEST, "The selected group is not active.") }
        if (group.name.contains("새가족")) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "The newcomer group cannot be a graduation destination.")
        }
        return group
    }
}

private fun NewcomerGraduation.toResponse() =
    NewcomerGraduationResponse(
        publicId = publicId.toString(),
        newcomerPublicId = newcomerProfile.publicId.toString(),
        memberPublicId = member.publicId.toString(),
        groupPublicId = group.publicId.toString(),
        cohortNumber = cohortNumber,
        cohortLabel = cohortLabel,
        graduatedOn = graduatedOn,
        assignmentReason = assignmentReason,
    )
