package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
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
    fun findAll(@Param("activeOnly") activeOnly: Boolean): List<AttendanceDefinition>

    /** Active definitions for a given day of week — used during check-in window lookup. */
    fun findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(dayOfWeek: DayOfWeek): List<AttendanceDefinition>
}

@Repository
interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {

    /** Duplicate guard: has this member already checked in for this definition on this date? */
    fun existsByMemberIdAndDefinitionIdAndAttendanceDateAndDeletedAtIsNull(
        memberId: Long,
        definitionId: Long,
        attendanceDate: LocalDate,
    ): Boolean

    /** Own history — all logs for a member ordered by date descending. */
    fun findAllByMemberIdAndDeletedAtIsNullOrderByAttendanceDateDesc(memberId: Long): List<AttendanceLog>

    /** Admin: logs filtered by optional member, definition, and date range. */
    @Query(
        """
        SELECT l FROM AttendanceLog l
        WHERE l.deletedAt IS NULL
          AND (:memberId IS NULL OR l.member.id = :memberId)
          AND (:definitionId IS NULL OR l.definition.id = :definitionId)
          AND l.attendanceDate >= :from
          AND l.attendanceDate <= :to
        ORDER BY l.attendanceDate DESC
        """,
    )
    fun findForAdmin(
        @Param("memberId") memberId: Long?,
        @Param("definitionId") definitionId: Long?,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<AttendanceLog>

    /** Stats: all logs in a date range for grouping by member. */
    @Query(
        """
        SELECT l FROM AttendanceLog l
        WHERE l.deletedAt IS NULL
          AND l.attendanceDate >= :from
          AND l.attendanceDate <= :to
        """,
    )
    fun findForStats(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<AttendanceLog>
}
