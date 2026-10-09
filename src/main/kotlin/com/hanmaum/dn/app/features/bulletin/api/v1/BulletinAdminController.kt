package com.hanmaum.dn.app.features.bulletin.api.v1

import com.hanmaum.dn.app.common.api.ErrorResponse
import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinDefaultsResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionSummary
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.CreateBulletinRequest
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.UpdateBulletinRequest
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.service.BulletinEditionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/** 주보 editing for the admin dashboard (HDN-146). */
@RestController
@RequestMapping("/admin/bulletins")
@PreAuthorize("hasRole('ADMIN')")
class BulletinAdminController(
    private val service: BulletinEditionService,
) {
    /** Newest Sunday first; [status] narrows to one state. */
    @GetMapping
    @Operation(operationId = "listBulletinEditions")
    fun list(
        @RequestParam(required = false) status: BulletinStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<BulletinEditionSummary>>> = ResponseEntity.ok(ApiResponse.success(service.list(status, page, size)))

    /** What a new draft would start with: the next free Sunday and the default service. */
    @GetMapping("/defaults")
    @Operation(operationId = "getBulletinDefaults")
    fun defaults(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
    ): ResponseEntity<ApiResponse<BulletinDefaultsResponse>> {
        val defaults = service.defaults(from)
        val data =
            BulletinDefaultsResponse(
                defaults.serviceDate,
                BulletinServiceResponse.from(defaults.service),
                defaults.sundays,
                defaults.nextFrom,
            )
        return ResponseEntity.ok(ApiResponse.success(data))
    }

    @GetMapping("/{publicId}")
    @Operation(operationId = "getBulletinEdition")
    fun get(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> = ResponseEntity.ok(ApiResponse.success(service.view(publicId)))

    @PostMapping
    @Operation(operationId = "createBulletinEdition")
    @OpenApiResponse(responseCode = "201", description = "Draft created")
    @OpenApiResponse(
        responseCode = "409",
        description = "An edition for that Sunday already exists.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateBulletinRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> {
        val draft = service.createDraft(request.serviceDate, request.servicePublicId, request.copyFrom, authentication.token.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = service.view(draft.publicId), message = "주보 초안이 생성되었습니다."))
    }

    /** Replaces the draft's content. */
    @PutMapping("/{publicId}")
    @Operation(operationId = "updateBulletinEdition")
    @OpenApiResponse(responseCode = "200", description = "Draft saved")
    @OpenApiResponse(
        responseCode = "409",
        description = "Stale version, or the edition is published.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateBulletinRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> =
        ResponseEntity.ok(ApiResponse.success(service.update(publicId, request, authentication.token.subject)))

    @PostMapping("/{publicId}/publish")
    @Operation(operationId = "publishBulletinEdition")
    @OpenApiResponse(responseCode = "200", description = "Published; the first publish assigns the VOL")
    @OpenApiResponse(
        responseCode = "409",
        description = "Already published.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "422",
        description = "Required content is missing (code BULLETIN_INCOMPLETE); fieldErrors names it.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun publish(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> {
        service.publish(publicId, authentication.token.subject)
        return ResponseEntity.ok(ApiResponse.success(data = service.view(publicId), message = "주보가 발행되었습니다."))
    }

    @PostMapping("/{publicId}/withdraw")
    @Operation(operationId = "withdrawBulletinEdition")
    @OpenApiResponse(responseCode = "200", description = "Withdrawn; the VOL stays")
    @OpenApiResponse(
        responseCode = "409",
        description = "Not published.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun withdraw(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinEditionResponse>> {
        service.withdraw(publicId, authentication.token.subject)
        return ResponseEntity.ok(ApiResponse.success(data = service.view(publicId), message = "주보 발행이 취소되었습니다."))
    }

    /** Only a draft that never had a VOL; anything else is withdrawn instead. */
    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteBulletinEdition")
    @OpenApiResponse(responseCode = "204", description = "Draft deleted")
    @OpenApiResponse(
        responseCode = "409",
        description = "The edition is not a never-published draft.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun delete(
        @PathVariable publicId: UUID,
    ) {
        service.delete(publicId)
    }
}
