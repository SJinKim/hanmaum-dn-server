package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceFollowup
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceChangeRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDate

/**
 * Lifecycle of the 장기결석자 phases (HDN-375, HDN-379, decisions #376 F3 and #380): closing a
 * phase when the member checks in again, the two retention periods after a phase ended, and
 * removal when a member leaves.
 *
 * Everything that touches a member's phases runs under that member's row lock, the same lock
 * order as the API writers: member, then phases, then history entries.
 */
@Service
class LongAbsenceRetentionService(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val changeRepository: AttendanceAbsenceChangeRepository,
    private val memberRepository: MemberRepository,
    private val phases: LongAbsencePhases,
    private val policy: SundayAttendancePolicy,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

    /**
     * The phase a check-in may end, read before the log is inserted: the member's listed
     * absence, if any. Called by the check-in writers with the member locked.
     */
    @Transactional
    fun beforeCheckIn(member: Member): CheckInBaseline? {
        val current = phases.current(member.id!!, LocalDate.now(clock)) ?: return null
        val stored = followupRepository.findOpenPhases(member.id!!, current.lastSundayDate, current.absenceAnchorDate)
        return CheckInBaseline(current.lastSundayDate, current.absenceAnchorDate, stored = stored.isNotEmpty())
    }

    /**
     * After the check-in writers inserted a log, in their transaction and under the member lock:
     * closes the phases a qualifying visit after their anchor has ended, also the listed absence
     * that nothing was recorded for (stored now, so it shows in the history), and raises the
     * conflict counter so no editor loaded before writes into the next phase. A manual return
     * date a check-in has caught up with is no longer needed and cleared.
     */
    @Transactional
    fun afterCheckIn(
        member: Member,
        baseline: CheckInBaseline?,
    ) {
        val memberId = member.id!!
        val today = LocalDate.now(clock)
        val now = clock.instant()
        followupRepository.closeReturned(today, policy.sundayServiceIdsParameter, memberId, now)

        val revision = member.nextAbsenceRevision()
        if (baseline != null && !baseline.stored) {
            followupRepository
                .findFirstQualifyingDateAfter(today, policy.sundayServiceIdsParameter, memberId, baseline.absenceAnchorDate)
                ?.let { returnedOn ->
                    val phase =
                        AttendanceAbsenceFollowup(
                            member = member,
                            lastSundayDate = baseline.lastSundayDate,
                            absenceAnchorDate = baseline.absenceAnchorDate,
                            phaseGeneration = revision,
                        )
                    phase.closeByCheckIn(returnedOn, now)
                    followupRepository.save(phase)
                }
        }

        val manualReturnOn = member.absenceManualReturnOn ?: return
        val lastSunday = followupRepository.findLastQualifyingDate(today, policy.sundayServiceIdsParameter, memberId)
        if (lastSunday != null && !lastSunday.isBefore(manualReturnOn)) member.absenceManualReturnOn = null
    }

    /**
     * Daily cleanup step, in this order: catch up on returns not closed yet (a back-filled log,
     * downtime) in member batches with their own transaction, then clear memos and memo versions
     * of phases ended at least [MEMO_RETENTION_DAYS] days ago, then delete phases ended at least
     * [PHASE_RETENTION_YEARS] years ago. Returns the rows changed.
     *
     * Failures are rethrown without their cause: the cleanup wrapper logs exception messages,
     * and a PostgreSQL error detail could quote row values.
     */
    fun purgeExpired(): Int {
        val today = LocalDate.now(clock)
        try {
            val closed = closeReturnedInBatches(today)
            return closed +
                (
                    transactions.execute {
                        val memoVersions = changeRepository.deleteMemoVersionsClosedOnOrBefore(memoCutoff(today))
                        val memos = followupRepository.clearMemosClosedOnOrBefore(memoCutoff(today))
                        memoVersions + memos + followupRepository.deleteClosedOnOrBefore(phaseCutoff(today))
                    } ?: 0
                )
        } catch (failure: DataAccessException) {
            throw IllegalStateException("Long-absence retention failed sqlState=${sqlState(failure)}")
        }
    }

    private fun closeReturnedInBatches(today: LocalDate): Int {
        var closed = 0
        var afterMemberId = 0L
        while (true) {
            val batch =
                transactions
                    .execute {
                        val ids =
                            followupRepository.findMemberIdsToClose(today, policy.sundayServiceIdsParameter, afterMemberId, BATCH_SIZE)
                        if (ids.isNotEmpty()) memberRepository.lockIdsForUpdate(ids)
                        ids.forEach { memberId ->
                            closed += followupRepository.closeReturned(today, policy.sundayServiceIdsParameter, memberId, clock.instant())
                        }
                        ids
                    }.orEmpty()
            if (batch.size < BATCH_SIZE) return closed
            afterMemberId = batch.last()
        }
    }

    /**
     * Removes every phase and history entry of a member who became INACTIVE or graduated, and
     * the manual return date; nothing is restored later. The counter is raised, never reset.
     */
    @Transactional
    fun deleteForMember(member: Member): Int {
        member.absenceManualReturnOn = null
        member.nextAbsenceRevision()
        return followupRepository.deleteAllByMemberId(member.id!!)
    }

    /** A soft delete, restore or reinstatement: tokens loaded before it are stale. */
    @Transactional
    fun invalidate(member: Member) {
        member.nextAbsenceRevision()
    }

    private fun sqlState(failure: Throwable): String =
        generateSequence(failure) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState ?: "unknown"

    /** What the check-in writers remember about the listed absence before their insert. */
    data class CheckInBaseline(
        val lastSundayDate: LocalDate,
        val absenceAnchorDate: LocalDate,
        val stored: Boolean,
    )

    companion object {
        const val MEMO_RETENTION_DAYS = 90L
        const val PHASE_RETENTION_YEARS = 10L
        private const val BATCH_SIZE = 200

        /** Phases that ended on or before this day have lost their memo: the 90th day after the end, that day included. */
        fun memoCutoff(today: LocalDate): LocalDate = today.minusDays(MEMO_RETENTION_DAYS)

        /**
         * Phases that ended on or before this day are deleted: due on the day ten years after the
         * end, that day included. A phase that ended on 29 February is due on 28 February ten
         * years later, as [LocalDate.plusYears] counts, which `today.minusYears(10)` alone misses.
         */
        fun phaseCutoff(today: LocalDate): LocalDate {
            val cutoff = today.minusYears(PHASE_RETENTION_YEARS)
            val next = cutoff.plusDays(1)
            return if (!next.plusYears(PHASE_RETENTION_YEARS).isAfter(today)) next else cutoff
        }
    }
}
