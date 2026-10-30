package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.Authz
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceSummaryResponse
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/**
 * 장기결석자 (HDN-375): members without a Sunday check-in for at least three calendar months,
 * with contact status and memo of the current absence phase. Pastors and admins only; every
 * response may carry sensitive notes and is never cached. Errors are answered by
 * [LongAbsenceExceptionHandler] so no memo reaches a log.
 */
@RestController
@PreAuthorize(Authz.PASTORAL_CARE)
@RequestMapping("/attendance/long-absences")
class LongAbsenceController(
    private val service: LongAbsenceService,
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
    ): ResponseEntity<ApiResponse<LongAbsenceResponse>> = noStore(service.setMemo(memberPublicId, request))

    private fun <T> noStore(data: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data = data))
}
