package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import org.springframework.data.jpa.repository.JpaRepository
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
}
