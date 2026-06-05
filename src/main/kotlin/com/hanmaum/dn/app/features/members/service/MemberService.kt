package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.applyPatch
import com.hanmaum.dn.app.features.members.api.toDto
import com.hanmaum.dn.app.features.members.api.toEntity
import com.hanmaum.dn.app.features.members.api.toResponse
import com.hanmaum.dn.app.features.members.api.toSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MinistryHistoryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberTrainingsRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.SummaryTrainingDto
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMyProfileRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import com.hanmaum.dn.app.features.ministry.repository.MinistryRegistrationRepository
import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import jakarta.persistence.EntityNotFoundException
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val churchGroupRepository: ChurchGroupRepository,
    private val userTrainingRepository: UserTrainingRepository,
    private val trainingRepository: TrainingRepository,
    private val ministryRegistrationRepository: MinistryRegistrationRepository,
    private val keycloak: Keycloak,
    @Value("\${app.keycloak.realm}") private val realm: String,
) {
    /**
     * READ Operations
     * ──────────────────────────────────────────────────────────────────
     * Paginated list of active (non-deleted) members, sorted by lastName ASC.
     * [search] is optional; filters on lastName / firstName / email.
     */
    @Transactional(readOnly = true)
    fun getMembers(
        search: String?,
        status: MemberStatus?,
        baptism: Baptism?,
        page: Int,
        size: Int,
    ): Page<MemberSummaryDto> {
        val pageable =
            PageRequest.of(
                page,
                size,
                Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()),
            )
        val members =
            memberRepository
                .findActiveMembers(search?.takeIf { it.isNotBlank() }.orEmpty(), status, baptism, pageable)

        // Enrich the page in batch queries (no N+1):
        //  - all trainings (with status) per member = grid chips, ordered by progression
        //  - latest completed training               = highest sort_order among COMPLETED chips
        //  - active ministry                         = most recent registration_period among APPROVED rows
        val memberIds = members.content.mapNotNull { it.id }
        val trainingsByMember: Map<Long, List<SummaryTrainingDto>> =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                userTrainingRepository
                    .findByMemberIds(memberIds)
                    .groupBy { it.memberId }
                    .mapValues { (_, rows) ->
                        rows
                            .sortedBy { it.sortOrder }
                            .map { SummaryTrainingDto(name = it.trainingName, status = it.status.name) }
                    }
            }
        val latestTrainingByMember: Map<Long, String> =
            trainingsByMember
                .mapNotNull { (memberId, rows) ->
                    rows.lastOrNull { it.status == TrainingStatus.COMPLETED.name }?.let { memberId to it.name }
                }.toMap()
        val activeMinistryByMember: Map<Long, String> =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                ministryRegistrationRepository
                    .findApprovedByMemberIds(memberIds)
                    .groupBy { it.memberId }
                    .mapValues { (_, rows) -> rows.maxBy { it.registrationPeriod }.ministryName }
            }

        return members.map {
            it.toSummaryDto(
                latestTraining = it.id?.let(latestTrainingByMember::get),
                trainings = it.id?.let(trainingsByMember::get).orEmpty(),
                activeMinistry = it.id?.let(activeMinistryByMember::get),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMemberByPublicId(publicId: UUID): MemberDto {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        val memberId = member.id!!
        val trainings = userTrainingRepository.findByMemberId(memberId).map { it.toDto() }
        val ministries = ministryRegistrationRepository.findByMemberId(memberId).map { it.toHistoryDto() }
        return member.toDto(trainings, ministries)
    }

    /**
     * Replaces a member's entire training set (PUT semantics). Existing rows are
     * removed and re-created from the request. Returns the refreshed member detail.
     */
    @Transactional
    fun replaceMemberTrainings(
        publicId: UUID,
        request: ReplaceMemberTrainingsRequest,
    ): MemberDto {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        val memberId = member.id!!

        // Flush the delete before re-inserting: Hibernate orders inserts before deletes
        // by default, which would otherwise violate the (user_id, training_id) unique key.
        userTrainingRepository.deleteByMemberId(memberId)
        userTrainingRepository.flush()
        val rows =
            request.trainings.map { item ->
                val training =
                    trainingRepository
                        .findByPublicIdAndDeletedAtIsNull(UUID.fromString(item.trainingPublicId))
                        .orElseThrow { EntityNotFoundException("Training not found: ${item.trainingPublicId}") }
                val status =
                    try {
                        TrainingStatus.valueOf(item.status.uppercase())
                    } catch (e: IllegalArgumentException) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown training status: ${item.status}")
                    }
                UserTraining(
                    member = member,
                    training = training,
                    status = status,
                    completedAt = item.completedAt,
                )
            }
        userTrainingRepository.saveAll(rows)

        val trainings = rows.map { it.toDto() }
        val ministries = ministryRegistrationRepository.findByMemberId(memberId).map { it.toHistoryDto() }
        return member.toDto(trainings, ministries)
    }

    private fun MinistryRegistration.toHistoryDto(): MinistryHistoryDto =
        MinistryHistoryDto(
            ministryPublicId = this.ministry.publicId.toString(),
            name = this.ministry.name,
            registrationPeriod = this.registrationPeriod,
            status = this.status.name,
        )

    /**
     * Own-profile lookup — [keycloakSubject] is the JWT `sub` claim (Keycloak UUID).
     * Falls back to email for legacy records that pre-date the keycloakId column.
     */
    @Transactional(readOnly = true)
    fun getMemberProfile(
        keycloakSubject: String,
        email: String?,
    ): MemberResponse {
        val member =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: email?.let { memberRepository.findByEmailAndDeletedAtIsNull(it) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found.")
        return member.toResponse()
    }

    @Transactional
    fun updateMyProfile(
        keycloakSubject: String,
        email: String?,
        request: UpdateMyProfileRequest,
    ): MemberResponse {
        val member =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: email?.let { memberRepository.findByEmailAndDeletedAtIsNull(it) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found.")
        request.phoneNumber?.let { member.phoneNumber = it }
        request.profileImageUrl?.let { member.profileImageUrl = it }
        return memberRepository.save(member).toResponse()
    }

    // ─── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createMember(request: CreateMemberRequest): MemberDto {
        request.email?.let { email ->
            if (memberRepository.findByEmailAndDeletedAtIsNull(email) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다: $email")
            }
        }
        val member = request.toEntity()
        request.groupId?.let { gid ->
            member.group =
                churchGroupRepository
                    .findById(gid)
                    .orElseThrow { EntityNotFoundException("Group not found: $gid") }
        }
        return memberRepository.save(member).toDto()
    }

    @Transactional
    fun updateMember(
        publicId: UUID,
        request: UpdateMemberRequest,
    ): MemberDto {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }

        member.applyPatch(request)

        request.groupId?.let { gid ->
            if (member.group?.id != gid) {
                member.group =
                    churchGroupRepository
                        .findById(gid)
                        .orElseThrow { EntityNotFoundException("Group not found: $gid") }
            }
        }
        return memberRepository.save(member).toDto()
    }

    /**
     * Soft-delete: marks member DELETED, sets deletedAt. Terminal — cannot be undone via API.
     */
    @Transactional
    fun softDeleteMember(publicId: UUID) {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found or already deleted: $publicId") }
        member.memberStatus = MemberStatus.DELETED
        member.deletedAt = Instant.now()
        memberRepository.save(member)
    }

    /**
     * Self-registration: creates DB record + Keycloak user, stores keycloakId on member.
     */
    @Transactional
    fun registerMember(req: RegisterMemberRequest): Member {
        if (memberRepository.findByEmailAndDeletedAtIsNull(req.email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다: ${req.email}")
        }

        // Discriminator logic: if name already exists, append A, B, C…
        val existingWithSameName = memberRepository.findSimilarNames(req.firstName, req.lastName)
        val takenDiscriminators = existingWithSameName.map { it.discriminator }.toSet()
        val discriminator: String? =
            if (takenDiscriminators.contains(null)) {
                var c = 'A'
                while (takenDiscriminators.contains(c.toString())) c++
                c.toString()
            } else {
                null
            }

        val newMember =
            Member(
                lastName = req.lastName,
                firstName = req.firstName,
                discriminator = discriminator,
                email = req.email,
                gender =
                    try {
                        req.gender?.let { Gender.valueOf(it.uppercase()) }
                    } catch (e: Exception) {
                        null
                    },
                baptism =
                    try {
                        req.baptism?.let { Baptism.valueOf(it.uppercase()) }
                    } catch (e: Exception) {
                        null
                    },
                city = req.city,
                birthDate = req.birthDate,
                phoneNumber = req.phoneNumber,
                street = req.street,
                zipCode = req.zipCode,
                registrationDate = LocalDate.now(),
                memberStatus = MemberStatus.PENDING,
            )

        val savedMember = memberRepository.save(newMember)

        // Create Keycloak user and store keycloakId back on the member record
        val keycloakUser =
            UserRepresentation().apply {
                username = req.email
                email = req.email
                firstName = req.firstName
                lastName = req.lastName
                isEnabled = true
                isEmailVerified = false
                credentials =
                    listOf(
                        CredentialRepresentation().apply {
                            type = CredentialRepresentation.PASSWORD
                            value = req.password
                            isTemporary = false
                        },
                    )
            }

        val kcResponse = keycloak.realm(realm).users().create(keycloakUser)
        if (kcResponse.status != 201) {
            val body = kcResponse.readEntity(String::class.java)
            throw RuntimeException("Keycloak 사용자 생성 실패 (HTTP ${kcResponse.status}): $body")
        }

        val keycloakId =
            kcResponse.location
                ?.path
                ?.substringAfterLast("/")
                .orEmpty()
        if (keycloakId.isNotBlank()) {
            savedMember.keycloakId = keycloakId
            memberRepository.save(savedMember)
        }

        return savedMember
    }
}
