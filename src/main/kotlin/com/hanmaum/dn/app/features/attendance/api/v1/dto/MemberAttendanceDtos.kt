package com.hanmaum.dn.app.features.attendance.api.v1.dto

import java.time.Instant
import java.time.LocalDate

/**
 * One scheduled attendance occurrence and whether the caller made it.
 *
 * Rows exist in attendance_logs only for check-ins that happened, so a 미출석 entry is
 * derived: the definition scheduled that day, and no log for it was found.
 */
data class MemberAttendanceEntryResponse(
    val definitionPublicId: String,
    /** Current display title of the attendance definition. */
    val definitionTitle: String,
    val date: LocalDate,
    val checkedIn: Boolean,
    /**
     * When the check-in was recorded; null for a missed occurrence.
     *
     * Exposed here and nowhere else: this is the caller's own history. The group-count
     * endpoint still reveals no timestamps, and no endpoint reveals another member's.
     */
    val checkedInAt: Instant?,
)

/**
 * The caller's attendance history over a resolved date range.
 *
 * [from] and [to] echo the range the server actually used, since both parameters are
 * optional and `to` is never allowed past today.
 */
data class MemberAttendanceHistoryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val entries: List<MemberAttendanceEntryResponse>,
)

/**
 * The counters behind the 이번 달 출석 tile, the 올해 출석 figure, and the year rate.
 *
 * All four counts are measured against the occurrences the *active* definitions schedule,
 * so a numerator can never exceed its denominator. A check-in against a since-deactivated
 * definition still shows up in the history list but does not move these numbers.
 */
data class MemberAttendanceSummaryResponse(
    /** Occurrences attended in the current calendar month. */
    val monthAttended: Int,
    /** Occurrences scheduled in the current calendar month, including ones still ahead. */
    val monthTotal: Int,
    /** Occurrences attended since 1 January. */
    val yearAttended: Int,
    /**
     * Occurrences scheduled since 1 January up to and including today.
     *
     * Deliberately not the whole year: dividing by all 52 Sundays would report a member
     * with perfect attendance in January at 8%.
     */
    val yearToDateTotal: Int,
    /** [yearAttended] / [yearToDateTotal] as a 0..1 fraction; 0.0 when nothing is scheduled yet. */
    val rate: Double,
)
