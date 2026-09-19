package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ReconciliationMemberResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ReconciliationResponse
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MemberReconciliationService(
    private val repository: MemberReconciliationRepository,
    private val memberRepository: MemberRepository,
    private val newcomerProfileRepository: NewcomerProfileRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        status: ReconciliationStatus,
        page: Int,
        size: Int,
    ): Page<ReconciliationResponse> =
        repository
            .findAllByStatusAndDeletedAtIsNull(
                status,
                PageRequest.of(page, size.coerceIn(1, 100), Sort.by("createdAt").descending()),
            ).map(::toResponse)

    @Transactional(readOnly = true)
    fun get(publicId: UUID): ReconciliationResponse = toResponse(requireCase(publicId))

    @Transactional
    fun link(
        publicId: UUID,
        memberPublicId: UUID,
        version: Long,
        actorSubject: String,
        merge: Boolean,
    ): ReconciliationResponse {
        val review = repository.findForUpdate(publicId) ?: throw EntityNotFoundException("Reconciliation not found")
        if (review.status == ReconciliationStatus.LINKED && review.selectedMember?.publicId == memberPublicId) return toResponse(review)
        requireOpenAndCurrent(review, version)
        val selected =
            memberRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(memberPublicId)
                .orElseThrow { NewcomerException(HttpStatus.BAD_REQUEST, "The selected member is no longer active.") }
        if (selected.id !in review.candidateMemberIds) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "The selected member is not a current candidate.")
        }
        val registration = review.registrationMember
        val subject =
            registration.keycloakId ?: throw NewcomerException(HttpStatus.CONFLICT, "The registration is no longer linked to an account.")
        if (selected.keycloakId != null && selected.keycloakId != subject) {
            throw NewcomerException(HttpStatus.CONFLICT, "The selected member is already linked to another account.")
        }

        preserveNewcomerHistory(registration, selected, merge)
        registration.keycloakId = null
        registration.memberStatus = MemberStatus.DELETED
        registration.deletedAt = Instant.now()
        memberRepository.saveAndFlush(registration)
        mergeSubmittedProfile(registration, selected)
        selected.keycloakId = subject
        memberRepository.save(selected)

        review.status = ReconciliationStatus.LINKED
        review.selectedMember = selected
        review.resolvedBy = actorSubject
        review.resolvedAt = Instant.now()
        return toResponse(repository.saveAndFlush(review))
    }

    @Transactional
    fun dismiss(
        publicId: UUID,
        version: Long,
        actorSubject: String,
    ): ReconciliationResponse {
        val review = repository.findForUpdate(publicId) ?: throw EntityNotFoundException("Reconciliation not found")
        if (review.status == ReconciliationStatus.DISMISSED) return toResponse(review)
        requireOpenAndCurrent(review, version)
        review.status = ReconciliationStatus.DISMISSED
        review.resolvedBy = actorSubject
        review.resolvedAt = Instant.now()
        return toResponse(repository.saveAndFlush(review))
    }

    private fun preserveNewcomerHistory(
        registration: Member,
        selected: Member,
        merge: Boolean,
    ) {
        val source = registration.id?.let(newcomerProfileRepository::findByMemberIdAndDeletedAtIsNull) ?: return
        val target = selected.id?.let(newcomerProfileRepository::findByMemberIdAndDeletedAtIsNull)
        if (target == null) {
            source.member = selected
            newcomerProfileRepository.save(source)
            return
        }
        if (!merge) {
            throw NewcomerException(HttpStatus.CONFLICT, "Both members have newcomer history; use the merge action.")
        }
        target.englishName = target.englishName ?: source.englishName
        target.kakaoId = target.kakaoId ?: source.kakaoId
        target.previousChurch = target.previousChurch ?: source.previousChurch
        target.workOrSchool = target.workOrSchool ?: source.workOrSchool
        target.assignmentReason = target.assignmentReason ?: source.assignmentReason
        target.overallNotes = target.overallNotes ?: source.overallNotes
        target.additionalNotes = target.additionalNotes ?: source.additionalNotes
        target.visitMotives = target.visitMotives ?: source.visitMotives
        target.caregiver = target.caregiver ?: source.caregiver
        target.assignedGroup = target.assignedGroup ?: source.assignedGroup
        target.firstVisitDate = target.firstVisitDate ?: source.firstVisitDate
        source.deletedAt = Instant.now()
        newcomerProfileRepository.saveAll(listOf(target, source))
    }

    private fun mergeSubmittedProfile(
        source: Member,
        target: Member,
    ) {
        target.firstName = source.firstName
        target.lastName = source.lastName
        target.birthDate = source.birthDate ?: target.birthDate
        target.gender = source.gender ?: target.gender
        target.phoneNumber = source.phoneNumber ?: target.phoneNumber
        target.email = source.email ?: target.email
        target.street = source.street ?: target.street
        target.houseNumber = source.houseNumber ?: target.houseNumber
        target.zipCode = source.zipCode ?: target.zipCode
        target.city = source.city ?: target.city
        target.baptism = source.baptism ?: target.baptism
        target.profileImageUrl = source.profileImageUrl ?: target.profileImageUrl
        target.group = source.group ?: target.group
    }

    private fun requireOpenAndCurrent(
        review: MemberReconciliation,
        version: Long,
    ) {
        if (review.status != ReconciliationStatus.OPEN) {
            throw NewcomerException(HttpStatus.CONFLICT, "The reconciliation has already been resolved.")
        }
        if (review.version != version) {
            throw NewcomerException(HttpStatus.CONFLICT, "The reconciliation changed. Reload and retry.")
        }
    }

    private fun requireCase(publicId: UUID): MemberReconciliation =
        repository.findByPublicIdAndDeletedAtIsNull(publicId) ?: throw EntityNotFoundException("Reconciliation not found")

    private fun toResponse(review: MemberReconciliation): ReconciliationResponse {
        val candidates = memberRepository.findAllById(review.candidateMemberIds).map(Member::toReconciliationResponse)
        return ReconciliationResponse(
            publicId = review.publicId.toString(),
            status = review.status,
            reasons = review.reasons.split(',').filter(String::isNotBlank),
            conflictFields =
                review.conflictFields
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    .orEmpty(),
            registrationMember = review.registrationMember.toReconciliationResponse(),
            candidates = candidates,
            selectedMemberPublicId = review.selectedMember?.publicId?.toString(),
            version = review.version,
            createdAt = review.createdAt,
            resolvedAt = review.resolvedAt,
        )
    }
}

private fun Member.toReconciliationResponse() =
    ReconciliationMemberResponse(
        publicId = publicId.toString(),
        firstName = firstName,
        lastName = lastName,
        email = email,
        birthDate = birthDate,
        phoneNumber = phoneNumber,
        linked = keycloakId != null,
    )
