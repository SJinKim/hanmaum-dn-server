package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberMinistriesRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberTrainingsRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMyProfileRequest
import com.hanmaum.dn.app.features.members.service.MemberService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/members")
class MemberController(
    private val memberService: MemberService,
) {
    /**
     * GET /api/v1/members
     * Role: ADMIN — paginated member list with optional search.
     * Default: page=0 size=20 sorted by lastName ASC.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listMembers(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) status: MemberStatus?,
        @RequestParam(required = false) baptism: Baptism?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<MemberSummaryDto>>> {
        val result = memberService.getMembers(search, status, baptism, page, size)
        return ResponseEntity.ok(ApiResponse.success(data = result))
    }

    /**
     * GET /api/v1/members/me
     * Role: MEMBER (any authenticated user) — own profile only.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    fun getMyProfile(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<*>> {
        val profile = memberService.getMemberProfile(jwt.subject, jwt.getClaim("email"))
        return ResponseEntity.ok(ApiResponse.success(data = profile))
    }

    /**
     * PATCH /api/v1/members/me
     * Role: any authenticated member — partial self-update; only phoneNumber and profileImageUrl.
     */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    fun updateMyProfile(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: UpdateMyProfileRequest,
    ): ResponseEntity<ApiResponse<MemberResponse>> {
        val updated = memberService.updateMyProfile(jwt.subject, jwt.getClaim("email"), request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /**
     * GET /api/v1/members/{publicId}
     * Role: ADMIN
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getMember(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<MemberDto>> {
        val member = memberService.getMemberByPublicId(publicId)
        return ResponseEntity.ok(ApiResponse.success(data = member))
    }

    /**
     * POST /api/v1/members
     * Role: ADMIN — admin creates a member record (no Keycloak user, no password).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createMember(
        @Valid @RequestBody request: CreateMemberRequest,
    ): ResponseEntity<ApiResponse<MemberDto>> {
        val created = memberService.createMember(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = created, message = "회원이 등록되었습니다."))
    }

    /**
     * PATCH /api/v1/members/{publicId}
     * Role: ADMIN — partial update; only non-null fields applied.
     * Status transitions: ACTIVE ↔ INACTIVE. Use DELETE to soft-delete.
     */
    @PatchMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateMember(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateMemberRequest,
    ): ResponseEntity<ApiResponse<MemberDto>> {
        val updated = memberService.updateMember(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /**
     * PUT /api/v1/members/{publicId}/trainings
     * Role: ADMIN — replaces the member's entire training set. Returns refreshed detail.
     */
    @PutMapping("/{publicId}/trainings")
    @PreAuthorize("hasRole('ADMIN')")
    fun replaceMemberTrainings(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: ReplaceMemberTrainingsRequest,
    ): ResponseEntity<ApiResponse<MemberDto>> {
        val updated = memberService.replaceMemberTrainings(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /**
     * PUT /api/v1/members/{publicId}/ministries
     * Role: ADMIN — replaces the member's entire ministry assignment set. Returns refreshed detail.
     */
    @PutMapping("/{publicId}/ministries")
    @PreAuthorize("hasRole('ADMIN')")
    fun replaceMemberMinistries(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: ReplaceMemberMinistriesRequest,
    ): ResponseEntity<ApiResponse<MemberDto>> {
        val updated = memberService.replaceMemberMinistries(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /**
     * DELETE /api/v1/members/{publicId}
     * Role: ADMIN — soft delete; sets deletedAt + memberStatus=DELETED (terminal).
     */
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMember(
        @PathVariable publicId: UUID,
    ) {
        memberService.softDeleteMember(publicId)
    }

    /**
     * POST /api/v1/members/register
     * Public (no auth required) — self-registration; creates DB record + Keycloak user.
     * Path is whitelisted in SecurityConfig.
     */
    @PostMapping("/register")
    fun registerMember(
        @Valid @RequestBody request: RegisterMemberRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberService.registerMember(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(message = "등록이 완료되었습니다."))
    }
}
