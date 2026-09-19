package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.NewcomerReadAccess
import com.hanmaum.dn.app.common.security.NewcomerWriteAccess
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateFormLinkRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.FormLinkResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicFormMetadataResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicNewcomerSubmissionRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicSubmissionResponse
import com.hanmaum.dn.app.features.newcomers.service.NewcomerFormService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/newcomer-form-links")
class NewcomerFormLinkAdminController(
    private val service: NewcomerFormService,
) {
    @PostMapping
    @NewcomerWriteAccess
    fun create(
        @RequestBody request: CreateFormLinkRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<FormLinkResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createLink(request, jwt.subject)))

    @GetMapping
    @NewcomerReadAccess
    fun list(): ResponseEntity<ApiResponse<List<FormLinkResponse>>> = ResponseEntity.ok(ApiResponse.success(service.listLinks()))

    @PostMapping("/{publicId}/revoke")
    @NewcomerWriteAccess
    fun revoke(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<FormLinkResponse>> = ResponseEntity.ok(ApiResponse.success(service.revoke(publicId)))
}

@RestController
@RequestMapping("/newcomer-forms")
class PublicNewcomerFormController(
    private val service: NewcomerFormService,
) {
    @GetMapping("/{token}")
    fun metadata(
        @PathVariable token: String,
    ): ResponseEntity<ApiResponse<PublicFormMetadataResponse>> = ResponseEntity.ok(ApiResponse.success(service.metadata(token)))

    @PostMapping("/{token}/submissions")
    fun submit(
        @PathVariable token: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PublicNewcomerSubmissionRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<PublicSubmissionResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(service.submit(token, idempotencyKey, servletRequest.remoteAddr, request)),
        )
}
