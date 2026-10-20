package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticsResponse
import com.hanmaum.dn.app.features.guide.service.GuideStatisticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ProblemDetail
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiDocResponse

@RestController
@RequestMapping("/guide/worship-counts/statistics")
@PreAuthorize("hasAnyRole('ADMIN','GUIDE_VIEWER','GUIDE_EDITOR')")
class GuideStatisticsController(
    private val service: GuideStatisticsService,
) {
    /** Read calendar-period statistics, including incomplete-report coverage, without member/check-in access. */
    @GetMapping
    @ApiDocResponse(responseCode = "200", description = "Statistics from the selected calendar periods.")
    @Operation(operationId = "getGuideWorshipStatistics", summary = "Guide annual statistics and comparable prior-year periods.")
    @ApiDocResponse(
        responseCode = "400",
        description = "Invalid year, cutoff, or default cutoff for a future year.",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiDocResponse(
        responseCode = "403",
        description = "Guide read permission required.",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun get(
        @Parameter(schema = Schema(minimum = "2", maximum = "9999"))
        @RequestParam year: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOf: LocalDate?,
    ): ApiResponse<GuideStatisticsResponse> = ApiResponse.success(service.statistics(year, asOf))
}
