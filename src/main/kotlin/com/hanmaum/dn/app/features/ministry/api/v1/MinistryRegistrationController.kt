package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ReviewMinistryRegistrationRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.SelfRegisterMinistryRequest
import com.hanmaum.dn.app.features.ministry.service.MinistryRegistrationService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping
class MinistryRegistrationController(
    private val service: MinistryRegistrationService,
) {
    @PostMapping("/ministries/{publicId}/registrations")
    @Operation(operationId = "selfRegisterForMinistry")
    @OpenApiResponse(responseCode = "201", description = "Application received and leader notified")
    @PreAuthorize("isAuthenticated()")
    fun apply(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: SelfRegisterMinistryRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<MinistryRegistrationDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(service.apply(publicId, request.selfIntroduction, authentication.token.subject)),
        )

    @GetMapping("/me/ministries")
    @Operation(operationId = "getMyMinistryRegistrations")
    @PreAuthorize("isAuthenticated()")
    fun mine(authentication: JwtAuthenticationToken): ApiResponse<List<MinistryRegistrationDto>> =
        ApiResponse.success(service.mine(authentication.token.subject))

    @GetMapping("/ministries/{publicId}/registrations/me")
    @Operation(operationId = "getMyMinistryRegistration")
    @PreAuthorize("isAuthenticated()")
    fun mineForMinistry(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ApiResponse<MinistryRegistrationDto?> = ApiResponse.success(service.mineForMinistry(publicId, authentication.token.subject))

    @GetMapping("/ministries/{publicId}/applications")
    @Operation(operationId = "getPendingMinistryApplications")
    @PreAuthorize("hasAnyRole('ADMIN', 'MINISTRY_LEADER')")
    fun pending(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ApiResponse<List<ActiveMinistryMemberDto>> =
        ApiResponse.success(service.pendingMembers(publicId, authentication.token.subject, authentication.isAdmin()))

    @PatchMapping("/ministries/{publicId}/applications/{memberPublicId}")
    @Operation(operationId = "reviewMinistryApplication")
    @PreAuthorize("hasAnyRole('ADMIN', 'MINISTRY_LEADER')")
    fun review(
        @PathVariable publicId: UUID,
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: ReviewMinistryRegistrationRequest,
        authentication: JwtAuthenticationToken,
    ): ApiResponse<MinistryRegistrationDto> =
        ApiResponse.success(
            service.review(
                publicId,
                memberPublicId,
                request.decision,
                request.message,
                authentication.token.subject,
                authentication.isAdmin(),
            ),
        )

    private fun JwtAuthenticationToken.isAdmin(): Boolean = authorities.any { it.authority == "ROLE_ADMIN" }
}
