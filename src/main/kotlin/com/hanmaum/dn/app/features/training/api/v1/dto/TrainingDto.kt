package com.hanmaum.dn.app.features.training.api.v1.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime

/**
 * A training catalog entry as the 양육 list renders it.
 *
 * publicId is the external identifier; internal id is never exposed. The offering fields
 * are null for a course that is not currently running — the list then shows the name
 * alone, without a meta line or a badge.
 */
data class TrainingDto(
    val publicId: String,
    val name: String,
    val sortOrder: Int,
    /** One-line blurb under the name. */
    val description: String?,
    /** First day of the current run — the "9월 7일 시작" half of the meta line. */
    val startDate: LocalDate?,
    /** Length of the current run in weeks — the "4주" half of the meta line. */
    val durationWeeks: Int?,
    /** Drives the "신청 가능" badge. */
    val openForRegistration: Boolean,
)

/**
 * Everything the 양육 detail page shows: the list fields plus the 시간 / 장소 / 인도
 * block, the seat counter, the application deadline, and "이런 분께 권합니다".
 */
data class TrainingDetailDto(
    val publicId: String,
    val name: String,
    val nameKo: String?,
    val category: String?,
    val sortOrder: Int,
    val description: String?,
    val startDate: LocalDate?,
    val durationWeeks: Int?,
    val openForRegistration: Boolean,
    /** java.time.DayOfWeek name, e.g. "SUNDAY". */
    val weekday: String?,
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(type = "string", format = "time", example = "14:00")
    val startTime: LocalTime?,
    val durationMinutes: Int?,
    val location: String?,
    val leaderName: String?,
    /** Seats in this run; null means uncapped and the progress bar is hidden. */
    val capacity: Int?,
    /** Members currently signed up for this run — the "8" of "8 / 12명". */
    val registeredCount: Int,
    val registrationDeadline: LocalDate?,
    val targetAudience: List<String>,
)

/** Confirmation returned by POST /trainings/{publicId}/registrations. */
data class TrainingRegistrationDto(
    val trainingPublicId: String,
    val trainingName: String,
    /** Always APPLIED right after signing up; an admin moves it on from there. */
    val status: String,
    val appliedOn: LocalDate,
    val registeredCount: Int,
    val capacity: Int?,
)

/** A catalog entry, including the fields the admin grid needs for its columns. */
data class TrainingCatalogDto(
    val publicId: String,
    val code: String,
    val name: String,
    val nameKo: String?,
    val category: String?,
    val sortOrder: Int,
    val hasCohorts: Boolean,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val prerequisiteCode: String?,
)

/** One intake of a course that runs in cohorts, e.g. 청년 파워제자반 3기. */
data class TrainingCohortDto(
    val publicId: String,
    val series: String,
    val ordinal: Int,
    val label: String?,
    val cohortYear: Int?,
    val term: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
)
