package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface NewcomerVisitRepository : JpaRepository<NewcomerVisit, Long> {
    /** Visits in [from, to], newest day first and, within a day, newest capture first. */
    @Query(
        """
        SELECT v FROM NewcomerVisit v
        LEFT JOIN FETCH v.newcomerProfile p
        LEFT JOIN FETCH p.member
        WHERE v.deletedAt IS NULL AND v.visitDate BETWEEN :from AND :to
        ORDER BY v.visitDate DESC, v.createdAt DESC, v.id DESC
        """,
    )
    fun findInRange(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<NewcomerVisit>

    /** One page of [findInRange]; the list has no one-year cap (#265). */
    @Query(
        value = """
        SELECT v FROM NewcomerVisit v
        LEFT JOIN FETCH v.newcomerProfile p
        LEFT JOIN FETCH p.member
        WHERE v.deletedAt IS NULL AND v.visitDate BETWEEN :from AND :to
        ORDER BY v.visitDate DESC, v.createdAt DESC, v.id DESC
        """,
        countQuery = """
        SELECT COUNT(v) FROM NewcomerVisit v
        WHERE v.deletedAt IS NULL AND v.visitDate BETWEEN :from AND :to
        """,
    )
    fun findPageInRange(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        pageable: Pageable,
    ): Page<NewcomerVisit>

    @Query(
        """
        SELECT v FROM NewcomerVisit v
        LEFT JOIN FETCH v.newcomerProfile p
        LEFT JOIN FETCH p.member
        WHERE v.publicId = :publicId AND v.deletedAt IS NULL
        """,
    )
    fun findActive(
        @Param("publicId") publicId: UUID,
    ): Optional<NewcomerVisit>

    /**
     * Quick records (빠른 등록) on [visitDate] that the 순별 counts of [definitionId] add to the
     * 새가족 순 (HDN-312). Only visits pinned to that definition count, so a day with several
     * services counts each visit once.
     *
     * A visit whose linked profile's member has an attended log for [definitionId] on that day
     * is already counted through the check-in, so it is left out here.
     */
    @Query(
        value = """
        SELECT COUNT(*)
        FROM newcomer_visits AS visit
        LEFT JOIN newcomer_profiles AS profile ON profile.id = visit.newcomer_profile_id
        WHERE visit.deleted_at IS NULL
          AND visit.visit_date = :visitDate
          AND visit.definition_id = :definitionId
          AND NOT EXISTS (
              SELECT 1
              FROM attendance_logs AS attendance_log
              WHERE attendance_log.definition_id = :definitionId
                AND attendance_log.attendance_date = :visitDate
                AND attendance_log.attended = TRUE
                AND attendance_log.deleted_at IS NULL
                AND attendance_log.member_id = profile.member_id
          )
        """,
        nativeQuery = true,
    )
    fun countQuickRecordsWithoutCheckIn(
        @Param("visitDate") visitDate: LocalDate,
        @Param("definitionId") definitionId: Long,
    ): Long

    /**
     * Of [countQuickRecordsWithoutCheckIn], the visits whose linked member already stands ACTIVE in
     * the 새가족 순 [groupId], so a roster-based 전체 holds them once (HDN-312).
     */
    @Query(
        value = """
        SELECT COUNT(*)
        FROM newcomer_visits AS visit
        JOIN newcomer_profiles AS profile ON profile.id = visit.newcomer_profile_id
        JOIN members AS member ON member.id = profile.member_id
        WHERE visit.deleted_at IS NULL
          AND visit.visit_date = :visitDate
          AND visit.definition_id = :definitionId
          AND member.deleted_at IS NULL
          AND member.member_status = 'ACTIVE'
          AND member.group_id = :groupId
          AND NOT EXISTS (
              SELECT 1
              FROM attendance_logs AS attendance_log
              WHERE attendance_log.definition_id = :definitionId
                AND attendance_log.attendance_date = :visitDate
                AND attendance_log.attended = TRUE
                AND attendance_log.deleted_at IS NULL
                AND attendance_log.member_id = profile.member_id
          )
        """,
        nativeQuery = true,
    )
    fun countRosteredQuickRecordsWithoutCheckIn(
        @Param("visitDate") visitDate: LocalDate,
        @Param("definitionId") definitionId: Long,
        @Param("groupId") groupId: Long,
    ): Long

    /**
     * Definitions the visits of [visitDate] are already pinned to, first pinned first (HDN-312).
     * A later visit of the same day joins the same one, whatever was checked in meanwhile.
     */
    @Query(
        """
        SELECT d FROM NewcomerVisit v
        JOIN v.definition d
        WHERE v.deletedAt IS NULL AND v.visitDate = :visitDate AND d.deletedAt IS NULL
        ORDER BY v.id ASC
        """,
    )
    fun findPinnedDefinitionsOn(
        @Param("visitDate") visitDate: LocalDate,
    ): List<AttendanceDefinition>

    /** Retention (#265): visits whose day lies before [cutoff], deleted or not. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM NewcomerVisit v WHERE v.visitDate < :cutoff")
    fun hardDeleteVisitedBefore(
        @Param("cutoff") cutoff: LocalDate,
    ): Int

    /** Soft-deleted visits whose deletion is at or before [cutoff]. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM NewcomerVisit v WHERE v.deletedAt IS NOT NULL AND v.deletedAt <= :cutoff")
    fun hardDeleteSoftDeletedBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
