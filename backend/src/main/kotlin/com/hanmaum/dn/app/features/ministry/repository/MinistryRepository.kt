package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.Ministry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MinistryRepository : JpaRepository<Ministry, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Ministry>

    /**
     * List ministries, optionally filtering by active flag.
     * Only returns non-deleted records.
     * [active] null → all; true → active only; false → inactive only.
     */
    @Query(
        """
        SELECT m FROM Ministry m
        WHERE m.deletedAt IS NULL
          AND (:active IS NULL OR m.isMinistryActive = :active)
        ORDER BY m.name ASC
        """,
    )
    fun findAllActive(
        @Param("active") active: Boolean?,
    ): List<Ministry>

    fun existsByNameAndDeletedAtIsNull(name: String): Boolean
}
