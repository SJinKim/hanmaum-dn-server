package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.applyPatch
import com.hanmaum.dn.app.features.ministry.api.toActiveMemberDto
import com.hanmaum.dn.app.features.ministry.api.toDto
import com.hanmaum.dn.app.features.ministry.api.toEntity
import com.hanmaum.dn.app.features.ministry.api.toSummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.AddMinistryMemberRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class MinistryService(
    private val ministryRepository: MinistryRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val memberRepository: MemberRepository,
    private val clock: Clock,
) {
    // ─── Ministry CRUD ─────────────────────────────────────────────────────────

    /**
     * Create a new ministry.
     * @throws ResponseStatusException 409 if name already taken
     * @throws ResponseStatusException 400 if a schedule has an invalid time range
     */
    @Transactional
    fun createMinistry(req: CreateMinistryRequest): MinistryDto {
        if (ministryRepository.existsByNameAndDeletedAtIsNull(req.title)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "사역 제목이 이미 사용 중입니다: ${req.title}")
        }
        validateScheduleTimes(req.schedules)
        val ministry = ministryRepository.save(req.toEntity())
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
     * @throws EntityNotFoundException if ministry not found
     * @throws ResponseStatusException 400 if a schedule has an invalid time range
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

        req.schedules?.let(::validateScheduleTimes)
        ministry.applyPatch(req)
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

    /**
     * Binds an existing member to a ministry (the "맴버 추가" action on the ministry detail page).
     * Authorized for ADMIN and MINISTRY_LEADER at the controller; no ownership check — any
     * ministry-leader may add to any ministry. Creates a [MinistryAssignment] starting on the
     * first of the current month.
     *
     * @throws EntityNotFoundException if the ministry or member is missing/soft-deleted
     * @throws ResponseStatusException 409 if the member is already active in this ministry
     */
    @Transactional
    fun addMember(
        ministryPublicId: UUID,
        req: AddMinistryMemberRequest,
    ): ActiveMinistryMemberDto {
        val ministry =
            ministryRepository
                .findByPublicIdAndDeletedAtIsNull(ministryPublicId)
                .orElseThrow { EntityNotFoundException("Ministry not found: $ministryPublicId") }
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(req.memberId)
                .orElseThrow { EntityNotFoundException("Member not found: ${req.memberId}") }
        if (ministryAssignmentRepository.existsActiveAssignment(ministry.id!!, member.id!!)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이 맴버는 이미 활동중입니다.")
        }
        val assignment =
            MinistryAssignment(
                ministry = ministry,
                member = member,
                startDate = (req.startDate ?: LocalDate.now(clock)).withDayOfMonth(1),
                note = req.note,
            )
        return ministryAssignmentRepository.save(assignment).toActiveMemberDto()
    }

    private fun validateScheduleTimes(schedules: List<MinistryScheduleRequest>) {
        schedules.forEachIndexed { index, schedule ->
            if (!schedule.endTime.isAfter(schedule.startTime)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사역 일정 ${index + 1}의 종료 시간은 시작 시간보다 늦어야 합니다.",
                )
            }
        }
    }
}
