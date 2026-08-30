package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.service.TrainingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
     * GET /api/v1/trainings?activeOnly=false
     * Role: MEMBER — the course list behind the 양육 tab, and the catalog that populates
     * the admin member edit form.
     *
     * activeOnly defaults to false so the admin form keeps seeing the discontinued Kairos
     * courses; the 양육 list passes true.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun listTrainings(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): ResponseEntity<ApiResponse<List<TrainingDto>>> =
        ResponseEntity.ok(ApiResponse.success(data = trainingService.getTrainings(activeOnly)))

    /**
     * GET /api/v1/trainings/{publicId}
     * Role: MEMBER — the 양육 detail page: schedule, location, leader, seat counter,
     * application deadline and who the course is meant for.
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    fun getTraining(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<TrainingDetailDto>> = ResponseEntity.ok(ApiResponse.success(data = trainingService.getTraining(publicId)))

    /**
     * POST /api/v1/trainings/{publicId}/registrations
     * Role: MEMBER — the 신청하기 button. Signs the caller up for the course, never
     * anyone else; admin assignment stays on PUT /members/{publicId}/trainings.
     *
     * 400 when the course is not taking applications or the deadline has passed,
     * 409 when the caller already applied or the course is full.
     */
    @PostMapping("/{publicId}/registrations")
    @PreAuthorize("isAuthenticated()")
    fun register(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<TrainingRegistrationDto>> {
        val registration = trainingService.registerCurrentMember(publicId, authentication.token.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = registration, message = "신청이 완료되었습니다."))
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
