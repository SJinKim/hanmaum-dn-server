package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.Authz
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangePage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryPage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsencePhaseResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceSummaryResponse
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceHistoryService
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.CacheControl
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/**
 * 장기결석자 (HDN-375, HDN-379): members without a Sunday check-in or manual return for at least
 * three calendar months, with contact status and memo of the current absence phase, the manual
 * return and the history of closed phases and their changes. Pastors and admins only; every
 * response may carry sensitive notes and is never cached. Errors are answered by
 * [LongAbsenceExceptionHandler] so no memo reaches a log.
 */
@RestController
@PreAuthorize(Authz.PASTORAL_CARE)
@RequestMapping("/attendance/long-absences")
class LongAbsenceController(
    private val service: LongAbsenceService,
    private val history: LongAbsenceHistoryService,
) {
    @GetMapping
    @Operation(operationId = "listLongAbsences", summary = "All current long-term absentees, longest absence first")
    fun list(): ResponseEntity<ApiResponse<List<LongAbsenceResponse>>> = noStore(service.list())

    @GetMapping("/summary")
    @Operation(operationId = "getLongAbsenceSummary", summary = "Memo-free counts of the same list for the home screen")
    fun summary(): ResponseEntity<ApiResponse<LongAbsenceSummaryResponse>> = noStore(service.summary())

    @PutMapping("/{memberPublicId}/contact-status")
    @Operation(operationId = "setLongAbsenceContactStatus", summary = "Set the contact status, optionally with a memo update")
    @OpenApiResponse(responseCode = "200", description = "Follow-up saved")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid body (code LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown or deleted member",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "Phase no longer current (LONG_ABSENCE_NOT_CURRENT) or stale version (LONG_ABSENCE_VERSION_CONFLICT)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun setContactStatus(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: LongAbsenceContactStatusRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<LongAbsenceResponse>> = noStore(service.setContactStatus(memberPublicId, request, jwt.subject))

    @PutMapping("/{memberPublicId}/memo")
    @Operation(operationId = "setLongAbsenceMemo", summary = "Replace or clear the memo of the current phase")
    @OpenApiResponse(responseCode = "200", description = "Follow-up saved")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid body, memo key missing or longer than 2000 (code LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown or deleted member",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "Phase no longer current (LONG_ABSENCE_NOT_CURRENT) or stale version (LONG_ABSENCE_VERSION_CONFLICT)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun setMemo(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: LongAbsenceMemoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<LongAbsenceResponse>> = noStore(service.setMemo(memberPublicId, request, jwt.subject))

    @PutMapping("/{memberPublicId}/return")
    @Operation(
        operationId = "markLongAbsenceReturned",
        summary = "Mark the member of the current phase as back without a check-in; ends the phase",
    )
    @OpenApiResponse(responseCode = "200", description = "Phase ended")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid body (code LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown or deleted member (LONG_ABSENCE_MEMBER_NOT_FOUND)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "Phase no longer current (LONG_ABSENCE_NOT_CURRENT) or stale version (LONG_ABSENCE_VERSION_CONFLICT)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun markReturned(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: LongAbsenceReturnRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<LongAbsenceReturnResponse>> = noStore(service.markReturned(memberPublicId, request, jwt.subject))

    @GetMapping("/history")
    @Operation(
        operationId = "listLongAbsenceHistory",
        summary = "Closed absence phases, newest end first, optionally of one member; no memos",
    )
    @OpenApiResponse(responseCode = "200", description = "History page")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid page, size or member id (LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown or deleted member (LONG_ABSENCE_MEMBER_NOT_FOUND)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun history(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(required = false) memberPublicId: UUID?,
    ): ResponseEntity<ApiResponse<LongAbsenceHistoryPage>> = noStore(history.history(memberPublicId, page, size))

    @GetMapping("/{memberPublicId}/phases/{phasePublicId}")
    @Operation(operationId = "getLongAbsencePhase", summary = "One stored absence phase of the member, with its current memo")
    @OpenApiResponse(responseCode = "200", description = "Phase")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid id (LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown member (LONG_ABSENCE_MEMBER_NOT_FOUND) or phase (LONG_ABSENCE_PHASE_NOT_FOUND)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun phase(
        @PathVariable memberPublicId: UUID,
        @PathVariable phasePublicId: UUID,
    ): ResponseEntity<ApiResponse<LongAbsencePhaseResponse>> = noStore(history.phase(memberPublicId, phasePublicId))

    @GetMapping("/{memberPublicId}/phases/{phasePublicId}/changes")
    @Operation(operationId = "listLongAbsenceChanges", summary = "Contact and memo changes of one phase, newest first")
    @OpenApiResponse(responseCode = "200", description = "Change page")
    @OpenApiResponse(
        responseCode = "400",
        description = "Invalid id, page or size (LONG_ABSENCE_INVALID_REQUEST)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "Unknown member (LONG_ABSENCE_MEMBER_NOT_FOUND) or phase (LONG_ABSENCE_PHASE_NOT_FOUND)",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun changes(
        @PathVariable memberPublicId: UUID,
        @PathVariable phasePublicId: UUID,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ResponseEntity<ApiResponse<LongAbsenceChangePage>> = noStore(history.changes(memberPublicId, phasePublicId, page, size))

    private companion object {
        const val MAX_PAGE_SIZE = LongAbsenceHistoryService.MAX_PAGE_SIZE.toLong()
    }

    private fun <T> noStore(data: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data = data))
}
