package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TrainingRepository : JpaRepository<Training, Long> {
    fun findAllByDeletedAtIsNullOrderBySortOrderAsc(): List<Training>

    fun findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(): List<Training>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Training>

    /** Primary lookup for the importer: sheet name maps to a code, code maps to a row. */
    fun findByCodeAndDeletedAtIsNull(code: TrainingCode): Optional<Training>

    /**
     * Active trainings with their aliases, for matching external courses. Fetched in one
     * query because matching reads the aliases of every row, outside any transaction.
     */
    @Query(
        """
        SELECT DISTINCT t FROM Training t
        LEFT JOIN FETCH t.aliases
        WHERE t.isActive = true
          AND t.deletedAt IS NULL
        ORDER BY t.sortOrder ASC
        """,
    )
    fun findActiveWithAliases(): List<Training>

    @Query(
        """
        SELECT t FROM Training t
        LEFT JOIN FETCH t.aliases
        WHERE t.publicId = :publicId
          AND t.deletedAt IS NULL
        """,
    )
    fun findWithAliasesByPublicId(
        @Param("publicId") publicId: UUID,
    ): Training?
}
