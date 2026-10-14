package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.api.v1.dto.NextGroupLeaderResponse
import com.hanmaum.dn.app.features.groups.api.v1.dto.TrainingMatrixMemberDto
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class TrainingMatrixService(
    private val memberService: MemberService,
    private val memberRepository: MemberRepository,
) {
    /** Active, non-deleted members in the established name order, with the existing page enrichment. */
    @Transactional(readOnly = true)
    fun getMembers(
        page: Int,
        size: Int,
    ): Page<TrainingMatrixMemberDto> {
        if (page < 0 || size !in 1..10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid matrix pagination")
        }
        return memberService
            .getMembers(
                search = null,
                status = MemberStatus.ACTIVE,
                baptism = null,
                groupPublicId = null,
                unassigned = null,
                trainingCode = null,
                ministryPublicId = null,
                sort = null,
                page = page,
                size = size,
            ).map(TrainingMatrixMemberDto::from)
    }

    /** Changes only 예비순장, without member editing, group assignment or leader-tenure side effects. */
    @Transactional
    fun setNextGroupLeader(
        publicId: UUID,
        value: Boolean,
    ): NextGroupLeaderResponse {
        val member =
            memberRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Active matrix member not found: $publicId") }
        if (member.memberStatus != MemberStatus.ACTIVE) {
            throw EntityNotFoundException("Active matrix member not found: $publicId")
        }
        member.isNextGroupLeader = value
        val saved = memberRepository.save(member)
        return NextGroupLeaderResponse(saved.publicId.toString(), saved.isNextGroupLeader)
    }
}
