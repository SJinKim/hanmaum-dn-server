package com.hanmaum.dn.app.features.attendance.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ─── Response DTOs ────────────────────────────────────────────────────────────

data class DefinitionDto(
    val publicId: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    @JsonProperty("isActive")
    val isActive: Boolean,
)

data class AttendanceCheckInResponse(
    /** Attendance definition used for the accepted check-in. */
    val definitionPublicId: String,
    /** Current display title of the attendance definition. */
    val definitionTitle: String,
    /** Calendar date recorded for attendance; no exact check-in time is exposed. */
    val attendanceDate: LocalDate,
)

data class ChurchGroupAttendanceCountResponse(
    /** Church-group identifier, or null for members without a group. */
    val groupPublicId: String?,
    /** Optional church-group division/category. */
    val groupDivision: String?,
    /** Church-group name, or null for members without a group. */
    val groupName: String?,
    /** Number of accepted check-ins attributed to this group snapshot. */
    val attendanceCount: Long,
)

data class AttendanceGroupCountsResponse(
    /** Attendance definition represented by this summary. */
    val definitionPublicId: String,
    /** Current display title of the attendance definition. */
    val definitionTitle: String,
    /** Calendar date represented by this summary. */
    val attendanceDate: LocalDate,
    /** Total accepted check-ins across all groups and ungrouped members. */
    val totalCount: Long,
    /** Per-group counts, including active groups with zero check-ins. */
    val groups: List<ChurchGroupAttendanceCountResponse>,
)

// ─── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateDefinitionRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 100, message = "제목은 최대 100자입니다.")
    val title: String,
    @field:NotNull(message = "요일은 필수입니다.")
    val dayOfWeek: DayOfWeek,
    @field:NotNull(message = "시작 시간은 필수입니다.")
    val windowStart: LocalTime,
    @field:NotNull(message = "종료 시간은 필수입니다.")
    val windowEnd: LocalTime,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateDefinitionRequest(
    @field:Size(max = 100)
    val title: String? = null,
    val dayOfWeek: DayOfWeek? = null,
    val windowStart: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    @JsonProperty("isActive")
    val isActive: Boolean? = null,
)
// CheckInRequest has no body — member resolved entirely from JWT subject.
