package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Repository
interface AttendanceDefinitionRepository : JpaRepository<AttendanceDefinition, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<AttendanceDefinition>

    /** All non-deleted definitions; [activeOnly] true filters to isActive=true only. */
    @Query(
        """
        SELECT d FROM AttendanceDefinition d
        WHERE d.deletedAt IS NULL
          AND (:activeOnly = false OR d.isActive = true)
        ORDER BY d.dayOfWeek ASC, d.windowStart ASC
        """,
    )
    fun findAll(
        @Param("activeOnly") activeOnly: Boolean,
    ): List<AttendanceDefinition>

    /** Active definitions for a given day of week — used during check-in window lookup. */
    fun findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(dayOfWeek: DayOfWeek): List<AttendanceDefinition>
}

@Repository
interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {
    /**
     * Atomically records one check-in. The database unique constraint is the
     * concurrency-safe duplicate guard.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO attendance_logs (
                public_id,
                definition_id,
                member_id,
                group_id_at_check_in,
                attendance_date,
                attended,
                presence,
                created_at
            )
            VALUES (
                :publicId,
                :definitionId,
                :memberId,
                :groupId,
                :attendanceDate,
                TRUE,
                :presence,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT ON CONSTRAINT uq_attendance_log DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("publicId") publicId: UUID,
        @Param("definitionId") definitionId: Long,
        @Param("memberId") memberId: Long,
        @Param("groupId") groupId: Long?,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("presence") presence: String,
    ): Int

    @Query(
        value = """
            WITH target_logs AS (
                SELECT attendance_log.id, attendance_log.group_id_at_check_in, attendance_log.presence
                FROM attendance_logs AS attendance_log
                WHERE attendance_log.definition_id = :definitionId
                  AND attendance_log.attendance_date = :attendanceDate
                  AND attendance_log.attended = TRUE
                  AND attendance_log.deleted_at IS NULL
            )
            SELECT *
            FROM (
                SELECT
                    church_group.public_id AS "groupPublicId",
                    church_group.division AS "groupDivision",
                    church_group.name AS "groupName",
                    COUNT(target_log.id) AS "attendanceCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'IN_PLACE') AS "inPlaceCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'OUTSIDE') AS "outsideCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'UNCONFIRMED') AS "unconfirmedCount"
                FROM church_groups AS church_group
                LEFT JOIN target_logs AS target_log
                    ON target_log.group_id_at_check_in = church_group.id
                WHERE church_group.deleted_at IS NULL
                   OR target_log.id IS NOT NULL
                GROUP BY church_group.public_id, church_group.division, church_group.name

                UNION ALL

                SELECT
                    CAST(NULL AS UUID) AS "groupPublicId",
                    CAST(NULL AS VARCHAR) AS "groupDivision",
                    CAST(NULL AS VARCHAR) AS "groupName",
                    COUNT(target_log.id) AS "attendanceCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'IN_PLACE') AS "inPlaceCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'OUTSIDE') AS "outsideCount",
                    COUNT(target_log.id) FILTER (WHERE target_log.presence = 'UNCONFIRMED') AS "unconfirmedCount"
                FROM target_logs AS target_log
                WHERE target_log.group_id_at_check_in IS NULL
            ) AS group_counts
            ORDER BY "groupDivision" NULLS LAST, "groupName" NULLS LAST
        """,
        nativeQuery = true,
    )
    fun countByChurchGroup(
        @Param("definitionId") definitionId: Long,
        @Param("attendanceDate") attendanceDate: LocalDate,
    ): List<ChurchGroupAttendanceCountView>

    /**
     * One member's accepted check-ins in a date range, newest first.
     *
     * The definition is fetched eagerly because every row is rendered with its title, and
     * the range is bounded by the caller, so this is a handful of rows rather than a page.
     * Served by uq_attendance_log, whose leading column is member_id.
     */
    @Query(
        """
        SELECT l FROM AttendanceLog l
        JOIN FETCH l.definition
        WHERE l.member.id = :memberId
          AND l.attendanceDate BETWEEN :from AND :to
          AND l.attended = true
          AND l.deletedAt IS NULL
        ORDER BY l.attendanceDate DESC
        """,
    )
    fun findMemberLogsBetween(
        @Param("memberId") memberId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<AttendanceLog>

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        DELETE FROM AttendanceLog l
        WHERE l.deleteEntryAt <= :now
          AND l.deletedAt IS NOT NULL
        """,
    )
    fun hardDeleteExpired(
        @Param("now") now: Instant,
    ): Int
}

interface ChurchGroupAttendanceCountView {
    val groupPublicId: UUID?
    val groupDivision: String?
    val groupName: String?
    val attendanceCount: Long
    val inPlaceCount: Long
    val outsideCount: Long
    val unconfirmedCount: Long
}
