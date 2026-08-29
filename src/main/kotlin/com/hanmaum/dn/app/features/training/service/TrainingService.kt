package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.repository.TrainingCohortRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.ErrorResponseException
import java.util.UUID

@Service
class TrainingService(
    private val trainingRepository: TrainingRepository,
    private val cohortRepository: TrainingCohortRepository,
) {
    /** The training catalog, ordered by progression (sort order). */
    @Transactional(readOnly = true)
    fun getTrainings(): List<TrainingDto> = trainingRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().map { it.toDto() }

    /**
     * The catalog with the fields the admin grid needs for its columns.
     *
     * [activeOnly] exists because KAIROS and KAIROS_FT are discontinued and kept only so
     * archived records have a valid course to point at. Selection lists must not offer
     * them; the grid still shows them where data exists.
     */
    @Transactional(readOnly = true)
    fun getCatalog(activeOnly: Boolean): List<TrainingCatalogDto> {
        val entries =
            if (activeOnly) {
                trainingRepository.findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc()
            } else {
                trainingRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc()
            }
        return entries.map { training ->
            TrainingCatalogDto(
                publicId = training.publicId.toString(),
                code = training.code.name,
                name = training.name,
                nameKo = training.nameKo,
                category = training.category?.name,
                sortOrder = training.sortOrder,
                hasCohorts = training.hasCohorts,
                isActive = training.isActive,
                prerequisiteCode = training.prerequisite?.code?.name,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getCohorts(trainingPublicId: UUID): List<TrainingCohortDto> {
        val training =
            trainingRepository.findByPublicIdAndDeletedAtIsNull(trainingPublicId).orElseThrow {
                ErrorResponseException(
                    HttpStatus.NOT_FOUND,
                    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No training with that id exists."),
                    null,
                )
            }
        return cohortRepository
            .findAllByTrainingIdAndDeletedAtIsNullOrderBySeriesAscOrdinalAsc(training.id!!)
            .map { cohort ->
                TrainingCohortDto(
                    publicId = cohort.publicId.toString(),
                    series = cohort.series.name,
                    ordinal = cohort.ordinal,
                    label = cohort.label,
                    cohortYear = cohort.cohortYear,
                    term = cohort.term?.name,
                    startedOn = cohort.startedOn,
                    endedOn = cohort.endedOn,
                )
            }
    }
}
