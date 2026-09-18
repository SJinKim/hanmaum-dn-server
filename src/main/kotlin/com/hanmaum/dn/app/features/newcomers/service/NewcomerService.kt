package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.newcomers.api.toResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerOption
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerOptionsResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

private const val NEWCOMER_MINISTRY_NAME = "새가족"
private const val LIST_SEPARATOR = "\u001F"

@Service
class NewcomerService(
    private val profileRepository: NewcomerProfileRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: ChurchGroupRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
) {
    @Transactional
    fun create(request: CreateNewcomerRequest): NewcomerResponse {
        requireAvailableEmail(request.email)
        val member =
            memberRepository.save(
                Member(
                    lastName = request.lastName.trim(),
                    firstName = request.firstName.trim(),
                    gender = request.gender,
                    birthDate = request.birthDate,
                    phoneNumber = request.phoneNumber?.trim(),
                    email = request.email?.trim()?.lowercase(),
                    street = request.street,
                    houseNumber = request.houseNumber,
                    zipCode = request.zipCode,
                    city = request.city,
                    registrationDate = request.registrationDate ?: LocalDate.now(),
                    memberStatus = MemberStatus.PENDING,
                    baptism = request.baptism,
                    profileImageUrl = request.profileImageUrl,
                    keycloakId = null,
                ),
            )
        val profile =
            NewcomerProfile(
                member = member,
                lifecycleStatus = request.lifecycleStatus,
                intakeRound = request.intakeRound,
                hasVisited = request.hasVisited,
                caregiver = resolveCaregiver(request.caregiverPublicId),
                identityStatus = request.identityStatus,
                workOrSchool = request.workOrSchool,
                firstVisitDate = request.firstVisitDate,
                assignedGroup = resolveAssignableGroup(request.assignedGroupPublicId),
                assignmentReason = request.assignmentReason,
                overallNotes = request.overallNotes,
                postAssignmentAttendance = request.postAssignmentAttendance,
                englishName = request.englishName,
                kakaoId = request.kakaoId,
                previousChurch = request.previousChurch,
                churchExperience = request.churchExperience,
                visitMotives = encodeList(request.visitMotives),
                additionalNotes = request.additionalNotes,
            )
        return profileRepository.save(profile).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(publicId: UUID): NewcomerResponse = requireProfile(publicId).toResponse()

    @Transactional(readOnly = true)
    fun list(
        search: String?,
        lifecycleStatus: NewcomerLifecycle?,
        hasVisited: Boolean?,
        caregiverPublicId: UUID?,
        groupPublicId: UUID?,
        identityStatus: com.hanmaum.dn.app.features.newcomers.domain.NewcomerIdentityStatus?,
        attendance: PostAssignmentAttendance?,
        registeredFrom: LocalDate?,
        registeredTo: LocalDate?,
        sort: String,
        direction: String,
        page: Int,
        size: Int,
    ): Page<NewcomerResponse> {
        if (page < 0 || size !in 1..100) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100.")
        }
        if (!direction.equals("asc", true) && !direction.equals("desc", true)) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "direction must be asc or desc.")
        }
        val needle = search?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        val filtered =
            profileRepository
                .findAllByDeletedAtIsNull()
                .asSequence()
                .filter { lifecycleStatus == null || it.lifecycleStatus == lifecycleStatus }
                .filter { hasVisited == null || it.hasVisited == hasVisited }
                .filter { caregiverPublicId == null || it.caregiver?.publicId == caregiverPublicId }
                .filter { groupPublicId == null || it.assignedGroup?.publicId == groupPublicId }
                .filter { identityStatus == null || it.identityStatus == identityStatus }
                .filter { attendance == null || it.postAssignmentAttendance == attendance }
                .filter { registeredFrom == null || !registrationDate(it).isBefore(registeredFrom) }
                .filter { registeredTo == null || !registrationDate(it).isAfter(registeredTo) }
                .filter {
                    needle == null ||
                        it.member
                            .getFullName()
                            .lowercase()
                            .contains(needle) ||
                        it.englishName?.lowercase()?.contains(needle) == true ||
                        it.member.email
                            ?.lowercase()
                            ?.contains(needle) == true ||
                        it.member.phoneNumber?.contains(needle) == true
                }.toList()
        val comparator = comparator(sort)
        val sorted = if (direction.equals("desc", true)) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
        val from = (page * size).coerceAtMost(sorted.size)
        val to = (from + size).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(from, to).map { it.toResponse() }, PageRequest.of(page, size), sorted.size.toLong())
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: UpdateNewcomerRequest,
    ): NewcomerResponse {
        val profile = profileRepository.findForUpdate(publicId) ?: throw EntityNotFoundException("Newcomer not found")
        if (profile.version != request.version) {
            throw NewcomerException(HttpStatus.CONFLICT, "The newcomer was changed by another request. Reload and retry.")
        }
        val member = profile.member
        request.email?.let {
            requireAvailableEmail(it, member.id)
            member.email = it.trim().lowercase()
        }
        request.lastName?.let { member.lastName = it.trim() }
        request.firstName?.let { member.firstName = it.trim() }
        request.gender?.let { member.gender = it }
        request.birthDate?.let { member.birthDate = it }
        request.phoneNumber?.let { member.phoneNumber = it.trim() }
        request.street?.let { member.street = it }
        request.houseNumber?.let { member.houseNumber = it }
        request.zipCode?.let { member.zipCode = it }
        request.city?.let { member.city = it }
        request.baptism?.let { member.baptism = it }
        request.profileImageUrl?.let { member.profileImageUrl = it }
        request.registrationDate?.let { member.registrationDate = it }
        request.lifecycleStatus?.let { profile.lifecycleStatus = it }
        request.intakeRound?.let { profile.intakeRound = it }
        request.hasVisited?.let { profile.hasVisited = it }
        request.caregiverPublicId?.let { profile.caregiver = resolveCaregiver(it) }
        request.identityStatus?.let { profile.identityStatus = it }
        request.workOrSchool?.let { profile.workOrSchool = it }
        request.firstVisitDate?.let { profile.firstVisitDate = it }
        request.assignedGroupPublicId?.let { profile.assignedGroup = resolveAssignableGroup(it) }
        request.assignmentReason?.let { profile.assignmentReason = it }
        request.overallNotes?.let { profile.overallNotes = it }
        request.postAssignmentAttendance?.let { profile.postAssignmentAttendance = it }
        request.englishName?.let { profile.englishName = it }
        request.kakaoId?.let { profile.kakaoId = it }
        request.previousChurch?.let { profile.previousChurch = it }
        request.churchExperience?.let { profile.churchExperience = it }
        request.visitMotives?.let { profile.visitMotives = encodeList(it) }
        request.additionalNotes?.let { profile.additionalNotes = it }
        return profileRepository.saveAndFlush(profile).toResponse()
    }

    @Transactional
    fun softDelete(publicId: UUID) {
        val profile = profileRepository.findForUpdate(publicId) ?: throw EntityNotFoundException("Newcomer not found")
        profile.deletedAt = java.time.Instant.now()
        profile.lifecycleStatus = NewcomerLifecycle.ARCHIVED
        profileRepository.save(profile)
    }

    @Transactional(readOnly = true)
    fun options(): NewcomerOptionsResponse {
        val caregivers =
            memberRepository
                .findAllByDeletedAtIsNull()
                .filter { it.memberStatus == MemberStatus.ACTIVE }
                .filter { member ->
                    member.id?.let {
                        ministryAssignmentRepository.existsActiveAssignmentByMemberAndMinistryName(it, NEWCOMER_MINISTRY_NAME)
                    } == true
                }.map { NewcomerOption(it.publicId.toString(), it.getFullName()) }
                .sortedBy { it.label }
        val groups =
            groupRepository
                .findAllByDeletedAtIsNullOrderByDivisionAscNameAsc()
                .filterNot(::isNewcomerGroup)
                .map { NewcomerOption(it.publicId.toString(), it.getFullName()) }
        return NewcomerOptionsResponse(caregivers, groups)
    }

    private fun requireProfile(publicId: UUID): NewcomerProfile =
        profileRepository.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow { EntityNotFoundException("Newcomer not found") }

    private fun requireAvailableEmail(
        email: String?,
        currentMemberId: Long? = null,
    ) {
        if (email.isNullOrBlank()) return
        val existing = memberRepository.findByEmailAndDeletedAtIsNull(email.trim().lowercase())
        if (existing != null && (currentMemberId == null || existing.id != currentMemberId)) {
            throw NewcomerException(HttpStatus.CONFLICT, "The email is already assigned to an active member.")
        }
    }

    private fun resolveCaregiver(publicId: String?): Member? {
        if (publicId.isNullOrBlank()) return null
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(parseUuid(publicId, "caregiverPublicId"))
                .orElseThrow { NewcomerException(HttpStatus.BAD_REQUEST, "The selected caregiver is not active.") }
        val eligible =
            member.memberStatus == MemberStatus.ACTIVE &&
                member.id?.let {
                    ministryAssignmentRepository.existsActiveAssignmentByMemberAndMinistryName(it, NEWCOMER_MINISTRY_NAME)
                } == true
        if (!eligible) throw NewcomerException(HttpStatus.BAD_REQUEST, "The selected caregiver is not active in the newcomer ministry.")
        return member
    }

    private fun resolveAssignableGroup(publicId: String?): ChurchGroup? {
        if (publicId.isNullOrBlank()) return null
        val group =
            groupRepository
                .findByPublicIdAndDeletedAtIsNull(parseUuid(publicId, "assignedGroupPublicId"))
                .orElseThrow { NewcomerException(HttpStatus.BAD_REQUEST, "The selected group is not active.") }
        if (isNewcomerGroup(
                group,
            )
        ) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "The newcomer group cannot be assigned as a destination group.")
        }
        return group
    }

    private fun isNewcomerGroup(group: ChurchGroup): Boolean =
        group.name.contains(NEWCOMER_MINISTRY_NAME) || group.division?.contains(NEWCOMER_MINISTRY_NAME) == true

    private fun parseUuid(
        value: String,
        field: String,
    ): UUID =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "$field must be a UUID.")
        }

    private fun comparator(sort: String): Comparator<NewcomerProfile> =
        when (sort) {
            "name" -> compareBy { it.member.getFullName().lowercase() }
            "status" -> compareBy { it.lifecycleStatus.name }
            "firstVisitDate" -> compareBy(nullsLast()) { it.firstVisitDate }
            "updatedAt" -> compareBy(nullsLast()) { it.updatedAt }
            "registrationDate" -> compareBy(nullsLast()) { it.member.registrationDate }
            else -> throw NewcomerException(HttpStatus.BAD_REQUEST, "Unsupported sort field.")
        }

    private fun registrationDate(profile: NewcomerProfile): LocalDate = profile.member.registrationDate ?: LocalDate.MIN
}

private fun encodeList(values: List<String>): String? =
    values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .also {
            if (it.any { value -> value.contains(LIST_SEPARATOR) }) {
                throw NewcomerException(HttpStatus.BAD_REQUEST, "visitMotives contains an unsupported control character.")
            }
        }.takeIf(List<String>::isNotEmpty)
        ?.joinToString(LIST_SEPARATOR)
