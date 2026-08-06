package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.serialization.TrimmingStringConverter
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.databind.annotation.JsonDeserialize
import java.time.Instant
import java.time.LocalDate

// ─── Response DTOs ────────────────────────────────────────────────────────────

/**
 * Full member detail. publicId (UUID string) is the external identifier.
 * Internal Long `id` is NEVER exposed in any response.
 */
@Redacted
data class MemberDto(
    @Unredacted val publicId: String,
    val lastName: String,
    val firstName: String,
    val discriminator: String? = null,
    val gender: String? = null,
    val baptism: String? = null,
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    @Unredacted val memberStatus: String,
    val churchRole: String? = null,
    /** publicId of the member's church group, or null. Used to pre-select the group on edit. */
    @Unredacted val groupPublicId: String? = null,
    @Unredacted val groupName: String? = null,
    val profileImageUrl: String? = null,
    /** Full training history, ordered by training sort order. */
    @Unredacted val trainings: List<UserTrainingDto> = emptyList(),
    /** Full ministry history, most recent registration period first. */
    @Unredacted val ministries: List<MinistryHistoryDto> = emptyList(),
    @Unredacted val isNextGroupLeader: Boolean = false,
    @Unredacted val oneOnOneSignupFilled: Boolean = false,
)

/** Lightweight DTO used in the paginated list endpoint. */
@Redacted
data class MemberSummaryDto(
    @Unredacted val publicId: String,
    val lastName: String,
    val firstName: String,
    val email: String? = null,
    @Unredacted val memberStatus: String,
    val baptism: String? = null,
    @Unredacted val groupPublicId: String? = null,
    @Unredacted val groupName: String? = null,
    @Unredacted val updatedAt: Instant? = null,
    /** Name of the member's latest completed training (highest sort order), or null. */
    @Unredacted val latestTraining: String? = null,
    /** All of the member's trainings, ordered by progression — rendered as chips in the grid. */
    @Unredacted val trainings: List<SummaryTrainingDto> = emptyList(),
    /** Names of the member's currently-active ministry assignments (end_date IS NULL), sorted. */
    @Unredacted val activeMinistries: List<String> = emptyList(),
    val churchRole: String? = null,
    @Unredacted val isNextGroupLeader: Boolean = false,
    @Unredacted val oneOnOneSignupFilled: Boolean = false,
)

/**
 * Minimal member identity for name pickers (e.g. the ministry "맴버 추가" dropdown).
 * No PII beyond the display name: [discriminator] disambiguates identical names
 * (members sharing a name get an appended marker). Internal id is never exposed.
 */
data class MemberNameDto(
    val publicId: String,
    val fullName: String,
    val discriminator: String? = null,
)

/** A member's training as shown on the grid chip: catalog name + status (IN_PROGRESS | COMPLETED). */
data class SummaryTrainingDto(
    val name: String,
    val status: String,
)

/** A single training entry in a member's history. */
data class UserTrainingDto(
    val trainingPublicId: String,
    val name: String,
    val status: String,
    val completedAt: LocalDate? = null,
)

/** A single ministry assignment in a member's history. */
data class MinistryHistoryDto(
    val ministryPublicId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val note: String? = null,
)

/**
 * Own-profile response — used by GET /me.
 * publicId only — internal id is never returned.
 */
@Redacted
data class MemberResponse(
    @Unredacted val publicId: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    @Unredacted val status: MemberStatus,
    val churchRole: String? = null,
    @Unredacted val groupName: String? = null,
    /** Division of the member's church group (e.g. "2교구"), or null when ungrouped. */
    @Unredacted val division: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val phoneNumber: String? = null,
    val birthDate: LocalDate? = null,
    val profileImageUrl: String? = null,
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

@Redacted
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
    @field:Size(max = 50)
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    /** Church position/title (직분), not the app access role. */
    val churchRole: String? = null,
    /** publicId of the church group to assign; null leaves the member ungrouped. */
    @Unredacted val groupPublicId: String? = null,
    val profileImageUrl: String? = null,
)

/**
 * PATCH semantics — every field is optional. Only non-null fields are applied.
 * Status transition rule: ACTIVE ↔ INACTIVE only. DELETED is terminal (use DELETE endpoint).
 */
@Redacted
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
    @field:Size(max = 50)
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val registrationDate: LocalDate? = null,
    @Unredacted val memberStatus: String? = null,
    val churchRole: String? = null,
    /**
     * publicId of the church group to assign. Null/absent leaves the current group
     * unchanged; a blank string ("") clears it (leaves the member ungrouped).
     */
    @Unredacted val groupPublicId: String? = null,
    val profileImageUrl: String? = null,
    @Unredacted val isNextGroupLeader: Boolean? = null,
    @Unredacted val oneOnOneSignupFilled: Boolean? = null,
)

@Redacted
data class RegisterMemberRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    val firstName: String,
    @field:NotBlank(message = "성은 필수입니다.")
    val lastName: String,
    @field:NotBlank
    val password: String,
    // Trim before validation so a stray leading/trailing space (mobile keyboard, copy-paste)
    // can't fail @Email or create a mismatched Keycloak username. Password is deliberately not trimmed.
    @field:JsonDeserialize(converter = TrimmingStringConverter::class)
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
    @field:Size(max = 50)
    val houseNumber: String? = null,
    val zipCode: String? = null,
)

/** PATCH /me — member can update their own contact, profile image, and address. */
@Redacted
data class UpdateMyProfileRequest(
    @field:Size(max = 50)
    val phoneNumber: String? = null,
    val birthDate: LocalDate? = null,
    val profileImageUrl: String? = null,
    val street: String? = null,
    @field:Size(max = 50)
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
)

/**
 * PUT /members/{publicId}/trainings — replaces the member's entire training set.
 * Each item references a training by its publicId; status is IN_PROGRESS | COMPLETED.
 */
data class ReplaceMemberTrainingsRequest(
    @field:Valid
    val trainings: List<MemberTrainingItem> = emptyList(),
)

data class MemberTrainingItem(
    @field:NotBlank(message = "trainingPublicId는 필수입니다.")
    val trainingPublicId: String,
    @field:NotBlank(message = "status는 필수입니다.")
    val status: String,
    val completedAt: LocalDate? = null,
)

/**
 * PUT /members/{publicId}/ministries — replaces the member's entire assignment set.
 * Each item references a ministry by its publicId, with a start month/year and an
 * optional end (null = ongoing/active).
 */
data class ReplaceMemberMinistriesRequest(
    @field:Valid
    val ministries: List<MemberMinistryItem> = emptyList(),
)

data class MemberMinistryItem(
    @field:NotBlank(message = "ministryPublicId는 필수입니다.")
    val ministryPublicId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    @field:Size(max = 500, message = "비고는 최대 500자입니다.")
    val note: String? = null,
)
