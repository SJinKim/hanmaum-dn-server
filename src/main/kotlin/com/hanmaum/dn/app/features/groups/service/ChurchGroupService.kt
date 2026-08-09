package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.api.v1.dto.AssignGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupLeader
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupLeaderRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

@Service
class ChurchGroupService(
    private val churchGroupRepository: ChurchGroupRepository,
    private val groupLeaderRepository: GroupLeaderRepository,
    private val memberRepository: MemberRepository,
) {
    private val log = LoggerFactory.getLogger(ChurchGroupService::class.java)

    /** All non-deleted groups, ordered by division then name — used to populate selection lists. */
    @Transactional(readOnly = true)
    fun getGroups(): List<ChurchGroupSummaryDto> {
        val groups = churchGroupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc()

        // One batched lookup for the whole list rather than a query per group (no N+1).
        val groupIds = groups.mapNotNull { it.id }
        val leaderByGroupId =
            if (groupIds.isEmpty()) {
                emptyMap()
            } else {
                groupLeaderRepository
                    .findActiveByGroupIds(groupIds)
                    .associateBy { it.group.id }
            }

        return groups.map { it.toSummaryDto(it.id?.let(leaderByGroupId::get)) }
    }

    /**
     * Makes the requested member the group's current leader, closing the sitting leader's tenure.
     *
     * Idempotent: re-sending the member who already leads the group returns the current state
     * untouched. Without that short-circuit a repeated save (or a client retry) would close a
     * live tenure and open an identical one the same day, splitting one continuous term across
     * several rows and resetting its start date.
     *
     * @throws EntityNotFoundException if the group or member does not exist
     * @throws ResponseStatusException 400 if the member does not belong to the group,
     *   or if [AssignGroupLeaderRequest.startDate] precedes the sitting leader's start
     */
    @Transactional
    fun assignLeader(
        groupPublicId: UUID,
        request: AssignGroupLeaderRequest,
    ): ChurchGroupSummaryDto {
        val group =
            churchGroupRepository
                .findByPublicIdAndDeletedAtIsNull(groupPublicId)
                .orElseThrow { EntityNotFoundException("Church group not found: $groupPublicId") }
        val memberPublicId =
            runCatching { UUID.fromString(request.memberPublicId) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 맴버 ID입니다.") }
        val member =
            memberRepository
                .findByPublicIdAndDeletedAtIsNull(memberPublicId)
                .orElseThrow { EntityNotFoundException("Member not found: $memberPublicId") }

        // A group leader is by definition part of the group they lead.
        if (member.group?.id != group.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "그룹 리더는 해당 그룹의 맴버여야 합니다.")
        }

        val startDate = request.startDate ?: LocalDate.now()
        val current = group.id?.let(groupLeaderRepository::findActiveByGroupId)

        if (current != null && current.member.id == member.id) {
            log.info("Group leader unchanged groupId={} memberId={}", group.id, member.id)
            return group.toSummaryDto(current)
        }

        if (current != null) {
            // Backdating below the sitting leader's start would produce endDate < startDate.
            if (startDate.isBefore(current.startDate)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시작일은 현재 리더의 시작일 이후여야 합니다.",
                )
            }
            current.endDate = startDate
            // Close the sitting tenure before opening the new one: both rows are momentarily
            // active otherwise, which the partial unique index rejects.
            groupLeaderRepository.saveAndFlush(current)
        }

        val assigned =
            groupLeaderRepository.save(
                GroupLeader(group = group, member = member, startDate = startDate),
            )
        log.info(
            "Assigned group leader groupId={} memberId={} previousMemberId={}",
            group.id,
            member.id,
            current?.member?.id,
        )
        return group.toSummaryDto(assigned)
    }

    private fun ChurchGroup.toSummaryDto(leader: GroupLeader?): ChurchGroupSummaryDto =
        ChurchGroupSummaryDto(
            publicId = this.publicId.toString(),
            division = this.division,
            name = this.name,
            leaderPublicId = leader?.member?.publicId?.toString(),
            leaderName = leader?.member?.getFullName(),
            leaderSince = leader?.startDate,
        )
}
