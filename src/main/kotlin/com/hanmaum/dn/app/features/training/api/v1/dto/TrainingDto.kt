package com.hanmaum.dn.app.features.training.api.v1.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * A training catalog entry as the 양육 list renders it.
 *
 * publicId is the external identifier; internal id is never exposed.
 *
 * The registration fields come from application.hanmaum.de on the 양육 list
 * (`activeOnly=true`) and are computed for the caller. On the admin catalog
 * (`activeOnly=false`) they keep their defaults and [openForRegistration] is the stored flag.
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
    /**
     * Drives the 신청가능 / 신청마감 tag: true when at least one external course of this
     * training is open and the caller may apply to it (여자반 only for women).
     */
    val openForRegistration: Boolean,
    /** Start of the registration window the 기간 line shows; null means no start bound. */
    val registrationStartsAt: OffsetDateTime? = null,
    /** End of that window; null means no end bound or no course at all. */
    val registrationEndsAt: OffsetDateTime? = null,
    /** 상시 접수: show that instead of an end date. */
    @get:JsonProperty("isAlwaysOpen")
    val isAlwaysOpen: Boolean = false,
    /** The caller's own application to this training — 신청 현황. Null when there is none. */
    val myApplication: MyTrainingApplicationDto? = null,
)

/**
 * Everything the 양육 detail page shows: the list fields plus the 시간 / 장소 / 인도
 * block, "이런 분께 권합니다", the courses that can be applied to right now, and what the
 * application form is pre-filled with.
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
    /** Seats in this run as stored; not shown by the app for now. */
    val capacity: Int?,
    /** Members currently signed up for this run; not shown by the app for now. */
    val registeredCount: Int,
    val registrationDeadline: LocalDate?,
    val targetAudience: List<String>,
    val registrationStartsAt: OffsetDateTime? = null,
    val registrationEndsAt: OffsetDateTime? = null,
    @get:JsonProperty("isAlwaysOpen")
    val isAlwaysOpen: Boolean = false,
    /**
     * External courses of this training that are open right now — the 반 to choose from,
     * e.g. 큐베세 여자반 and 큐베세 직장인/청년 반. Includes courses the caller may not apply
     * to, flagged with isEligible = false, so the app can show them disabled.
     */
    val courses: List<TrainingCourseDto> = emptyList(),
    val myApplication: MyTrainingApplicationDto? = null,
    /** The caller's profile data the application form starts from. */
    val applicantPrefill: ApplicantPrefillDto? = null,
)

/** Confirmation returned by POST /trainings/{publicId}/registrations. */
data class TrainingRegistrationDto(
    val trainingPublicId: String,
    val trainingName: String,
    /** APPLIED right after applying; an admin moves it on from there. */
    val status: String,
    val appliedOn: LocalDate,
    val registeredCount: Int,
    /** The course's seat limit; null when it has none. */
    val capacity: Int?,
    /** The external course applied to, e.g. 106 for 큐베세 직장인/청년 반. */
    val externalCourseId: Int,
    val courseName: String,
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
