package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.service.WorshipCountService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/guide/worship-counts")
@PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_EDITOR')")
class WorshipCountController(
    private val service: WorshipCountService,
) {
    /** Paged reports, date sorted; only aggregate guide counts, never personal check-ins. */
    @GetMapping
    @Operation(operationId = "listGuideWorshipCounts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_VIEWER')")
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "serviceDate,desc") sort: String,
    ): ApiResponse<Page<WorshipCountResponse>> = ApiResponse.success(service.list(from, to, page, size, sort))

    /** Read an active summary report by UUID, including live-derived total and calendar-week delta. */
    @GetMapping("/{publicId}")
    @Operation(operationId = "getGuideWorshipCount")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_VIEWER')")
    fun get(
        @PathVariable publicId: UUID,
    ): ApiResponse<WorshipCountResponse> = ApiResponse.success(service.get(publicId))

    /** Create one report per active date. Counts are int32 or null; notes are free text. */
    @PostMapping
    @Operation(operationId = "createGuideWorshipCount")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: WorshipCountRequest,
        actor: JwtAuthenticationToken,
    ): ApiResponse<WorshipCountResponse> = ApiResponse.success(service.create(request, actor.token.subject))

    /** Replace all editable summary fields with expected version; omitted/null counts remain unrecorded. */
    @PatchMapping("/{publicId}")
    @Operation(operationId = "updateGuideWorshipCount")
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: WorshipCountRequest,
        actor: JwtAuthenticationToken,
    ): ApiResponse<WorshipCountResponse> = ApiResponse.success(service.update(publicId, request, actor.token.subject))

    /** Soft-delete an unchanged report; concurrent changes return 409, missing/deleted returns 404. */
    @DeleteMapping("/{publicId}")
    @Operation(operationId = "deleteGuideWorshipCount")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable publicId: UUID,
        @RequestParam version: Long,
        actor: JwtAuthenticationToken,
    ) = service.delete(publicId, version, actor.token.subject)
}
