package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.applyPatch
import com.hanmaum.dn.app.features.ministry.api.toDto
import com.hanmaum.dn.app.features.ministry.api.toEntity
import com.hanmaum.dn.app.features.ministry.api.toSummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MinistryService(
    private val ministryRepository: MinistryRepository,
    private val memberRepository: MemberRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
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

    /**
     * Returns all members currently active in a ministry (endDate IS NULL).
     * Soft-deleted members are excluded via the repository query filter.
     *
     * @throws EntityNotFoundException if ministry not found or soft-deleted
     */
    @Transactional(readOnly = true)
    fun getActiveMembers(publicId: UUID): List<ActiveMinistryMemberDto> {
        ministryRepository
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { EntityNotFoundException("Ministry not found: $publicId") }
        return ministryAssignmentRepository
            .findActiveByMinistryPublicId(publicId)
            .map { it.toDto() }
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
