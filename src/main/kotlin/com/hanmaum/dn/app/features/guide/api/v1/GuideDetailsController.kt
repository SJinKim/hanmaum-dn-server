package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsUpdateRequest
import com.hanmaum.dn.app.features.guide.service.GuideDetailsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiDocResponse

@RestController
@RequestMapping("/guide/worship-counts")
@PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_EDITOR')")
class GuideDetailsController(
    private val service: GuideDetailsService,
) {
    /** Read explicit report mode and details; summaries have no invented room/category breakdown. */
    @GetMapping("/{publicId}/details")
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "getGuideWorshipDetails", summary = "Read Guide report mode and detailed counts.")
    @ApiDocResponse(
        responseCode = "404",
        description = "Report missing or soft-deleted.",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_VIEWER')")
    fun get(
        @PathVariable publicId: UUID,
    ): ApiResponse<GuideDetailsResponse> = ApiResponse.success(service.get(publicId))

    /** Create one detail report, normalizing omitted room cells to unknown; never implicitly zero. */
    @PostMapping("/details")
    @Operation(operationId = "createGuideWorshipDetails", summary = "Create detailed Guide counts, including incomplete drafts.")
    @ApiDocResponse(
        responseCode = "409",
        description = "An active report already exists for this date.",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: GuideDetailsCreateRequest,
        actor: JwtAuthenticationToken,
    ): ApiResponse<GuideDetailsResponse> = ApiResponse.success(service.create(request, actor.token.subject))

    /** Atomically replace all base counts with expected version; summary conversion requires an explicit old-count comparison. */
    @PutMapping("/{publicId}/details")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        operationId = "updateGuideWorshipDetails",
        summary = "Atomically replace details with expected version and optional explicit summary conversion.",
    )
    @ApiResponses(
        value = [
            ApiDocResponse(
                responseCode = "404",
                description = "Report missing or soft-deleted.",
                content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiDocResponse(
                responseCode = "409",
                description = "Date/version conflict or explicit matching summary confirmation required.",
                content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: GuideDetailsUpdateRequest,
        actor: JwtAuthenticationToken,
    ): ApiResponse<GuideDetailsResponse> = ApiResponse.success(service.update(publicId, request, actor.token.subject))
}
