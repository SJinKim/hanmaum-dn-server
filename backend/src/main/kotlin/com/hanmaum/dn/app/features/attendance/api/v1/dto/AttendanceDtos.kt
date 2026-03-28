package com.hanmaum.dn.app.features.attendance.api.v1.dto

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
    val isActive: Boolean,
)

data class AttendanceLogDto(
    val publicId: String,
    val definitionPublicId: String,
    val definitionTitle: String,
    val memberPublicId: String,
    val memberName: String,
    val attendanceDate: LocalDate,
    val attended: Boolean,
)

data class AttendanceStatsDto(
    val memberPublicId: String,
    val memberName: String,
    val attendanceCount: Int,
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
    val isActive: Boolean? = null,
)
// CheckInRequest has no body — member resolved entirely from JWT subject.
