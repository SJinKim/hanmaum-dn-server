package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDate

/**
 * Lifecycle of the 장기결석자 follow-ups (HDN-375, decision #376 F3): closing a phase when the
 * member returns, the 90-day hard delete of closed phases, and removal when a member leaves.
 */
@Service
class LongAbsenceRetentionService(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val policy: SundayAttendancePolicy,
    private val clock: Clock,
) {
    /**
     * Closes the member's phases they have just returned from. Called by the check-in writers in
     * their own transaction, after the insert and under the member lock.
     */
    @Transactional
    fun markReturned(memberId: Long): Int =
        followupRepository.markReturned(LocalDate.now(clock), policy.sundayServiceIdsParameter, memberId)

    /**
     * Daily cleanup step. Catches up on returns not marked yet (a back-filled log, downtime),
     * then hard-deletes phases closed at least [RETENTION_DAYS] days ago. Returns the rows deleted.
     *
     * Failures are rethrown without their cause: the cleanup wrapper logs exception messages,
     * and a PostgreSQL error detail could quote row values.
     */
    @Transactional
    fun purgeExpired(): Int {
        val today = LocalDate.now(clock)
        try {
            followupRepository.markReturned(today, policy.sundayServiceIdsParameter, null)
            return followupRepository.deleteClosedOnOrBefore(today.minusDays(RETENTION_DAYS))
        } catch (failure: DataAccessException) {
            throw IllegalStateException("Long-absence retention failed sqlState=${sqlState(failure)}")
        }
    }

    /** Removes every follow-up of a member who became INACTIVE or graduated; nothing is restored later. */
    @Transactional
    fun deleteForMember(memberId: Long): Int = followupRepository.deleteAllByMemberId(memberId)

    private fun sqlState(failure: Throwable): String =
        generateSequence(failure) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState ?: "unknown"

    companion object {
        const val RETENTION_DAYS = 90L

        /** A closed phase is due on its 90th day after the return, that day included. */
        fun isDue(
            closedOn: LocalDate,
            today: LocalDate,
        ): Boolean = !closedOn.plusDays(RETENTION_DAYS).isAfter(today)
    }
}
