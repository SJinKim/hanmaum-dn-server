package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.repository.TrainingCohortRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.ErrorResponseException
import java.util.UUID

/**
 * The training catalog as stored.
 *
 * The 양육 list, detail page and application live in
 * [com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationService]: their
 * registration state comes from application.hanmaum.de, not from these rows.
 */
@Service
class TrainingService(
    private val trainingRepository: TrainingRepository,
    private val cohortRepository: TrainingCohortRepository,
) {
    /**
     * The training catalog, ordered by progression (sort order), without registration state
     * from the external API.
     *
     * [activeOnly] defaults to false so the admin member-edit form keeps seeing the
     * discontinued Kairos courses it has always seen.
     */
    @Transactional(readOnly = true)
    fun getTrainings(activeOnly: Boolean = false): List<TrainingDto> = findCatalogEntries(activeOnly).map { it.toDto() }

    /**
     * The catalog with the fields the admin grid needs for its columns.
     *
     * [activeOnly] exists because KAIROS and KAIROS_FT are discontinued and kept only so
     * archived records have a valid course to point at. Selection lists must not offer
     * them; the grid still shows them where data exists.
     */
    @Transactional(readOnly = true)
    fun getCatalog(activeOnly: Boolean): List<TrainingCatalogDto> =
        findCatalogEntries(activeOnly).map { training ->
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

    @Transactional(readOnly = true)
    fun getCohorts(trainingPublicId: UUID): List<TrainingCohortDto> {
        val training = requireTraining(trainingPublicId)
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

    private fun findCatalogEntries(activeOnly: Boolean): List<Training> =
        if (activeOnly) {
            trainingRepository.findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc()
        } else {
            trainingRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc()
        }

    private fun requireTraining(publicId: UUID): Training =
        trainingRepository.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow {
            ErrorResponseException(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No training with that id exists."),
                null,
            )
        }

    companion object {
        /**
         * The statuses that occupy a seat in the run a course is currently offering.
         *
         * Historical participation an admin records lands on COMPLETED, DROPPED,
         * IN_PROGRESS or UNKNOWN. Counting those would show "436 / 12명" on a course that
         * has been taught for years, so the seat counter only sees people who signed up
         * for the run that is open now.
         *
         * The trade-off: rows left at ENROLLED after a run finishes keep counting until
         * an admin moves them on. Acceptable while one course has one open run — a course
         * running two concurrent intakes needs the offering on its own table.
         */
        val REGISTERED_STATUSES = setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED)
    }
}
