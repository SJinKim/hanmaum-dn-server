package com.hanmaum.dn.app.features.bulletin.api.v1

import com.hanmaum.dn.app.common.api.ErrorResponse
import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSectionTitleResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceRequest
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.UpdateSectionTitleRequest
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.service.BulletinSettingsService
import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/** 주보 설정: services and section titles (HDN-146). */
@RestController
@RequestMapping("/admin/bulletin")
@PreAuthorize("hasRole('ADMIN')")
class BulletinSettingsController(
    private val settings: BulletinSettingsService,
) {
    // ─── Services ─────────────────────────────────────────────────────────────

    @GetMapping("/services")
    @Operation(operationId = "listBulletinServices")
    fun listServices(): ResponseEntity<ApiResponse<List<BulletinServiceResponse>>> =
        ResponseEntity.ok(ApiResponse.success(settings.listServices()))

    @PostMapping("/services")
    @Operation(operationId = "createBulletinService")
    @OpenApiResponse(responseCode = "201", description = "Service created")
    fun createService(
        @Valid @RequestBody request: BulletinServiceRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinServiceResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = settings.createService(request, authentication.token.subject), message = "예배가 추가되었습니다."))

    @PutMapping("/services/{publicId}")
    @Operation(operationId = "updateBulletinService")
    @OpenApiResponse(responseCode = "200", description = "Service updated")
    @OpenApiResponse(
        responseCode = "409",
        description = "The default service cannot lose the default or be deactivated; mark another one as default first.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun updateService(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: BulletinServiceRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinServiceResponse>> =
        ResponseEntity.ok(ApiResponse.success(settings.updateService(publicId, request, authentication.token.subject)))

    /**
     * 200 with the service when an edition still uses it and it was only deactivated,
     * 204 when it was deleted.
     */
    @DeleteMapping("/services/{publicId}")
    @Operation(operationId = "deleteBulletinService")
    @OpenApiResponse(responseCode = "200", description = "In use by an edition, so only deactivated")
    @OpenApiResponse(responseCode = "204", description = "Deleted")
    @OpenApiResponse(
        responseCode = "409",
        description = "The default service cannot be deleted.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun deleteService(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinServiceResponse>> {
        val deactivated = settings.deleteService(publicId, authentication.token.subject)
        return if (deactivated == null) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(ApiResponse.success(data = deactivated, message = "사용 중인 예배라 비활성화했습니다."))
        }
    }

    // ─── Section titles ───────────────────────────────────────────────────────

    @GetMapping("/section-titles")
    @Operation(operationId = "listBulletinSectionTitles")
    fun listSectionTitles(): ResponseEntity<ApiResponse<List<BulletinSectionTitleResponse>>> =
        ResponseEntity.ok(ApiResponse.success(settings.listSectionTitles()))

    /** A blank title goes back to the default one. */
    @PutMapping("/section-titles/{key}")
    @Operation(operationId = "updateBulletinSectionTitle")
    fun updateSectionTitle(
        @PathVariable key: BulletinSectionKey,
        @Valid @RequestBody request: UpdateSectionTitleRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<BulletinSectionTitleResponse>> =
        ResponseEntity.ok(ApiResponse.success(settings.updateSectionTitle(key, request.title, authentication.token.subject)))
}
