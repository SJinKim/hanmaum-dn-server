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
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberMinistriesRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberTrainingsRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.SummaryTrainingDto
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMyProfileRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.ProcessingException
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
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
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val ministryRepository: MinistryRepository,
    private val keycloak: Keycloak,
    @Value("\${app.keycloak.realm:hanmaum}") private val realm: String,
    @Value("\${app.member-retention.days:30}") private val memberRetentionDays: Long = 30,
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)

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
        //  - active ministries                       = ministry names where end_date IS NULL
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
        val activeMinistriesByMember: Map<Long, List<String>> =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                ministryAssignmentRepository
                    .findActiveByMemberIds(memberIds)
                    .groupBy { it.memberId }
                    .mapValues { (_, rows) -> rows.map { it.ministryName }.sorted() }
            }

        return members.map {
            it.toSummaryDto(
                latestTraining = it.id?.let(latestTrainingByMember::get),
                trainings = it.id?.let(trainingsByMember::get).orEmpty(),
                activeMinistries = it.id?.let(activeMinistriesByMember::get).orEmpty(),
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
        val ministries = ministryAssignmentRepository.findByMemberId(memberId).map { it.toHistoryDto() }
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
        val ministries = ministryAssignmentRepository.findByMemberId(memberId).map { it.toHistoryDto() }
        return member.toDto(trainings, ministries)
    }

    /**
     * Replaces a member's entire ministry assignment set (PUT semantics). Existing
     * rows are deleted and re-created from the request. Returns refreshed member detail.
     */
    @Transactional
    fun replaceMemberMinistries(
        publicId: UUID,
        request: ReplaceMemberMinistriesRequest,
    ): MemberDto {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        val memberId = member.id!!

        // Flush the delete before re-inserting so Hibernate doesn't reorder the new
        // inserts ahead of the delete (mirrors replaceMemberTrainings).
        ministryAssignmentRepository.deleteByMemberId(memberId)
        ministryAssignmentRepository.flush()

        val rows =
            request.ministries.map { item ->
                val ministry =
                    ministryRepository
                        .findByPublicIdAndDeletedAtIsNull(UUID.fromString(item.ministryPublicId))
                        .orElseThrow { EntityNotFoundException("Ministry not found: ${item.ministryPublicId}") }
                MinistryAssignment(
                    ministry = ministry,
                    member = member,
                    startDate = item.startDate,
                    endDate = item.endDate,
                    note = item.note,
                )
            }
        ministryAssignmentRepository.saveAll(rows)

        // Re-read both lists from the DB so the returned detail reflects persisted state.
        val trainings = userTrainingRepository.findByMemberId(memberId).map { it.toDto() }
        val ministries = ministryAssignmentRepository.findByMemberId(memberId).map { it.toHistoryDto() }
        return member.toDto(trainings, ministries)
    }

    private fun MinistryAssignment.toHistoryDto(): MinistryHistoryDto =
        MinistryHistoryDto(
            ministryPublicId = this.ministry.publicId.toString(),
            name = this.ministry.name,
            startDate = this.startDate,
            endDate = this.endDate,
            note = this.note,
        )

    /**
     * Own-profile lookup — [keycloakSubject] is the JWT `sub` claim (Keycloak UUID).
     * Falls back to email for legacy records that pre-date the keycloakId column.
     */
    @Transactional
    fun getMemberProfile(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean = false,
    ): MemberResponse = resolveAndLinkMember(keycloakSubject, email, emailVerified).toResponse()

    @Transactional
    fun updateMyProfile(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean = false,
        request: UpdateMyProfileRequest,
    ): MemberResponse {
        val member = resolveAndLinkMember(keycloakSubject, email, emailVerified)
        request.phoneNumber?.let { member.phoneNumber = it }
        request.profileImageUrl?.let { member.profileImageUrl = it }
        request.street?.let { member.street = it }
        request.houseNumber?.let { member.houseNumber = it }
        request.zipCode?.let { member.zipCode = it }
        request.city?.let { member.city = it }
        return memberRepository.save(member).toResponse()
    }

    // ─── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createMember(request: CreateMemberRequest): MemberDto {
        request.email?.let { email ->
            if (memberRepository.findByEmailAndDeletedAtIsNull(email) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.")
            }
        }
        val member = request.toEntity()
        request.groupPublicId?.let { gpid ->
            member.group =
                churchGroupRepository
                    .findByPublicIdAndDeletedAtIsNull(UUID.fromString(gpid))
                    .orElseThrow { EntityNotFoundException("Group not found: $gpid") }
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

        request.email?.let { email ->
            val existing = memberRepository.findByEmailAndDeletedAtIsNull(email)
            if (existing != null && existing.id != member.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.")
            }
        }
        member.applyPatch(request)

        request.groupPublicId?.let { gpid ->
            member.group =
                if (gpid.isBlank()) {
                    // Explicit blank string clears the group assignment.
                    null
                } else {
                    val groupPublicId = UUID.fromString(gpid)
                    if (member.group?.publicId == groupPublicId) {
                        member.group
                    } else {
                        churchGroupRepository
                            .findByPublicIdAndDeletedAtIsNull(groupPublicId)
                            .orElseThrow { EntityNotFoundException("Group not found: $gpid") }
                    }
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
        member.deleteEntryAt = Instant.now().plusSeconds(memberRetentionDays * 24 * 60 * 60)
        memberRepository.save(member)
    }

    /**
     * Self-registration: creates DB record + Keycloak user, stores keycloakId on member.
     */
    @Transactional
    fun registerMember(req: RegisterMemberRequest): Member {
        log.info(
            "Register member started realm={} firstNameLength={} lastNameLength={} cityPresent={} zipCodePresent={}",
            realm,
            req.firstName.length,
            req.lastName.length,
            req.city?.isNotBlank() == true,
            req.zipCode?.isNotBlank() == true,
        )
        if (memberRepository.findByEmailAndDeletedAtIsNull(req.email) != null) {
            log.warn("Register member rejected duplicate email")
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.")
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
                houseNumber = req.houseNumber,
                zipCode = req.zipCode,
                registrationDate = LocalDate.now(),
                memberStatus = MemberStatus.PENDING,
            )

        val savedMember = memberRepository.save(newMember)
        log.info(
            "Register member saved pending DB record memberId={} discriminatorAssigned={}",
            savedMember.id,
            discriminator != null,
        )

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

        log.info("Creating Keycloak user realm={}", realm)
        val kcResponse =
            try {
                keycloak.realm(realm).users().create(keycloakUser)
            } catch (e: ProcessingException) {
                log.error(
                    "Keycloak user creation transport failure realm={} exceptionType={} message={}",
                    realm,
                    e::class.qualifiedName,
                    e.message?.redactEmail(),
                )
                throw RuntimeException("Keycloak user creation transport failure", e)
            } catch (e: RuntimeException) {
                log.error(
                    "Keycloak user creation runtime failure realm={} exceptionType={} message={}",
                    realm,
                    e::class.qualifiedName,
                    e.message?.redactEmail(),
                )
                throw e
            }
        log.info("Keycloak user creation response realm={} status={}", realm, kcResponse.status)
        if (kcResponse.status != 201) {
            val body = kcResponse.readEntity(String::class.java).orEmpty()
            log.error(
                "Keycloak user creation rejected realm={} status={} body={}",
                realm,
                kcResponse.status,
                body.redactEmail().take(500),
            )
            throw RuntimeException("Keycloak user creation failed (HTTP ${kcResponse.status})")
        }

        val keycloakId =
            kcResponse.location
                ?.path
                ?.substringAfterLast("/")
                .orEmpty()
        if (keycloakId.isNotBlank()) {
            savedMember.keycloakId = keycloakId
            memberRepository.save(savedMember)
            log.info("Register member linked Keycloak user memberId={}", savedMember.id)
        } else {
            log.warn("Keycloak user created without location header memberId={}", savedMember.id)
        }

        log.info("Register member completed memberId={}", savedMember.id)
        return savedMember
    }

    private fun String.redactEmail(): String = replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<redacted-email>")

    private fun resolveAndLinkMember(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean,
    ): Member {
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)?.let { return it }

        if (!emailVerified || email.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found.")
        }

        val legacyMember =
            memberRepository.findByEmailAndDeletedAtIsNull(email)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found.")
        if (legacyMember.keycloakId != null && legacyMember.keycloakId != keycloakSubject) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Member profile is linked to another identity.")
        }

        legacyMember.keycloakId = keycloakSubject
        return memberRepository.save(legacyMember)
    }
}
