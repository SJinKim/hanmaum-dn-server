package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.repository.LongAbsenceSql
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * What counts as Sunday attendance and when an absence is long (HDN-375, decision #376).
 *
 * The per-log rule lives in [LongAbsenceSql.QUALIFYING_LOG]; this class supplies its
 * configuration and the calendar arithmetic, so the list, the summary, the writes and the
 * return marker all use the same definition.
 */
@Component
class SundayAttendancePolicy(
    @Value("\${app.long-absence.sunday-service-definition-ids:}") configuredIds: String,
) {
    /** 주일 2부/3부 definitions, including retired predecessors, that count at any time of day. */
    val sundayServiceDefinitionIds: Set<UUID> =
        configuredIds
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(UUID::fromString)
            .toSet()

    /** The id set as SQL array literal for `:sundayServiceIds`. */
    val sundayServiceIdsParameter: String = LongAbsenceSql.uuidArray(sundayServiceDefinitionIds)

    companion object {
        const val LIST_MONTHS = 3L
        const val SUMMARY_URGENT_MONTHS = 6L

        /**
         * A loose upper bound for the last Sunday that can be [LIST_MONTHS] calendar months back:
         * three calendar months are at least 89 days (e.g. 31.01. → 30.04.). Only narrows the SQL
         * scan; [isListed] decides.
         */
        fun candidateUpperBound(today: LocalDate): LocalDate = today.minusDays(89)

        /** Listed from the day three calendar months after the last Sunday, that day included. */
        fun isListed(
            lastSundayDate: LocalDate,
            today: LocalDate,
        ): Boolean = !lastSundayDate.plusMonths(LIST_MONTHS).isAfter(today)

        /** Largest n with lastSundayDate + n calendar months <= today. */
        fun absenceMonths(
            lastSundayDate: LocalDate,
            today: LocalDate,
        ): Long {
            var months = ChronoUnit.MONTHS.between(lastSundayDate, today).coerceAtLeast(0)
            // ChronoUnit counts by day-of-month; month-end anchors can be one short of the
            // plusMonths jubilee (31.01. + 3 months = 30.04.), so step up while it still fits.
            while (!lastSundayDate.plusMonths(months + 1).isAfter(today)) months++
            return months
        }

        fun absenceDays(
            lastSundayDate: LocalDate,
            today: LocalDate,
        ): Long = ChronoUnit.DAYS.between(lastSundayDate, today)
    }
}
