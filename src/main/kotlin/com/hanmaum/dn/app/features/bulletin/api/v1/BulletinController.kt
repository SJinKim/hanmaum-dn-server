package com.hanmaum.dn.app.features.bulletin.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionSummary
import com.hanmaum.dn.app.features.bulletin.service.BulletinEditionService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/** Visible published 주보 for members; Sunday editions open Friday in Europe/Berlin. */
@RestController
@RequestMapping("/bulletins")
@PreAuthorize("isAuthenticated()")
class BulletinController(
    private val service: BulletinEditionService,
) {
    /** Latest visible edition; 404 is the normal empty state when none is available. */
    @GetMapping("/current")
    @Operation(
        operationId = "getCurrentBulletin",
        description =
            "Latest published edition with serviceDate <= today in Europe/Berlin plus 2 calendar days. " +
                "Sunday editions open Friday at 00:00; older editions remain available.",
    )
    @OpenApiResponse(responseCode = "200", description = "The current published edition")
    @OpenApiResponse(responseCode = "404", description = "No published edition within the visibility window")
    fun current(): ResponseEntity<ApiResponse<BulletinEditionResponse>> {
        val edition = service.currentView() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No bulletin published")
        return ResponseEntity.ok(ApiResponse.success(edition))
    }

    @GetMapping
    @Operation(
        operationId = "getBulletinByDate",
        description =
            "Published edition for a Sunday within today in Europe/Berlin plus 2 calendar days. " +
                "Later Sundays are hidden even when already published.",
    )
    @OpenApiResponse(responseCode = "404", description = "No visible published edition for that Sunday")
    fun byDate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> = ResponseEntity.ok(ApiResponse.success(service.publishedView(date)))

    /** Visible published editions, newest first. */
    @GetMapping("/history")
    @Operation(
        operationId = "listBulletinHistory",
        description =
            "Published editions with serviceDate <= today in Europe/Berlin plus 2 calendar days, newest first. " +
                "The visibility filter applies before pagination and totals.",
    )
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<BulletinEditionSummary>>> = ResponseEntity.ok(ApiResponse.success(service.history(page, size)))
}
