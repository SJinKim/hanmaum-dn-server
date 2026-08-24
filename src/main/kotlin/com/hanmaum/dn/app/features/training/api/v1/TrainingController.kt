package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.service.TrainingService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/trainings")
class TrainingController(
    private val trainingService: TrainingService,
) {
    /**
     * GET /api/v1/trainings
     * Role: ADMIN — the training catalog used to populate the member edit form.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listTrainings(): ResponseEntity<ApiResponse<List<TrainingDto>>> {
        val trainings = trainingService.getTrainings()
        return ResponseEntity.ok(ApiResponse.success(data = trainings))
    }

    /**
     * GET /api/v1/trainings/catalog?activeOnly=true
     *
     * The full catalog entry, including the Korean name used as a column header and the
     * sort order the grid's column order comes from.
     *
     * activeOnly defaults to true so selection lists never offer the discontinued Kairos
     * courses; the grid passes false to render archived data.
     */
    @GetMapping("/catalog")
    @PreAuthorize("hasRole('ADMIN')")
    fun catalog(
        @RequestParam(defaultValue = "true") activeOnly: Boolean,
    ): ResponseEntity<ApiResponse<List<TrainingCatalogDto>>> =
        ResponseEntity.ok(ApiResponse.success(data = trainingService.getCatalog(activeOnly)))

    /** GET /api/v1/trainings/{publicId}/cohorts — intakes of one course. */
    @GetMapping("/{publicId}/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    fun cohorts(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<List<TrainingCohortDto>>> =
        ResponseEntity.ok(ApiResponse.success(data = trainingService.getCohorts(publicId)))
}
