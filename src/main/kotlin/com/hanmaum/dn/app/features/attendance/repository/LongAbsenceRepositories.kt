package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceChange
import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceFollowup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * SQL shared by the absentee query and the phase closing, so both agree on what a Sunday
 * check-in is (HDN-375, decision #376 F2). Expects `l` = attendance_logs, `d` = its
 * attendance_definitions row and the parameters `:today` and `:sundayServiceIds`.
 *
 * A log qualifies when it is an accepted, non-deleted check-in up to today and either
 * - belongs to a definition configured as 주일 2부/3부 (by public id, whatever its time,
 *   active flag or title today), or
 * - was recorded on a Sunday between 08:00 and 15:00 inclusive, Europe/Berlin, on the day it
 *   is for. A back-filled log has no time of the visit itself, so it only counts through the
 *   first branch.
 */
object LongAbsenceSql {
    const val QUALIFYING_LOG = """
        l.attended = TRUE
        AND l.deleted_at IS NULL
        AND l.member_id IS NOT NULL
        AND l.attendance_date <= :today
        AND (
            d.public_id = ANY(CAST(:sundayServiceIds AS uuid[]))
            OR (
                l.created_at IS NOT NULL
                AND EXTRACT(ISODOW FROM (l.created_at AT TIME ZONE 'Europe/Berlin')) = 7
                AND CAST(l.created_at AT TIME ZONE 'Europe/Berlin' AS date) = l.attendance_date
                AND CAST(l.created_at AT TIME ZONE 'Europe/Berlin' AS time) BETWEEN TIME '08:00:00' AND TIME '15:00:00'
            )
        )
    """

    /** Formats ids for `CAST(:sundayServiceIds AS uuid[])`; an empty set disables the first branch. */
    fun uuidArray(ids: Collection<java.util.UUID>): String = ids.joinToString(",", "{", "}")
}

/**
 * One member whose absence anchor lies on or before the caller's upper bound. The anchor is the
 * later of [lastSundayDate] and [manualReturnOn] (HDN-379).
 */
interface LongAbsenceCandidateView {
    val memberId: Long
    val lastSundayDate: LocalDate
    val manualReturnOn: LocalDate?
    val absenceAnchorDate: LocalDate
    val postAssignmentAttendance: String?
}

/** The memo-free part of an open follow-up, for counting without decrypting anything. */
data class AbsenceFollowupState(
    val memberId: Long,
    val lastSundayDate: LocalDate,
    val absenceAnchorDate: LocalDate,
    val contacted: Boolean,
)

/**
 * A stored phase as the history reads it. Encrypted columns arrive as stored and are decrypted by
 * the caller, so an expired memo is never decrypted; [memo] is null once its retention ran out.
 */
interface AbsencePhaseRow {
    val id: Long
    val phasePublicId: UUID
    val memberId: Long
    val memberPublicId: UUID
    val lastSundayDate: LocalDate
    val absenceAnchorDate: LocalDate
    val closedOn: LocalDate?
    val endReason: String?
    val endRecordedAt: Instant?
    val returnedBy: String?
    val contacted: Boolean
    val contactedBy: String?
    val contactedAt: Instant?
    val memo: String?
}

/** One history entry as stored; [actorSubject] and [memo] are still encrypted. */
interface AbsenceChangeRow {
    val publicId: UUID
    val revision: Long
    val kind: String
    val origin: String
    val occurredAt: Instant?
    val actorSubject: String?
    val contacted: Boolean?
    val memo: String?
}

private const val PHASE_COLUMNS = """
    f.id AS "id",
    f.public_id AS "phasePublicId",
    m.id AS "memberId",
    m.public_id AS "memberPublicId",
    f.last_sunday_date AS "lastSundayDate",
    f.absence_anchor_date AS "absenceAnchorDate",
    f.closed_on AS "closedOn",
    f.end_reason AS "endReason",
    f.end_recorded_at AS "endRecordedAt",
    f.returned_by AS "returnedBy",
    f.contacted AS "contacted",
    f.contacted_by AS "contactedBy",
    f.contacted_at AS "contactedAt"
"""

/** Closed phases that are still kept (ten years after closed_on) of members not soft-deleted. */
private const val HISTORY_FILTER = """
    FROM attendance_absence_followups f
    JOIN members m ON m.id = f.member_id
    WHERE f.closed_on IS NOT NULL
      AND f.closed_on > :phaseCutoff
      AND m.deleted_at IS NULL
      AND (CAST(:memberId AS bigint) IS NULL OR f.member_id = CAST(:memberId AS bigint))
"""

/** Entries of one phase without memo versions whose retention ran out. */
private const val CHANGES_FILTER = """
    FROM attendance_absence_changes c
    JOIN attendance_absence_followups f ON f.id = c.followup_id
    WHERE c.followup_id = :followupId
      AND NOT (c.kind = 'MEMO_CHANGED' AND f.closed_on IS NOT NULL AND f.closed_on <= :memoCutoff)
"""

@Repository
interface AttendanceAbsenceFollowupRepository : JpaRepository<AttendanceAbsenceFollowup, Long> {
    /**
     * Members who may be on the absentee list: active, linked to an app account, not a 새가족
     * and not marked as moved on in a newcomer profile (#376 F1), with the date of their last
     * qualifying Sunday check-in and their anchor. [upperBound] only narrows the scan; the exact
     * three-calendar-month rule is applied by the caller. [memberId] null means everyone.
     */
    @Query(
        value = """
            WITH last_sunday AS (
                SELECT l.member_id, MAX(l.attendance_date) AS last_sunday_date
                FROM attendance_logs l
                JOIN attendance_definitions d ON d.id = l.definition_id
                WHERE ${LongAbsenceSql.QUALIFYING_LOG}
                  AND (CAST(:memberId AS bigint) IS NULL OR l.member_id = CAST(:memberId AS bigint))
                GROUP BY l.member_id
            )
            SELECT m.id AS "memberId",
                   s.last_sunday_date AS "lastSundayDate",
                   m.absence_manual_return_on AS "manualReturnOn",
                   GREATEST(s.last_sunday_date, COALESCE(m.absence_manual_return_on, s.last_sunday_date)) AS "absenceAnchorDate",
                   p.post_assignment_attendance AS "postAssignmentAttendance"
            FROM last_sunday s
            JOIN members m ON m.id = s.member_id
            LEFT JOIN church_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
            LEFT JOIN newcomer_profiles p ON p.member_id = m.id AND p.deleted_at IS NULL
            WHERE m.deleted_at IS NULL
              AND m.member_status = 'ACTIVE'
              AND m.keycloak_lookup_hash IS NOT NULL
              AND COALESCE(g.is_newcomer_group, FALSE) = FALSE
              AND (p.id IS NULL OR p.lifecycle_status NOT IN ('SUBMITTED', 'IN_CARE'))
              AND (p.post_assignment_attendance IS NULL
                   OR p.post_assignment_attendance NOT IN ('CHANGED_CHURCH', 'RETURNED_OR_MOVED'))
              AND GREATEST(s.last_sunday_date, COALESCE(m.absence_manual_return_on, s.last_sunday_date)) <= :upperBound
        """,
        nativeQuery = true,
    )
    fun findCandidates(
        @Param("today") today: LocalDate,
        @Param("upperBound") upperBound: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long?,
    ): List<LongAbsenceCandidateView>

    /** The member's last qualifying Sunday check-in up to [today], whatever their status. */
    @Query(
        value = """
            SELECT MAX(l.attendance_date)
            FROM attendance_logs l
            JOIN attendance_definitions d ON d.id = l.definition_id
            WHERE ${LongAbsenceSql.QUALIFYING_LOG}
              AND l.member_id = :memberId
        """,
        nativeQuery = true,
    )
    fun findLastQualifyingDate(
        @Param("today") today: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long,
    ): LocalDate?

    /** The member's first qualifying Sunday check-in after [after] and up to [today]. */
    @Query(
        value = """
            SELECT MIN(l.attendance_date)
            FROM attendance_logs l
            JOIN attendance_definitions d ON d.id = l.definition_id
            WHERE ${LongAbsenceSql.QUALIFYING_LOG}
              AND l.member_id = :memberId
              AND l.attendance_date > :after
        """,
        nativeQuery = true,
    )
    fun findFirstQualifyingDateAfter(
        @Param("today") today: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long,
        @Param("after") after: LocalDate,
    ): LocalDate?

    /** Open phases only: a closed phase is history and never part of the current list. */
    @Query(
        """
        SELECT f FROM AttendanceAbsenceFollowup f
        WHERE f.member.id IN :memberIds
          AND f.closedOn IS NULL
        """,
    )
    fun findOpenByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<AttendanceAbsenceFollowup>

    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.attendance.repository.AbsenceFollowupState(
            f.member.id, f.lastSundayDate, f.absenceAnchorDate, f.contacted
        )
        FROM AttendanceAbsenceFollowup f
        WHERE f.member.id IN :memberIds
          AND f.closedOn IS NULL
        """,
    )
    fun findOpenStatesByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<AbsenceFollowupState>

    /** The stored open phase with this anchor, newest generation first. */
    @Query(
        """
        SELECT f FROM AttendanceAbsenceFollowup f
        WHERE f.member.id = :memberId
          AND f.lastSundayDate = :lastSundayDate
          AND f.absenceAnchorDate = :absenceAnchorDate
          AND f.closedOn IS NULL
        ORDER BY f.phaseGeneration DESC
        """,
    )
    fun findOpenPhases(
        @Param("memberId") memberId: Long,
        @Param("lastSundayDate") lastSundayDate: LocalDate,
        @Param("absenceAnchorDate") absenceAnchorDate: LocalDate,
    ): List<AttendanceAbsenceFollowup>

    /**
     * Members with a phase a qualifying check-in after its anchor has ended: an open one, or one
     * closed by a check-in that a back-filled earlier visit moves forward. Manual returns stay as
     * recorded. Ordered by id, so the cleanup locks them in the same order as the writers.
     */
    @Query(
        value = """
            SELECT DISTINCT f.member_id
            FROM attendance_absence_followups f
            JOIN attendance_logs l
                ON l.member_id = f.member_id
               AND l.attendance_date > f.absence_anchor_date
            JOIN attendance_definitions d ON d.id = l.definition_id
            WHERE ${LongAbsenceSql.QUALIFYING_LOG}
              AND f.member_id > :afterMemberId
              AND (f.closed_on IS NULL OR (f.end_reason = 'CHECK_IN' AND f.closed_on > l.attendance_date))
            ORDER BY f.member_id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findMemberIdsToClose(
        @Param("today") today: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("afterMemberId") afterMemberId: Long,
        @Param("limit") limit: Int,
    ): List<Long>

    /**
     * Closes the member's phases ended by a qualifying check-in after their anchor, with the
     * earliest such visit; the visit date, not today, starts the retention. A CHECK_IN close may
     * move earlier through a back-filled log, a manual return never changes. Run by both check-in
     * writers after their insert and by the daily cleanup, always under the member lock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(
        value = """
            UPDATE attendance_absence_followups f
            SET closed_on = r.return_date,
                end_reason = 'CHECK_IN',
                end_recorded_at = COALESCE(f.end_recorded_at, :now),
                updated_at = :now
            FROM (
                SELECT f2.id, MIN(l.attendance_date) AS return_date
                FROM attendance_absence_followups f2
                JOIN attendance_logs l
                    ON l.member_id = f2.member_id
                   AND l.attendance_date > f2.absence_anchor_date
                JOIN attendance_definitions d ON d.id = l.definition_id
                WHERE ${LongAbsenceSql.QUALIFYING_LOG}
                  AND f2.member_id = :memberId
                  AND (f2.end_reason IS NULL OR f2.end_reason = 'CHECK_IN')
                GROUP BY f2.id
            ) r
            WHERE f.id = r.id
              AND (f.closed_on IS NULL OR f.closed_on > r.return_date)
        """,
        nativeQuery = true,
    )
    fun closeReturned(
        @Param("today") today: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long,
        @Param("now") now: Instant,
    ): Int

    /** 90 days after the end (#380): the memo of the phase goes; its versions are deleted separately. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE attendance_absence_followups
            SET memo = NULL
            WHERE closed_on IS NOT NULL
              AND closed_on <= :memoCutoff
              AND memo IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun clearMemosClosedOnOrBefore(
        @Param("memoCutoff") memoCutoff: LocalDate,
    ): Int

    /** Ten years after the end (#380): the phase goes, with its remaining history entries. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            DELETE FROM attendance_absence_followups
            WHERE closed_on IS NOT NULL
              AND closed_on <= :phaseCutoff
        """,
        nativeQuery = true,
    )
    fun deleteClosedOnOrBefore(
        @Param("phaseCutoff") phaseCutoff: LocalDate,
    ): Int

    /** Removes every follow-up of a member who left (INACTIVE, graduation), history included. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "DELETE FROM attendance_absence_followups WHERE member_id = :memberId",
        nativeQuery = true,
    )
    fun deleteAllByMemberId(
        @Param("memberId") memberId: Long,
    ): Int

    @Query(value = "SELECT COUNT(*) $HISTORY_FILTER", nativeQuery = true)
    fun countHistory(
        @Param("phaseCutoff") phaseCutoff: LocalDate,
        @Param("memberId") memberId: Long?,
    ): Long

    /** One history page, newest end first; memo-free. */
    @Query(
        value = """
            SELECT $PHASE_COLUMNS, CAST(NULL AS text) AS "memo"
            $HISTORY_FILTER
            ORDER BY f.closed_on DESC, f.public_id DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findHistory(
        @Param("phaseCutoff") phaseCutoff: LocalDate,
        @Param("memberId") memberId: Long?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<AbsencePhaseRow>

    /**
     * One stored phase of the member, bound to both public ids, unless it is past its ten years.
     * The memo is left out once its 90 days ran out, so it is never decrypted.
     */
    @Query(
        value = """
            SELECT $PHASE_COLUMNS,
                   CASE WHEN f.closed_on IS NULL OR f.closed_on > :memoCutoff THEN f.memo END AS "memo"
            FROM attendance_absence_followups f
            JOIN members m ON m.id = f.member_id
            WHERE f.public_id = :phasePublicId
              AND f.member_id = :memberId
              AND (f.closed_on IS NULL OR f.closed_on > :phaseCutoff)
        """,
        nativeQuery = true,
    )
    fun findPhase(
        @Param("memberId") memberId: Long,
        @Param("phasePublicId") phasePublicId: UUID,
        @Param("phaseCutoff") phaseCutoff: LocalDate,
        @Param("memoCutoff") memoCutoff: LocalDate,
    ): AbsencePhaseRow?
}

@Repository
interface AttendanceAbsenceChangeRepository : JpaRepository<AttendanceAbsenceChange, Long> {
    /** Clearing the memo removes every memo version of the phase, actors included (#380). */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "DELETE FROM attendance_absence_changes WHERE followup_id = :followupId AND kind = 'MEMO_CHANGED'",
        nativeQuery = true,
    )
    fun deleteMemoVersions(
        @Param("followupId") followupId: Long,
    ): Int

    /** 90 days after the end of a phase its memo versions go; contact entries stay with the phase. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            DELETE FROM attendance_absence_changes c
            USING attendance_absence_followups f
            WHERE f.id = c.followup_id
              AND c.kind = 'MEMO_CHANGED'
              AND f.closed_on IS NOT NULL
              AND f.closed_on <= :memoCutoff
        """,
        nativeQuery = true,
    )
    fun deleteMemoVersionsClosedOnOrBefore(
        @Param("memoCutoff") memoCutoff: LocalDate,
    ): Int

    @Query(value = "SELECT COUNT(*) $CHANGES_FILTER", nativeQuery = true)
    fun countChanges(
        @Param("followupId") followupId: Long,
        @Param("memoCutoff") memoCutoff: LocalDate,
    ): Long

    @Query(
        value = """
            SELECT c.public_id AS "publicId",
                   c.revision AS "revision",
                   c.kind AS "kind",
                   c.origin AS "origin",
                   c.occurred_at AS "occurredAt",
                   c.actor_subject AS "actorSubject",
                   c.contacted AS "contacted",
                   c.memo AS "memo"
            $CHANGES_FILTER
            ORDER BY c.revision DESC, c.kind ASC, c.public_id DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findChanges(
        @Param("followupId") followupId: Long,
        @Param("memoCutoff") memoCutoff: LocalDate,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<AbsenceChangeRow>
}
