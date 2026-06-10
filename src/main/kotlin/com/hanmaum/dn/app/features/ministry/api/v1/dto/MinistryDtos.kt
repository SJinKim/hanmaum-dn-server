package com.hanmaum.dn.app.features.ministry.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ─── Response DTOs ────────────────────────────────────────────────────────────

/** Lightweight DTO for list endpoints. */
data class MinistrySummaryDto(
    val publicId: String,
    val name: String,
    val shortDescription: String,
    val imageUrl: String?,
    val leaderName: String?,
    val isActive: Boolean,
)

/** Full detail DTO returned by GET /{publicId}. */
data class MinistryDto(
    val publicId: String,
    val name: String,
    val shortDescription: String,
    val longDescription: String?,
    val imageUrl: String?,
    val leader: LeaderDto?,
    val isActive: Boolean,
)

/** Nested leader info inside MinistryDto. */
data class LeaderDto(
    val publicId: String,
    val fullName: String,
)

/** One active member in a ministry — returned by GET /{publicId}/members. */
data class ActiveMinistryMemberDto(
    val publicId: String, // member public ID
    val fullName: String,
    val startDate: String, // ISO 'YYYY-MM-DD'
    val note: String?,
    val gender: String?,  // "M" | "F" | null
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateMinistryRequest(
    @field:NotBlank(message = "부서 이름은 필수입니다.")
    @field:Size(max = 100, message = "부서 이름은 최대 100자입니다.")
    val name: String,
    @field:NotBlank(message = "짧은 설명은 필수입니다.")
    @field:Size(max = 200, message = "짧은 설명은 최대 200자입니다.")
    val shortDescription: String,
    val longDescription: String? = null,
    val imageUrl: String? = null,
    /** publicId of the leader Member; null means no leader assigned. */
    val leaderPublicId: String? = null,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateMinistryRequest(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 200)
    val shortDescription: String? = null,
    val longDescription: String? = null,
    val imageUrl: String? = null,
    /** null = keep current; empty string = remove leader */
    val leaderPublicId: String? = null,
    val isActive: Boolean? = null,
)
