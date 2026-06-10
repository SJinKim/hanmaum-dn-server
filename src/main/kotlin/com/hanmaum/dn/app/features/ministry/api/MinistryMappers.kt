package com.hanmaum.dn.app.features.ministry.api

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.LeaderDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.repository.ActiveMemberView

// ─── Entity creation ──────────────────────────────────────────────────────────

fun CreateMinistryRequest.toEntity(leader: Member?): Ministry =
    Ministry(
        name = this.name,
        shortDescription = this.shortDescription,
        longDescription = this.longDescription,
        leader = leader,
        imageUrl = this.imageUrl,
        isMinistryActive = true,
    )

// ─── PATCH update ─────────────────────────────────────────────────────────────

/**
 * Applies only non-null fields (PATCH semantics).
 * [resolveLeader] is a lambda that resolves leaderPublicId string → Member? entity.
 * Pass null for [resolveLeader] when leaderPublicId is not present in the request.
 */
fun Ministry.applyPatch(
    request: UpdateMinistryRequest,
    resolveLeader: ((String) -> Member?)?,
) {
    request.name?.let { this.name = it }
    request.shortDescription?.let { this.shortDescription = it }
    request.longDescription?.let { this.longDescription = it }
    request.imageUrl?.let { this.imageUrl = it }
    request.isActive?.let { this.isMinistryActive = it }

    request.leaderPublicId?.let { leaderPublicIdStr ->
        this.leader =
            if (leaderPublicIdStr.isBlank()) {
                null // empty string explicitly removes the leader
            } else {
                resolveLeader?.invoke(leaderPublicIdStr)
            }
    }
}

// ─── Entity → DTO mappings ────────────────────────────────────────────────────

fun Ministry.toSummaryDto(): MinistrySummaryDto =
    MinistrySummaryDto(
        publicId = this.publicId.toString(),
        name = this.name,
        shortDescription = this.shortDescription,
        imageUrl = this.imageUrl,
        leaderName = this.leader?.let { "${it.lastName}${it.firstName}" },
        isActive = this.isMinistryActive,
    )

fun Ministry.toDto(): MinistryDto =
    MinistryDto(
        publicId = this.publicId.toString(),
        name = this.name,
        shortDescription = this.shortDescription,
        longDescription = this.longDescription,
        imageUrl = this.imageUrl,
        leader = this.leader?.toLeaderDto(),
        isActive = this.isMinistryActive,
    )

fun Member.toLeaderDto(): LeaderDto =
    LeaderDto(
        publicId = this.publicId.toString(),
        fullName = "${this.lastName}${this.firstName}",
    )

fun ActiveMemberView.toDto(): ActiveMinistryMemberDto =
    ActiveMinistryMemberDto(
        publicId = this.memberPublicId.toString(),
        fullName = this.fullName,
        startDate = this.startDate.toString(),
        note = this.note,
    )
