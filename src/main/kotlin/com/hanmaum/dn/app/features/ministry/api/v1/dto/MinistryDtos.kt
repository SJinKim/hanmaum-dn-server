package com.hanmaum.dn.app.features.ministry.api.v1.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalTime
import java.util.UUID

// ─── Response DTOs ────────────────────────────────────────────────────────────

/** Lightweight DTO for list endpoints. */
data class MinistrySummaryDto(
    val publicId: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val contacts: List<MinistryContactDto>,
    val isActive: Boolean,
)

/** Full detail DTO returned by GET /{publicId}. */
data class MinistryDto(
    val publicId: String,
    val title: String,
    val subtitle: String,
    val about: String,
    val requirements: List<String>,
    val schedules: List<MinistryScheduleDto>,
    val contacts: List<MinistryContactDto>,
    val imageUrl: String?,
    val isActive: Boolean,
)

data class MinistryContactDto(
    val role: String,
    val name: String,
)

data class MinistryScheduleDto(
    val description: String,
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(type = "string", format = "time", example = "07:00")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(type = "string", format = "time", example = "09:00")
    val endTime: LocalTime,
)

/** One active member in a ministry — returned by GET /{publicId}/members. */
data class ActiveMinistryMemberDto(
    val publicId: String, // member public ID
    val fullName: String,
    val startDate: String, // ISO 'YYYY-MM-DD'
    val note: String?,
    val gender: String?, // "M" | "F" | null
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateMinistryRequest(
    @field:NotBlank(message = "사역 제목은 필수입니다.")
    @field:Size(max = 100, message = "사역 제목은 최대 100자입니다.")
    val title: String,
    @field:NotBlank(message = "사역 부제목은 필수입니다.")
    @field:Size(max = 200, message = "사역 부제목은 최대 200자입니다.")
    val subtitle: String,
    @field:NotBlank(message = "사역 소개는 필수입니다.")
    val about: String,
    @field:Size(max = 20, message = "지원 자격은 최대 20개입니다.")
    val requirements: List<
        @NotBlank(message = "지원 자격 내용은 비워둘 수 없습니다.")
        String,
    > = emptyList(),
    @field:Valid
    @field:Size(max = 20, message = "사역 일정은 최대 20개입니다.")
    val schedules: List<MinistryScheduleRequest> = emptyList(),
    @field:Valid
    @field:Size(max = 20, message = "연락처는 최대 20개입니다.")
    val contacts: List<MinistryContactRequest> = emptyList(),
    val imageUrl: String? = null,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateMinistryRequest(
    @field:Pattern(regexp = "(?s).*\\S.*", message = "사역 제목은 비워둘 수 없습니다.")
    @field:Size(max = 100)
    val title: String? = null,
    @field:Pattern(regexp = "(?s).*\\S.*", message = "사역 부제목은 비워둘 수 없습니다.")
    @field:Size(max = 200)
    val subtitle: String? = null,
    @field:Pattern(regexp = "(?s).*\\S.*", message = "사역 소개는 비워둘 수 없습니다.")
    val about: String? = null,
    @field:Size(max = 20, message = "지원 자격은 최대 20개입니다.")
    val requirements: List<
        @NotBlank(message = "지원 자격 내용은 비워둘 수 없습니다.")
        String,
    >? = null,
    @field:Valid
    @field:Size(max = 20, message = "사역 일정은 최대 20개입니다.")
    val schedules: List<MinistryScheduleRequest>? = null,
    @field:Valid
    @field:Size(max = 20, message = "연락처는 최대 20개입니다.")
    val contacts: List<MinistryContactRequest>? = null,
    val imageUrl: String? = null,
    val isActive: Boolean? = null,
)

/** Binds an existing member to a ministry — body of POST /{publicId}/members. */
data class AddMinistryMemberRequest(
    @field:NotNull(message = "회원 ID는 필수입니다.")
    val memberId: UUID,
    @field:Size(max = 500, message = "메모는 최대 500자입니다.")
    val note: String? = null,
)

data class MinistryContactRequest(
    @field:NotBlank(message = "연락처 역할은 필수입니다.")
    @field:Size(max = 50, message = "연락처 역할은 최대 50자입니다.")
    val role: String,
    @field:NotBlank(message = "연락처 이름은 필수입니다.")
    @field:Size(max = 150, message = "연락처 이름은 최대 150자입니다.")
    val name: String,
)

data class MinistryScheduleRequest(
    @field:NotBlank(message = "사역 일정 설명은 필수입니다.")
    @field:Size(max = 200, message = "사역 일정 설명은 최대 200자입니다.")
    val description: String,
    @field:JsonFormat(pattern = "HH:mm")
    @field:NotNull
    @field:Schema(type = "string", format = "time", example = "07:00")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    @field:NotNull
    @field:Schema(type = "string", format = "time", example = "09:00")
    val endTime: LocalTime,
)
