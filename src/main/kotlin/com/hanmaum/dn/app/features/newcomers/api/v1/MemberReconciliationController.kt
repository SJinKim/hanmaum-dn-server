package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.NewcomerReadAccess
import com.hanmaum.dn.app.common.security.NewcomerWriteAccess
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.DismissReconciliationRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ReconciliationResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ResolveReconciliationRequest
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.service.MemberReconciliationService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/newcomers/reconciliations")
class MemberReconciliationController(
    private val service: MemberReconciliationService,
) {
    @GetMapping
    @NewcomerReadAccess
    fun list(
        @RequestParam(defaultValue = "OPEN") status: ReconciliationStatus,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<ReconciliationResponse>>> = ResponseEntity.ok(ApiResponse.success(service.list(status, page, size)))

    @GetMapping("/{publicId}")
    @NewcomerReadAccess
    fun get(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<ReconciliationResponse>> = ResponseEntity.ok(ApiResponse.success(service.get(publicId)))

    @PostMapping("/{publicId}/link")
    @NewcomerWriteAccess
    fun link(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: ResolveReconciliationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<ReconciliationResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(service.link(publicId, UUID.fromString(request.memberPublicId), request.version, jwt.subject, false)),
        )

    @PostMapping("/{publicId}/merge")
    @NewcomerWriteAccess
    fun merge(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: ResolveReconciliationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<ReconciliationResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(service.link(publicId, UUID.fromString(request.memberPublicId), request.version, jwt.subject, true)),
        )

    @PostMapping("/{publicId}/dismiss")
    @NewcomerWriteAccess
    fun dismiss(
        @PathVariable publicId: UUID,
        @RequestBody request: DismissReconciliationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<ReconciliationResponse>> =
        ResponseEntity.ok(ApiResponse.success(service.dismiss(publicId, request.version, jwt.subject)))
}
