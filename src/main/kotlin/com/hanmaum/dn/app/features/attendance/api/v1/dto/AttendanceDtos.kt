package com.hanmaum.dn.app.features.attendance.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import jakarta.validation.constraints.AssertTrue
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
    @get:JsonProperty("isActive")
    val isActive: Boolean,
)

/**
 * Optional body for POST /attendance/check-in. Omitting it entirely is equivalent to
 * sending one without a position.
 *
 * The client sends the raw fix and the platform's own accuracy estimate — never a verdict.
 * The server owns the comparison because it owns the radius, and because a client-supplied
 * "I was there" would be one line to forge.
 */
data class AttendanceCheckInRequest(
    /** WGS84 latitude of the device fix. */
    val latitude: Double? = null,
    /** WGS84 longitude of the device fix. */
    val longitude: Double? = null,
    /** The platform's accuracy estimate for this fix, in metres (a radius, not a diameter). */
    val accuracyMeters: Double? = null,
) {
    /**
     * All three or none. Two of three cannot be judged and is a client bug worth surfacing,
     * rather than something to silently treat as "no position".
     */
    @AssertTrue(message = "latitude, longitude, and accuracyMeters must be sent together or not at all")
    fun isPositionComplete(): Boolean {
        val provided = listOfNotNull(latitude, longitude, accuracyMeters).size
        return provided == 0 || provided == 3
    }
}

data class AttendanceCheckInResponse(
    /** Attendance definition used for the accepted check-in. */
    val definitionPublicId: String,
    /** Current display title of the attendance definition. */
    val definitionTitle: String,
    /** Calendar date recorded for attendance; no exact check-in time is exposed. */
    val attendanceDate: LocalDate,
    /**
     * What the server could establish about the member's position. The check-in was
     * accepted regardless — this never explains a rejection.
     */
    val presence: CheckInPresence,
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
    /** Of those, the ones the server could place inside the church geofence. */
    val inPlaceCount: Long,
    /** Of those, the ones measured somewhere else. */
    val outsideCount: Long,
    /**
     * Of those, the ones with no usable position — no location sent, a fix too imprecise to
     * judge, or a deployment without a geofence.
     *
     * Kept separate from [outsideCount] on purpose. A view that wants "not confirmed at the
     * church" adds the two; the reverse is impossible once they are summed here, and the
     * distinction is the whole reason the record has three states rather than a boolean.
     */
    val unconfirmedCount: Long,
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
    /** Of the total, those placed inside the church geofence. */
    val totalInPlaceCount: Long,
    /** Of the total, those measured elsewhere. */
    val totalOutsideCount: Long,
    /** Of the total, those with no usable position. */
    val totalUnconfirmedCount: Long,
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
    @get:JsonProperty("isActive")
    val isActive: Boolean? = null,
)
// CheckInRequest has no body — member resolved entirely from JWT subject.
