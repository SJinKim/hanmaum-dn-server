package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.ExternalCallOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupLeaderRepository
import com.hanmaum.dn.app.features.members.api.applyPatch
import com.hanmaum.dn.app.features.members.api.toDto
import com.hanmaum.dn.app.features.members.api.toEntity
import com.hanmaum.dn.app.features.members.api.toNameDto
import com.hanmaum.dn.app.features.members.api.toResponse
import com.hanmaum.dn.app.features.members.api.toSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberNameDto
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
import com.hanmaum.dn.app.features.members.repository.MemberGraduationRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.WebApplicationException
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
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
    private val groupLeaderRepository: GroupLeaderRepository,
    private val memberGraduationRepository: MemberGraduationRepository,
    private val userTrainingRepository: UserTrainingRepository,
    private val trainingRepository: TrainingRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val ministryRepository: MinistryRepository,
    private val keycloak: Keycloak,
    private val currentMemberResolver: CurrentMemberResolver,
    private val operationalMetrics: OperationalMetrics,
    @Value("\${app.keycloak.realm:hanmaum}") private val realm: String,
    @Value("\${app.member-retention.days:30}") private val memberRetentionDays: Long = 30,
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)
    private val sortPropertyAliases =
        mapOf(
            "lastName" to "lastName",
            "memberStatus" to "memberStatus",
            "groupName" to "groupName",
            "baptism" to "baptism",
            "updatedAt" to "updatedAt",
            "latestTraining" to "latestTraining",
            "training" to "latestTraining",
            "activeMinistries" to "activeMinistries",
            "ministry" to "activeMinistries",
        )

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
        groupPublicId: UUID?,
        unassigned: Boolean?,
        trainingCode: String?,
        ministryPublicId: UUID?,
        sort: List<String>?,
        page: Int,
        size: Int,
    ): Page<MemberSummaryDto> {
        if (groupPublicId != null && unassigned == true) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "groupPublicId and unassigned=true cannot be combined")
        }
        val pageable = PageRequest.of(page, size, parseSort(sort))
        val requestedTrainingCode = parseTrainingCode(trainingCode)
        val trainingMemberIds =
            requestedTrainingCode
                ?.let(userTrainingRepository::findMemberIdsByTrainingCode)
                ?.toSet()
        val ministryMemberIds =
            ministryPublicId
                ?.let(ministryAssignmentRepository::findActiveMemberIdsByMinistryPublicId)
                ?.toSet()
        val members =
            memberRepository
                .findActiveMembers(search?.takeIf { it.isNotBlank() }.orEmpty(), status, baptism)
                .asSequence()
                .filter { groupPublicId == null || it.group?.publicId == groupPublicId }
                .filter { unassigned != true || it.group == null }
                .filter { trainingMemberIds == null || it.id?.let(trainingMemberIds::contains) == true }
                .filter { ministryMemberIds == null || it.id?.let(ministryMemberIds::contains) == true }
                .toList()

        // Enrich the filtered collection in batch queries (no N+1):
        //  - all trainings (with status) per member = grid chips, ordered by progression
        //  - latest completed training               = highest sort_order among COMPLETED chips
        //  - active ministries                       = ministry names where end_date IS NULL
        //  - current group leadership                = tenure start where end_date IS NULL
        val memberIds = members.mapNotNull { it.id }
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
                            .map {
                                SummaryTrainingDto(
                                    code = it.trainingCode.name,
                                    name = it.trainingName,
                                    status = it.status.name,
                                )
                            }
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
        val leaderSinceByMember: Map<Long, LocalDate> =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                groupLeaderRepository
                    .findActiveByMemberIds(memberIds)
                    .associate { it.memberId to it.startDate }
            }
        val graduatedOnByMember: Map<Long, LocalDate> =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                memberGraduationRepository
                    .findOpenByMemberIds(memberIds)
                    .associate { it.memberId to it.graduatedOn }
            }

        val summaries =
            members.map {
                it.toSummaryDto(
                    latestTraining = it.id?.let(latestTrainingByMember::get),
                    trainings = it.id?.let(trainingsByMember::get).orEmpty(),
                    activeMinistries = it.id?.let(activeMinistriesByMember::get).orEmpty(),
                    groupLeaderSince = it.id?.let(leaderSinceByMember::get),
                    graduatedOn = it.id?.let(graduatedOnByMember::get),
                )
            }
        return PageImpl(sortSummaries(summaries, pageable.sort), pageable, summaries.size.toLong())
    }

    private fun parseTrainingCode(trainingCode: String?): TrainingCode? {
        val code = trainingCode?.takeIf(String::isNotBlank)?.uppercase() ?: return null
        return try {
            TrainingCode.valueOf(code)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown trainingCode: $trainingCode")
        }
    }

    private fun parseSort(sort: List<String>?): Sort {
        val orders =
            sort
                .orEmpty()
                .filter(String::isNotBlank)
                .map(::parseSortOrder)
        return if (orders.isEmpty()) Sort.unsorted() else Sort.by(orders)
    }

    private fun parseSortOrder(value: String): Sort.Order {
        val parts = value.split(',').map(String::trim)
        if (parts.size !in 1..2 || parts.first().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort: $value")
        }
        val property = sortPropertyAliases[parts.first()]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort property: ${parts.first()}")
        val direction =
            if (parts.size == 1) {
                Sort.Direction.ASC
            } else {
                Sort.Direction.fromOptionalString(parts[1])
                    .orElse(null)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort direction: ${parts[1]}")
            }
        return Sort.Order(direction, property)
    }

    private fun sortSummaries(
        summaries: List<MemberSummaryDto>,
        sort: Sort,
    ): List<MemberSummaryDto> {
        val orders =
            if (sort.isSorted) {
                sort.toList()
            } else {
                listOf(Sort.Order.asc("lastName"), Sort.Order.asc("firstName"))
            }
        val comparator = orders.fold(Comparator<MemberSummaryDto> { _, _ -> 0 }) { result, order ->
            result.thenComparing(summaryComparator(order))
        }
        return summaries.sortedWith(comparator.thenComparing(compareBy { it.publicId }))
    }

    private fun summaryComparator(order: Sort.Order): Comparator<MemberSummaryDto> {
        val comparator =
            when (order.property) {
                "lastName" -> stringComparator { it.lastName }
                "firstName" -> stringComparator { it.firstName }
                "memberStatus" -> stringComparator { it.memberStatus }
                "groupName" -> stringComparator { it.groupName }
                "baptism" -> stringComparator { it.baptism }
                "updatedAt" -> Comparator { first, second -> compareNullable(first.updatedAt, second.updatedAt) }
                "latestTraining" -> stringComparator { it.latestTraining }
                "activeMinistries" -> stringComparator { it.activeMinistries.firstOrNull() }
                else -> error("Unsupported sort property was not rejected: ${order.property}")
            }
        return if (order.direction == Sort.Direction.DESC) comparator.reversed() else comparator
    }

    private fun stringComparator(
        selector: (MemberSummaryDto) -> String?,
    ): Comparator<MemberSummaryDto> = Comparator { first, second ->
        compareNullable(selector(first)?.lowercase(), selector(second)?.lowercase())
    }

    private fun <T : Comparable<T>> compareNullable(
        first: T?,
        second: T?,
    ): Int =
        when {
            first == null && second == null -> 0
            first == null -> 1
            second == null -> -1
            else -> first.compareTo(second)
        }

    /**
     * All non-deleted members as minimal name entries (publicId, fullName, discriminator),
     * sorted by display name. Backs the ministry "맴버 추가" picker; intentionally carries no
     * PII beyond the name so it can be exposed to MINISTRY_LEADER as well as ADMIN.
     */
    @Transactional(readOnly = true)
    fun getMemberNames(): List<MemberNameDto> =
        memberRepository
            .findAllByDeletedAtIsNull()
            .map { it.toNameDto() }
            .sortedBy { it.fullName }

    @Transactional(readOnly = true)
    fun getMemberByPublicId(publicId: UUID): MemberDto {
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        val memberId = member.id!!
        val trainings = userTrainingRepository.findByMemberId(memberId).map { it.toDto() }
        val ministries = ministryAssignmentRepository.findByMemberId(memberId).map { it.toHistoryDto() }
        return member.toDto(trainings, ministries, groupLeaderSince(memberId), graduatedOn(memberId))
    }

    /**
     * Start of the member's current group-leader tenure, or null when they lead no group.
     * Reuses the batched query with a single id — one lookup either way.
     */
    private fun groupLeaderSince(memberId: Long): LocalDate? =
        groupLeaderRepository
            .findActiveByMemberIds(listOf(memberId))
            .firstOrNull()
            ?.startDate

    /**
     * Day the member left the community, or null. Reuses the batched query with a single
     * id — one lookup either way, and one place where "graduated" is decided.
     */
    private fun graduatedOn(memberId: Long): LocalDate? =
        memberGraduationRepository
            .findOpenByMemberIds(listOf(memberId))
            .firstOrNull()
            ?.graduatedOn

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
        return member.toDto(trainings, ministries, groupLeaderSince(memberId), graduatedOn(memberId))
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
        return member.toDto(trainings, ministries, groupLeaderSince(memberId), graduatedOn(memberId))
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
        firstName: String? = null,
        lastName: String? = null,
        birthDate: LocalDate? = null,
    ): MemberResponse {
        val member = resolveAndLinkMember(keycloakSubject, email, emailVerified, firstName, lastName, birthDate)
        return member.toResponse(activeMinistryNames(member.id), emailVerified)
    }

    /**
     * Ministry names the member currently serves in, sorted, for the own-profile response.
     *
     * Same source and same "active" definition as the admin grid in [listMembers]
     * (`end_date IS NULL`), reusing its batch query with a single id — one member is one
     * row set, so there is nothing to batch here.
     */
    private fun activeMinistryNames(memberId: Long?): List<String> =
        memberId
            ?.let { id ->
                ministryAssignmentRepository
                    .findActiveByMemberIds(listOf(id))
                    .map { it.ministryName }
                    .sorted()
            }.orEmpty()

    /**
     * Resolve the calling member from JWT claims for the notifications feature.
     */
    @Transactional
    fun resolveMember(
        keycloakSubject: String,
        email: String?,
    ): Member = resolveAndLinkMember(keycloakSubject, email, false)

    @Transactional
    fun updateMyProfile(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean = false,
        firstName: String? = null,
        lastName: String? = null,
        birthDateClaim: LocalDate? = null,
        request: UpdateMyProfileRequest,
    ): MemberResponse {
        val member = resolveAndLinkMember(keycloakSubject, email, emailVerified, firstName, lastName, birthDateClaim)
        request.phoneNumber?.let { member.phoneNumber = it }
        request.birthDate?.let { member.birthDate = it }
        request.profileImageUrl?.let { member.profileImageUrl = it }
        request.street?.let { member.street = it }
        request.houseNumber?.let { member.houseNumber = it }
        request.zipCode?.let { member.zipCode = it }
        request.city?.let { member.city = it }
        val saved = memberRepository.save(member)
        return saved.toResponse(activeMinistryNames(saved.id), emailVerified)
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

        val previousGroupId = member.group?.id
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
        endLeadershipIfMovedOutOfGroup(member, previousGroupId)

        return memberRepository.save(member).toDto()
    }

    /**
     * Closes an active group-leader tenure when the member is moved to another group or
     * ungrouped: a leader is by definition part of the group they lead, so leaving the group
     * ends the tenure. Without this, the member would keep leading a group they are no longer in.
     */
    private fun endLeadershipIfMovedOutOfGroup(
        member: Member,
        previousGroupId: Long?,
    ) {
        if (previousGroupId == null || previousGroupId == member.group?.id) return
        val leadership = groupLeaderRepository.findActiveByGroupId(previousGroupId) ?: return
        if (leadership.member.id != member.id) return

        leadership.endDate = LocalDate.now()
        groupLeaderRepository.save(leadership)
        log.info(
            "Ended group leadership after group change memberId={} groupId={}",
            member.id,
            previousGroupId,
        )
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
        // A dashboard/newcomer record with this email is not an error. The account is
        // created as a separate pending registration and is linked only after Keycloak has
        // verified the email and the identity checks run on first authenticated access.
        val existingUnclaimedMember =
            memberRepository.findByEmailAndDeletedAtIsNull(req.email)?.takeIf { it.keycloakId == null }

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
                // The active-email hash is unique. Keep the verified address on the
                // pre-existing member; the JWT supplies it during first-login claiming.
                email = if (existingUnclaimedMember == null) req.email else null,
                gender =
                    try {
                        req.gender?.let { Gender.valueOf(it.uppercase()) }
                    } catch (e: IllegalArgumentException) {
                        null
                    },
                baptism =
                    try {
                        req.baptism?.let { Baptism.valueOf(it.uppercase()) }
                    } catch (e: IllegalArgumentException) {
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

        val keycloakCallStartedAt = System.nanoTime()
        val kcResponse =
            try {
                keycloak.realm(realm).users().create(keycloakUser)
            } catch (e: ProcessingException) {
                recordKeycloakCall(ExternalCallOutcome.TRANSPORT_ERROR, keycloakCallStartedAt)
                log
                    .atError()
                    .setCause(e)
                    .addKeyValue("event.action", "keycloak.user.create")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("dependency.name", "keycloak")
                    .addKeyValue("error.type", "transport")
                    .addKeyValue("realm", realm)
                    .log("Keycloak user creation failed")
                throw RuntimeException("Keycloak user creation transport failure", e)
            } catch (e: RuntimeException) {
                recordKeycloakCall(ExternalCallOutcome.UNEXPECTED_ERROR, keycloakCallStartedAt)
                log
                    .atError()
                    .setCause(e)
                    .addKeyValue("event.action", "keycloak.user.create")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("dependency.name", "keycloak")
                    .addKeyValue("error.type", "unexpected")
                    .addKeyValue("realm", realm)
                    .log("Keycloak user creation failed")
                throw e
            }
        val keycloakId =
            kcResponse.use { response ->
                if (response.status != 201) {
                    val outcome = keycloakHttpOutcome(response.status)
                    recordKeycloakCall(outcome, keycloakCallStartedAt)
                    log
                        .atError()
                        .addKeyValue("event.action", "keycloak.user.create")
                        .addKeyValue("event.outcome", "failure")
                        .addKeyValue("dependency.name", "keycloak")
                        .addKeyValue("http.response.status_code", response.status)
                        .addKeyValue("realm", realm)
                        .log("Keycloak user creation was rejected")
                    throw RuntimeException("Keycloak user creation failed (HTTP ${response.status})")
                }

                response.location
                    ?.path
                    ?.substringAfterLast("/")
                    .orEmpty()
            }
        if (keycloakId.isNotBlank()) {
            recordKeycloakCall(ExternalCallOutcome.SUCCESS, keycloakCallStartedAt)
            savedMember.keycloakId = keycloakId
            memberRepository.save(savedMember)
            sendVerificationEmail(keycloakId)
        } else {
            recordKeycloakCall(ExternalCallOutcome.INVALID_RESPONSE, keycloakCallStartedAt)
            log
                .atError()
                .addKeyValue("event.action", "keycloak.user.create")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("dependency.name", "keycloak")
                .addKeyValue("error.type", "missing_location")
                .addKeyValue("realm", realm)
                .log("Keycloak user creation returned an invalid response")
        }

        log
            .atInfo()
            .addKeyValue("event.action", "member.register")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("keycloak.linked", keycloakId.isNotBlank())
            .log("Member registration completed")
        return savedMember
    }

    private fun recordKeycloakCall(
        outcome: ExternalCallOutcome,
        startedAt: Long,
        operation: String = "create_user",
    ) {
        operationalMetrics.recordExternalCall(
            dependency = "keycloak",
            operation = operation,
            outcome = outcome,
            elapsedNanos = System.nanoTime() - startedAt,
        )
    }

    /**
     * Verification is advisory and runs alongside manual approval. A mail outage must not
     * roll back the member and Keycloak user that were already created successfully.
     */
    private fun sendVerificationEmail(keycloakId: String) {
        val startedAt = System.nanoTime()
        try {
            keycloak
                .realm(realm)
                .users()
                .get(keycloakId)
                .sendVerifyEmail()
            recordKeycloakCall(ExternalCallOutcome.SUCCESS, startedAt, "send_verify_email")
        } catch (e: ProcessingException) {
            recordKeycloakCall(ExternalCallOutcome.TRANSPORT_ERROR, startedAt, "send_verify_email")
            log
                .atWarn()
                .setCause(e)
                .addKeyValue("event.action", "keycloak.user.send_verify_email")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("dependency.name", "keycloak")
                .addKeyValue("error.type", "transport")
                .addKeyValue("realm", realm)
                .log("Keycloak verification email could not be sent")
        } catch (e: WebApplicationException) {
            val status = e.response.status
            recordKeycloakCall(keycloakHttpOutcome(status), startedAt, "send_verify_email")
            log
                .atWarn()
                .setCause(e)
                .addKeyValue("event.action", "keycloak.user.send_verify_email")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("dependency.name", "keycloak")
                .addKeyValue("error.type", "http")
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("realm", realm)
                .log("Keycloak verification email could not be sent")
        } catch (e: RuntimeException) {
            recordKeycloakCall(ExternalCallOutcome.UNEXPECTED_ERROR, startedAt, "send_verify_email")
            log
                .atWarn()
                .setCause(e)
                .addKeyValue("event.action", "keycloak.user.send_verify_email")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("dependency.name", "keycloak")
                .addKeyValue("error.type", "unexpected")
                .addKeyValue("realm", realm)
                .log("Keycloak verification email could not be sent")
        }
    }

    private fun keycloakHttpOutcome(status: Int): ExternalCallOutcome =
        when (status) {
            in 400..499 -> ExternalCallOutcome.CLIENT_ERROR
            in 500..599 -> ExternalCallOutcome.SERVER_ERROR
            else -> ExternalCallOutcome.INVALID_RESPONSE
        }

    // Delegates rather than reimplements: this used to be the only self-healing resolution
    // in the codebase, which is exactly why the other readers behaved differently.
    private fun resolveAndLinkMember(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean,
        firstName: String? = null,
        lastName: String? = null,
        birthDate: LocalDate? = null,
    ): Member = currentMemberResolver.resolveAndLink(keycloakSubject, email, emailVerified, firstName, lastName, birthDate)
}
