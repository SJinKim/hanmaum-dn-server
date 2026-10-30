package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceFollowup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * SQL shared by the absentee query and the return marker, so both agree on what a Sunday
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

/** One member whose last qualifying Sunday lies on or before the caller's upper bound. */
interface LongAbsenceCandidateView {
    val memberId: Long
    val lastSundayDate: LocalDate
    val postAssignmentAttendance: String?
}

/** The memo-free part of a follow-up, for counting without decrypting anything. */
data class AbsenceFollowupState(
    val memberId: Long,
    val lastSundayDate: LocalDate,
    val contacted: Boolean,
    val closedOn: LocalDate?,
)

@Repository
interface AttendanceAbsenceFollowupRepository : JpaRepository<AttendanceAbsenceFollowup, Long> {
    /**
     * Members who may be on the absentee list: active, linked to an app account, not a 새가족
     * and not marked as moved on in a newcomer profile (#376 F1), with the date of their last
     * qualifying Sunday check-in. [upperBound] only narrows the scan; the exact
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
              AND s.last_sunday_date <= :upperBound
        """,
        nativeQuery = true,
    )
    fun findCandidates(
        @Param("today") today: LocalDate,
        @Param("upperBound") upperBound: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long?,
    ): List<LongAbsenceCandidateView>

    @Query(
        """
        SELECT f FROM AttendanceAbsenceFollowup f
        WHERE f.member.id IN :memberIds
        """,
    )
    fun findAllByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<AttendanceAbsenceFollowup>

    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.attendance.repository.AbsenceFollowupState(
            f.member.id, f.lastSundayDate, f.contacted, f.closedOn
        )
        FROM AttendanceAbsenceFollowup f
        WHERE f.member.id IN :memberIds
        """,
    )
    fun findStatesByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<AbsenceFollowupState>

    @Query(
        """
        SELECT f FROM AttendanceAbsenceFollowup f
        WHERE f.member.id = :memberId
          AND f.lastSundayDate = :lastSundayDate
        """,
    )
    fun findPhase(
        @Param("memberId") memberId: Long,
        @Param("lastSundayDate") lastSundayDate: LocalDate,
    ): AttendanceAbsenceFollowup?

    /**
     * Marks every phase the member has since returned from with the earliest qualifying
     * check-in after its anchor. Run by both check-in writers after their insert and by the
     * daily cleanup for all members ([memberId] null), so a return is never lost, even after
     * downtime or for a back-filled log. A later back-fill can only move the date earlier.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE attendance_absence_followups f
            SET closed_on = r.return_date
            FROM (
                SELECT f2.id, MIN(l.attendance_date) AS return_date
                FROM attendance_absence_followups f2
                JOIN attendance_logs l
                    ON l.member_id = f2.member_id
                   AND l.attendance_date > f2.last_sunday_date
                JOIN attendance_definitions d ON d.id = l.definition_id
                WHERE ${LongAbsenceSql.QUALIFYING_LOG}
                  AND (CAST(:memberId AS bigint) IS NULL OR f2.member_id = CAST(:memberId AS bigint))
                GROUP BY f2.id
            ) r
            WHERE f.id = r.id
              AND (f.closed_on IS NULL OR f.closed_on > r.return_date)
        """,
        nativeQuery = true,
    )
    fun markReturned(
        @Param("today") today: LocalDate,
        @Param("sundayServiceIds") sundayServiceIds: String,
        @Param("memberId") memberId: Long?,
    ): Int

    /** Hard-deletes phases that ended on or before [closedOnOrBefore] (90-day retention, #376). */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            DELETE FROM attendance_absence_followups
            WHERE closed_on IS NOT NULL
              AND closed_on <= :closedOnOrBefore
        """,
        nativeQuery = true,
    )
    fun deleteClosedOnOrBefore(
        @Param("closedOnOrBefore") closedOnOrBefore: LocalDate,
    ): Int

    /** Removes every follow-up of a member who left (INACTIVE, graduation), memo included. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "DELETE FROM attendance_absence_followups WHERE member_id = :memberId",
        nativeQuery = true,
    )
    fun deleteAllByMemberId(
        @Param("memberId") memberId: Long,
    ): Int
}
