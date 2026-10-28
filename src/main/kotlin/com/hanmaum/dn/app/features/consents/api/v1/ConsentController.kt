package com.hanmaum.dn.app.features.consents.api.v1

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentPolicyResponse
import com.hanmaum.dn.app.features.consents.api.v1.dto.GrantConsentRequest
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentStatus
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentsResponse
import com.hanmaum.dn.app.features.consents.service.ConsentService
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/**
 * Consent to data processing (HDN-358): which policy versions are in force, and the
 * member's own grants. Granting inside another flow (registration, 양육 application) goes
 * through that flow's request; these endpoints cover a standalone consent screen and settings.
 */
@RestController
class ConsentController(
    private val consentService: ConsentService,
) {
    /**
     * GET /api/v1/consents/policies
     * Role: public — the registration screen needs the version before an account exists.
     */
    @GetMapping("/consents/policies")
    fun getPolicies(): ResponseEntity<ApiResponse<List<ConsentPolicyResponse>>> =
        ResponseEntity.ok(ApiResponse.success(data = consentService.policies()))

    /**
     * GET /api/v1/members/me/consents
     * Role: any authenticated member, PENDING included. actionRequired tells the app to
     * prompt before going on, e.g. after the privacy policy changed.
     */
    @GetMapping("/members/me/consents")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(
        responseCode = "404",
        description = "No member profile for this account; the error carries code MEMBER_PROFILE_NOT_FOUND.",
    )
    fun getMyConsents(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<MemberConsentsResponse>> =
        ResponseEntity.ok(ApiResponse.success(data = consentService.status(MemberPrincipal.from(authentication))))

    /**
     * POST /api/v1/members/me/consents
     * Role: any authenticated member. Granting the version already standing is a no-op.
     */
    @PostMapping("/members/me/consents")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "201", description = "Recorded; the purpose's state is now GRANTED.")
    @OpenApiResponse(responseCode = "400", description = "Consent not accepted; the error carries code CONSENT_REQUIRED.")
    @OpenApiResponse(
        responseCode = "409",
        description = "The version shown is no longer in force; code CONSENT_VERSION_OUTDATED. Refetch the policies.",
    )
    fun grant(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: GrantConsentRequest,
    ): ResponseEntity<ApiResponse<MemberConsentStatus>> {
        val status = consentService.grant(MemberPrincipal.from(authentication), request.purpose!!, request.consent!!)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = status, message = "동의가 저장되었습니다."))
    }

    /**
     * DELETE /api/v1/members/me/consents/{purpose}
     * Role: any authenticated member. Idempotent; withdrawing nothing is still a 204.
     */
    @DeleteMapping("/members/me/consents/{purpose}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @OpenApiResponse(responseCode = "204", description = "Withdrawn, or nothing was standing.")
    fun withdraw(
        authentication: JwtAuthenticationToken,
        @PathVariable purpose: ConsentPurpose,
    ) {
        consentService.withdraw(MemberPrincipal.from(authentication), purpose)
    }
}
