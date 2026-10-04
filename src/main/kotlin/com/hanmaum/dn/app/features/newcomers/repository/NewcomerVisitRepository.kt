package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
}
