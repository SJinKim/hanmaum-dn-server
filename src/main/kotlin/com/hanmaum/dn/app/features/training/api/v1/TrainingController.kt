package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.api.ErrorResponse
import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationService
import com.hanmaum.dn.app.features.training.api.v1.dto.MyTrainingApplicationDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingApplicationRequest
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.service.TrainingService
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping("/trainings")
class TrainingController(
    private val trainingService: TrainingService,
    private val courseApplicationService: CourseApplicationService,
) {
    /**
     * GET /api/v1/trainings?activeOnly=false
     * Role: MEMBER — the course list behind the 양육 tab, and the catalog that populates
     * the admin member edit form.
     *
     * activeOnly=true is the 양육 list: active trainings that have at least one course in
     * application.hanmaum.de, those open for the caller first, each with its registration
     * window and the caller's own application. 503 when that API cannot be reached.
     *
     * activeOnly defaults to false, the stored catalog the admin form has always seen,
     * discontinued Kairos courses included and without asking the external API.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "200", description = "The training list.")
    @OpenApiResponse(
        responseCode = "503",
        description = "activeOnly=true only: application.hanmaum.de could not be reached (code COURSE_APPLICATION_UNAVAILABLE).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun listTrainings(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<List<TrainingDto>>> {
        val trainings =
            if (activeOnly) {
                courseApplicationService.listTrainings(authentication.token.subject)
            } else {
                trainingService.getTrainings(activeOnly = false)
            }
        return ResponseEntity.ok(ApiResponse.success(data = trainings))
    }

    /**
     * GET /api/v1/trainings/{publicId}
     * Role: MEMBER — the 양육 detail page: schedule, location, leader, who the course is
     * meant for, the courses open for application right now, the caller's own application
     * and the data the application form starts from.
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "200", description = "The training detail.")
    @OpenApiResponse(
        responseCode = "404",
        description = "No training with that id.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "503",
        description = "application.hanmaum.de could not be reached (code COURSE_APPLICATION_UNAVAILABLE).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun getTraining(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<TrainingDetailDto>> =
        ResponseEntity.ok(
            ApiResponse.success(data = courseApplicationService.getTrainingDetail(publicId, authentication.token.subject)),
        )

    /**
     * POST /api/v1/trainings/{publicId}/registrations
     * Role: MEMBER — the 신청하기 button. Applies the caller, never anyone else, to the
     * chosen external course through application.hanmaum.de and records it here.
     *
     * Safe to retry: a repeat for the same course returns the application already made
     * instead of creating a second one.
     */
    @PostMapping("/{publicId}/registrations")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "201", description = "Applied, or the existing application for a retry.")
    @OpenApiResponse(
        responseCode = "400",
        description =
            "A required applicant field is missing from both the request and the profile " +
                "(code COURSE_APPLICATION_INVALID, fieldErrors).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "403",
        description = "The caller may not apply to this course, e.g. a 여자반 (code COURSE_APPLICATION_NOT_ELIGIBLE).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "No such training, or the course does not belong to it (code COURSE_APPLICATION_COURSE_NOT_FOUND).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description =
            "Registration closed (COURSE_APPLICATION_CLOSED), course full (COURSE_APPLICATION_FULL), " +
                "or already applied to or taking this training (COURSE_APPLICATION_ALREADY_APPLIED).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "422",
        description = "Rejected by application.hanmaum.de (code COURSE_APPLICATION_INVALID, fieldErrors).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "503",
        description = "application.hanmaum.de could not be reached (code COURSE_APPLICATION_UNAVAILABLE).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun register(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: TrainingApplicationRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<TrainingRegistrationDto>> {
        val registration = courseApplicationService.apply(publicId, authentication.token.subject, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = registration, message = "신청이 완료되었습니다."))
    }

    /**
     * DELETE /api/v1/trainings/{publicId}/registrations
     * Role: MEMBER — the 신청 취소 button. Cancels the caller's latest application to this
     * training at application.hanmaum.de, then marks the participation DROPPED here.
     *
     * The app sends no application id; the server resolves the caller's own. Safe to retry:
     * a repeat answers 200 with the cancelled application. Applying again afterwards creates
     * a new application.
     */
    @DeleteMapping("/{publicId}/registrations")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "200", description = "Cancelled, or already cancelled by an earlier call.")
    @OpenApiResponse(
        responseCode = "404",
        description = "No such training, or the caller has no application to it (code COURSE_APPLICATION_NOT_FOUND).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "The caller already completed this training (code COURSE_APPLICATION_NOT_CANCELLABLE).",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "503",
        description =
            "application.hanmaum.de could not be reached (code COURSE_APPLICATION_UNAVAILABLE); " +
                "nothing was changed.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun cancelRegistration(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<MyTrainingApplicationDto>> =
        ResponseEntity.ok(
            ApiResponse.success(
                data = courseApplicationService.cancel(publicId, authentication.token.subject),
                message = "신청이 취소되었습니다.",
            ),
        )

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
