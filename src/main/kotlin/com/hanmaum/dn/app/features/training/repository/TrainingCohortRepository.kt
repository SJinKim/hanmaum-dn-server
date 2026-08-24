package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.CohortSeries
import com.hanmaum.dn.app.features.training.domain.TrainingCohort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TrainingCohortRepository : JpaRepository<TrainingCohort, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<TrainingCohort>

    fun findAllByTrainingIdAndDeletedAtIsNullOrderBySeriesAscOrdinalAsc(trainingId: Long): List<TrainingCohort>

    /** Lookup on the real identity of an intake — see uq_training_cohort_ordinal. */
    fun findByTrainingIdAndSeriesAndOrdinalAndDeletedAtIsNull(
        trainingId: Long,
        series: CohortSeries,
        ordinal: Int,
    ): Optional<TrainingCohort>
}
