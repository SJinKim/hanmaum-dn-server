package com.hanmaum.dn.app.features.newcomers.repository

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
