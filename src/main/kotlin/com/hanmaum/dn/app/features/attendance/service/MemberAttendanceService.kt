package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceEntryResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceHistoryResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceSummaryResponse
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.round

/**
 * The caller's own attendance history and counters.
 *
 * Kept apart from [AttendanceService] on purpose. That one owns definitions, check-in and
 * church-group aggregates, none of which may reveal who attended. Everything here is
 * person-level and is therefore only ever resolved from the caller's own JWT subject —
 * there is no path in this class that takes a member id from a request.
 */
@Service
class MemberAttendanceService(
    private val definitionRepo: AttendanceDefinitionRepository,
    private val logRepo: AttendanceLogRepository,
    private val memberRepo: MemberRepository,
    private val clock: Clock,
) {
    /**
     * The 최근 출석 list: every occurrence the active definitions scheduled in the range,
     * marked 출석 or 미출석, newest first.
     *
     * [from] defaults to [DEFAULT_WINDOW_DAYS] before [to]; [to] defaults to today and is
     * never allowed past it, because an occurrence that has not happened yet is not a
     * missed one.
     */
    @Transactional(readOnly = true)
    fun getHistory(
        keycloakSubject: String,
        from: LocalDate?,
        to: LocalDate?,
    ): MemberAttendanceHistoryResponse {
        val member = requireMember(keycloakSubject)
        val today = LocalDate.now(clock)
        val rangeEnd = minOf(to ?: today, today)
        val rangeStart = from ?: rangeEnd.minusDays(DEFAULT_WINDOW_DAYS)

        if (rangeStart.isAfter(rangeEnd)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.")
        }
        if (ChronoUnit.DAYS.between(rangeStart, rangeEnd) > MAX_RANGE_DAYS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간은 최대 ${MAX_RANGE_DAYS}일입니다.")
        }

        val definitions = activeDefinitions()
        val logs = logRepo.findMemberLogsBetween(member.id!!, rangeStart, rangeEnd)
        val attendedByOccurrence = logs.associateBy { Occurrence(it.definition.id!!, it.attendanceDate) }
        // Active definitions cover the 미출석 rows; the logs' own definitions cover check-ins
        // against a definition that has since been deactivated.
        val definitionsById = (definitions + logs.map { it.definition }).associateBy { it.id!! }

        val entries =
            (scheduledOccurrences(definitions, rangeStart, rangeEnd) + attendedByOccurrence.keys)
                .distinct()
                .map { occurrence ->
                    val log = attendedByOccurrence[occurrence]
                    val definition =
                        checkNotNull(definitionsById[occurrence.definitionId]) {
                            "Occurrence refers to a definition that was neither active nor carried by a log"
                        }
                    definition.toEntry(occurrence.date, log)
                }.sortedWith(compareByDescending<MemberAttendanceEntryResponse> { it.date }.thenBy { it.definitionTitle })

        return MemberAttendanceHistoryResponse(from = rangeStart, to = rangeEnd, entries = entries)
    }

    /** The 이번 달 출석 tile, the 올해 출석 figure, and the year rate behind them. */
    @Transactional(readOnly = true)
    fun getSummary(keycloakSubject: String): MemberAttendanceSummaryResponse {
        val member = requireMember(keycloakSubject)
        val today = LocalDate.now(clock)
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.with(TemporalAdjusters.lastDayOfMonth())
        val yearStart = today.withDayOfYear(1)

        val definitions = activeDefinitions()
        // One query covers both windows: the month is a slice of the year to date.
        val attendedThisYear =
            logRepo
                .findMemberLogsBetween(member.id!!, yearStart, today)
                .map { Occurrence(it.definition.id!!, it.attendanceDate) }
                .toSet()

        // Counting against the schedule rather than against every log keeps a numerator
        // from exceeding its denominator when a definition is deactivated mid-year.
        val scheduledThisMonth = scheduledOccurrences(definitions, monthStart, monthEnd)
        val scheduledYearToDate = scheduledOccurrences(definitions, yearStart, today)
        val monthAttended = scheduledThisMonth.count { it in attendedThisYear }
        val yearAttended = scheduledYearToDate.count { it in attendedThisYear }

        return MemberAttendanceSummaryResponse(
            monthAttended = monthAttended,
            monthTotal = scheduledThisMonth.size,
            yearAttended = yearAttended,
            yearToDateTotal = scheduledYearToDate.size,
            rate = ratio(yearAttended, scheduledYearToDate.size),
        )
    }

    /**
     * Every occurrence the active definitions schedule between [start] and [end].
     *
     * attendance_definitions is the single source of truth for when attendance is taken,
     * so the 미출석 rows are derived from it rather than from a second table that would
     * have to be kept in step.
     */
    private fun scheduledOccurrences(
        definitions: List<AttendanceDefinition>,
        start: LocalDate,
        end: LocalDate,
    ): List<Occurrence> {
        if (start.isAfter(end) || definitions.isEmpty()) {
            return emptyList()
        }
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .flatMap { date ->
                definitions.asSequence().filter { it.dayOfWeek == date.dayOfWeek }.map { Occurrence(it.id!!, date) }
            }.toList()
    }

    private fun activeDefinitions(): List<AttendanceDefinition> = definitionRepo.findAll(activeOnly = true)

    private fun requireMember(keycloakSubject: String) =
        memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

    private fun AttendanceDefinition.toEntry(
        date: LocalDate,
        log: AttendanceLog?,
    ) = MemberAttendanceEntryResponse(
        definitionPublicId = publicId.toString(),
        definitionTitle = title,
        date = date,
        checkedIn = log != null,
        checkedInAt = log?.createdAt,
    )

    /** One scheduled attendance slot: a definition on a given calendar day. */
    private data class Occurrence(
        val definitionId: Long,
        val date: LocalDate,
    )

    companion object {
        /** How far back the 최근 출석 list reaches when the caller passes no `from`. */
        const val DEFAULT_WINDOW_DAYS = 90L

        /** Bounds the day-by-day enumeration; a year of Sundays is already generous. */
        const val MAX_RANGE_DAYS = 366L

        private fun ratio(
            attended: Int,
            scheduled: Int,
        ): Double = if (scheduled == 0) 0.0 else round(attended.toDouble() / scheduled * 1000) / 1000
    }
}
