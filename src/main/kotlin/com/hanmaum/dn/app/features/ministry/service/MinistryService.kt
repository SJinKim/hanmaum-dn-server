package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.applyPatch
import com.hanmaum.dn.app.features.ministry.api.toDto
import com.hanmaum.dn.app.features.ministry.api.toEntity
import com.hanmaum.dn.app.features.ministry.api.toSummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateRegistrationRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.RegistrationDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateRegistrationStatusRequest
import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import com.hanmaum.dn.app.features.ministry.domain.RegistrationStatus
import com.hanmaum.dn.app.features.ministry.repository.MinistryRegistrationRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class MinistryService(
    private val ministryRepository: MinistryRepository,
    private val ministryRegistrationRepository: MinistryRegistrationRepository,
    private val memberRepository: MemberRepository,
) {
    // ─── Ministry CRUD ─────────────────────────────────────────────────────────

    /**
     * Create a new ministry.
     * [req.leaderPublicId] is optional; if provided, the Member must exist.
     *
     * @throws ResponseStatusException 409 if name already taken
     * @throws EntityNotFoundException if leaderPublicId is provided but not found
     */
    @Transactional
    fun createMinistry(req: CreateMinistryRequest): MinistryDto {
        if (ministryRepository.existsByNameAndDeletedAtIsNull(req.name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "부서 이름이 이미 사용 중입니다: ${req.name}")
        }
        val leader = req.leaderPublicId?.let { resolveLeader(it) }
        val ministry = ministryRepository.save(req.toEntity(leader))
        return ministry.toDto()
    }

    /**
     * List ministries; optionally filter by active state.
     * [active] null → all; true → active only; false → inactive only.
     */
    @Transactional(readOnly = true)
    fun getMinistries(active: Boolean?): List<MinistrySummaryDto> = ministryRepository.findAllActive(active).map { it.toSummaryDto() }

    /**
     * Full detail for a single ministry.
     *
     * @throws EntityNotFoundException if not found or soft-deleted
     */
    @Transactional(readOnly = true)
    fun getMinistry(publicId: UUID): MinistryDto {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $publicId") }
        return ministry.toDto()
    }

    /**
     * Partial update (PATCH semantics).
     *
     * @throws EntityNotFoundException  if ministry not found
     * @throws EntityNotFoundException  if leaderPublicId provided but not found
     */
    @Transactional
    fun updateMinistry(
        publicId: UUID,
        req: UpdateMinistryRequest,
    ): MinistryDto {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $publicId") }

        val leaderResolver: ((String) -> Member?)? =
            if (req.leaderPublicId != null) {
                { id -> if (id.isBlank()) null else resolveLeader(id) }
            } else {
                null
            }

        ministry.applyPatch(req, leaderResolver)
        return ministry.toDto()
    }

    /**
     * Deactivate a ministry (isActive = false).
     * Not a hard delete — sets isMinistryActive=false only. Soft delete (deletedAt) is NOT used here
     * per MVP spec: "Deactivate (isActive=false, NOT hard delete)".
     *
     * @throws EntityNotFoundException if not found
     */
    @Transactional
    fun deactivateMinistry(publicId: UUID) {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $publicId") }
        ministry.isMinistryActive = false
    }

    // ─── Registration ──────────────────────────────────────────────────────────

    @Transactional
    fun registerSelf(
        ministryPublicId: UUID,
        keycloakSubject: String,
        req: CreateRegistrationRequest,
    ): RegistrationDto {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }

        if (!ministry.isMinistryActive) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "비활성 부서에는 신청할 수 없습니다.")
        }

        val member =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

        val existing =
            ministryRegistrationRepository.findByMinistryIdAndMemberIdAndPeriod(
                ministry.id!!,
                member.id!!,
                req.period,
            )

        if (existing.isPresent) {
            val existingReg = existing.get()
            when (existingReg.status) {
                RegistrationStatus.REJECTED -> {
                    // Soft-delete the rejected record so re-apply can proceed
                    existingReg.deletedAt = Instant.now()
                    ministryRegistrationRepository.flush()
                }

                else -> throw ResponseStatusException(HttpStatus.CONFLICT, "이미 해당 기간에 신청되어 있습니다.")
            }
        }

        val registration =
            ministryRegistrationRepository.save(
                MinistryRegistration(
                    ministry = ministry,
                    member = member,
                    registrationPeriod = req.period,
                    note = req.note,
                ),
            )
        return registration.toDto()
    }

    /**
     * List registrations for a ministry. ADMIN only.
     *
     * @throws EntityNotFoundException if ministry not found
     */
    @Transactional(readOnly = true)
    fun getRegistrations(
        ministryPublicId: UUID,
        period: String?,
    ): List<RegistrationDto> {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }
        return ministryRegistrationRepository
            .findByMinistryId(ministry.id!!, period)
            .map { it.toDto() }
    }

    /**
     * Return the calling member's own registration for a ministry in [period].
     * Returns null if no non-deleted record exists.
     *
     * @throws EntityNotFoundException if ministry or member not found
     */
    @Transactional(readOnly = true)
    fun getMyRegistration(
        ministryPublicId: UUID,
        keycloakSubject: String,
        period: String,
    ): RegistrationDto? {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }

        val member =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

        return ministryRegistrationRepository
            .findByMinistryIdAndMemberIdAndPeriod(ministry.id!!, member.id!!, period)
            .map { it.toDto() }
            .orElse(null)
    }

    /**
     * Admin: approve or reject a registration. Only PENDING → APPROVED/REJECTED allowed.
     *
     * @throws EntityNotFoundException  if ministry or registration not found
     * @throws ResponseStatusException  400 if status value is invalid
     * @throws ResponseStatusException  409 if registration is not in PENDING state
     */
    @Transactional
    fun approveOrRejectRegistration(
        ministryPublicId: UUID,
        regPublicId: UUID,
        req: UpdateRegistrationStatusRequest,
    ): RegistrationDto {
        ministryRepository
            .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
            .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }

        val registration =
            ministryRegistrationRepository
                .findByPublicIdAndDeletedAtIsNull(regPublicId)
                .orElseThrow { EntityNotFoundException("Registration not found: $regPublicId") }

        if (registration.status != RegistrationStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "PENDING 상태인 신청만 처리할 수 있습니다.")
        }

        val newStatus =
            try {
                RegistrationStatus.valueOf(req.status)
            } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 상태값: ${req.status}")
            }

        if (newStatus == RegistrationStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PENDING으로 변경할 수 없습니다.")
        }

        registration.status = newStatus
        return registration.toDto()
    }

    /**
     * Withdraw a registration. Member can only withdraw their own.
     *
     * @throws EntityNotFoundException  if ministry or registration not found
     * @throws ResponseStatusException  403 if registration belongs to a different member
     */
    @Transactional
    fun withdrawRegistration(
        ministryPublicId: UUID,
        regPublicId: UUID,
        keycloakSubject: String,
    ) {
        // Verify ministry exists
        ministryRepository
            .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
            .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }

        val registration =
            ministryRegistrationRepository
                .findByPublicIdAndDeletedAtIsNull(regPublicId)
                .orElseThrow { EntityNotFoundException("Registration not found: $regPublicId") }

        val callingMember =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

        if (registration.member.id != callingMember.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 등록만 취소할 수 있습니다.")
        }

        // Soft delete
        registration.deletedAt = Instant.now()
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private fun resolveLeader(leaderPublicId: String): Member {
        val uuid =
            try {
                UUID.fromString(leaderPublicId)
            } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 leaderPublicId 형식입니다.")
            }
        return memberRepository
            .findByPublicIdAndDeletedAtIsNull(uuid)
            .orElseThrow { EntityNotFoundException("Leader member not found: $leaderPublicId") }
    }
}
