package com.hanmaum.dn.app.features.training.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** One external course the caller can choose on the 양육 detail page. */
data class TrainingCourseDto(
    val externalCourseId: Int,
    /** As published, e.g. "큐베세 직장인/청년 반". */
    val name: String,
    /** Free-text schedule, e.g. "3월 2일 - 3월 23일 매주 월요일 저녁 7시30분 비전홀". */
    val dateText: String?,
    val description: String?,
    val secondaryText: String?,
    val registrationStartsAt: OffsetDateTime?,
    val registrationEndsAt: OffsetDateTime?,
    @get:JsonProperty("isAlwaysOpen")
    val isAlwaysOpen: Boolean,
    /** False when the caller may not apply, e.g. a 여자반 for a member who is not a woman. */
    @get:JsonProperty("isEligible")
    val isEligible: Boolean,
    /** The fields this course's form asks for, named like [TrainingApplicationRequest]. */
    val formFields: List<CourseFormFieldDto>,
)

/** One field of a course's application form. */
data class CourseFormFieldDto(
    /** A [TrainingApplicationRequest] property name, e.g. "phone" or "history". */
    val name: String,
    /** Input type as the external API defines it, e.g. "enum"; null when it does not say. */
    val type: String?,
    val required: Boolean,
    /** Display label from the external API; null when it does not provide one. */
    val label: String?,
    val options: List<CourseFormFieldOptionDto> = emptyList(),
)

data class CourseFormFieldOptionDto(
    val value: String,
    val label: String?,
)

/** Profile data the application form starts from. Editing the form does not change the profile. */
data class ApplicantPrefillDto(
    val name: String?,
    val birthDate: LocalDate?,
    val email: String?,
    val phone: String?,
    /** "M" or "F". */
    val gender: String?,
    val residence: String?,
    /** 양육 받은 경험: one line per completed training, e.g. "큐티베이직세미나 / 2017년 5월". */
    val history: String? = null,
    /** 현재 신청한 양육: one line per training applied or enrolled for. */
    val waiting: String? = null,
    /** 현재 진행 중인 양육: one line per training in progress. */
    val running: String? = null,
    /** 세례 여부 as the form's option value: 1 유아세례, 2 입교, 3 세례, 4 미세례. */
    val baptized: String? = null,
    /** 세례 구분 as the form's option value: 1 유아세례, 3 입교, 4 세례, 5 미세례 (2 아동세례 is never prefilled). */
    val baptizeType: String? = null,
) {
    // Hand-written: the generated toString would render every field of a person's profile.
    override fun toString(): String = "ApplicantPrefillDto(<redacted>)"
}

/** An application the caller made through the app — 신청 현황 and 나의 신청. */
data class MyTrainingApplicationDto(
    val trainingPublicId: String,
    val trainingName: String,
    val trainingNameKo: String?,
    val externalCourseId: Int,
    val courseName: String,
    /** When the application was made. */
    val appliedAt: Instant,
    /** A TrainingStatus name: APPLIED, ENROLLED, IN_PROGRESS, COMPLETED, DROPPED or UNKNOWN. */
    val status: String,
)

/**
 * POST /trainings/{publicId}/registrations.
 *
 * Every applicant field is optional: whatever is left out is taken from the caller's
 * profile. Values sent here go to the application only and never change the profile.
 */
data class TrainingApplicationRequest(
    /** The course chosen on the detail page; must belong to this training and be open. */
    val externalCourseId: Int,
    @field:Size(max = 100) val name: String? = null,
    val birthDate: LocalDate? = null,
    @field:Email @field:Size(max = 254) val email: String? = null,
    @field:Size(max = 50) val phone: String? = null,
    @field:Pattern(regexp = "[MF]") val gender: String? = null,
    @field:Size(max = 50) val baptized: String? = null,
    @field:Size(max = 50) val baptizeType: String? = null,
    @field:Size(max = 200) val residence: String? = null,
    @field:Size(max = 50) val gyogu: String? = null,
    @field:Size(max = 50) val soon: String? = null,
    @field:Size(max = 2000) val children: String? = null,
    @field:Size(max = 2000) val history: String? = null,
    @field:Size(max = 2000) val waiting: String? = null,
    @field:Size(max = 2000) val running: String? = null,
    @field:Size(max = 2000) val comment: String? = null,
) {
    // Hand-written: the generated toString would put name, birth date and contact details
    // into any log line or exception message that renders the request.
    override fun toString(): String = "TrainingApplicationRequest(externalCourseId=$externalCourseId)"
}
