package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

// ─── Response DTOs ────────────────────────────────────────────────────────────

/**
 * Full member detail. publicId (UUID string) is the external identifier.
 * Internal Long `id` is NEVER exposed in any response.
 */
data class MemberDto(
    val publicId: String,
    val lastName: String,
    val firstName: String,
    val discriminator: String? = null,
    val gender: String? = null,
    val baptism: String? = null,
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    val memberStatus: String,
    val churchRole: String? = null,
    val groupName: String? = null,
    val profileImageUrl: String? = null,
)

/** Lightweight DTO used in the paginated list endpoint. */
data class MemberSummaryDto(
    val publicId: String,
    val lastName: String,
    val firstName: String,
    val email: String? = null,
    val memberStatus: String,
    val groupName: String? = null,
)

/**
 * Own-profile response — used by GET /me.
 * publicId only — internal id is never returned.
 */
data class MemberResponse(
    val publicId: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val status: MemberStatus,
    val churchRole: String? = null,
    val groupName: String? = null,
    val city: String? = null,
    val phoneNumber: String? = null,
    val profileImageUrl: String? = null,
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateMemberRequest(
    @field:NotBlank(message = "성은 필수입니다.")
    val lastName: String,
    @field:NotBlank(message = "이름은 필수입니다.")
    val firstName: String,
    val discriminator: String? = null,
    val gender: String? = null,
    val baptism: String? = null,
    val birthDate: LocalDate? = null,
    @field:Size(max = 50)
    val phoneNumber: String? = null,
    @field:Email
    val email: String? = null,
    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    /** Church position/title (직분), not the app access role. */
    val churchRole: String? = null,
    /** Internal group id — used server-side only, never returned. */
    val groupId: Long? = null,
    val profileImageUrl: String? = null,
)

/**
 * PATCH semantics — every field is optional. Only non-null fields are applied.
 * Status transition rule: ACTIVE ↔ INACTIVE only. DELETED is terminal (use DELETE endpoint).
 */
data class UpdateMemberRequest(
    val lastName: String? = null,
    val firstName: String? = null,
    val discriminator: String? = null,
    val gender: String? = null,
    val baptism: String? = null,
    val birthDate: LocalDate? = null,
    @field:Size(max = 50)
    val phoneNumber: String? = null,
    @field:Email
    val email: String? = null,
    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    val memberStatus: String? = null,
    val churchRole: String? = null,
    val groupId: Long? = null,
    val profileImageUrl: String? = null,
)

data class RegisterMemberRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    val firstName: String,
    @field:NotBlank(message = "성은 필수입니다.")
    val lastName: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    @field:Email(message = "유효한 이메일이어야 합니다.")
    val email: String,
    val city: String? = null,
    val baptism: String? = null,
    val gender: String? = null,
    val birthDate: LocalDate? = null,
    @field:Size(max = 50)
    val phoneNumber: String? = null,
    val street: String? = null,
    val zipCode: String? = null,
)
