package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateGraduationRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.GraduationDto
import com.hanmaum.dn.app.features.members.api.v1.dto.GraduationStateDto
import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import com.hanmaum.dn.app.features.members.service.MemberGraduationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Marks members who have left the DN community, most often on marrying.
 *
 * Admin-only: graduating deactivates a member, and the notes may name other people.
 */
@RestController
@RequestMapping("/members/{publicId}/graduation")
@PreAuthorize("hasRole('ADMIN')")
class MemberGraduationController(
    private val graduationService: MemberGraduationService,
) {
    @PostMapping
    fun graduate(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: CreateGraduationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<GraduationDto>> {
        val graduation =
            graduationService.graduate(
                publicId = publicId,
                graduatedOn = request.graduatedOn!!,
                reason = request.reason!!,
                note = request.note,
                actorSubject = jwt.subject,
            )
        return ResponseEntity.ok(ApiResponse.success(graduation.toDto()))
    }

    @DeleteMapping
    fun reinstate(
        @PathVariable publicId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<GraduationDto>> {
        val graduation = graduationService.reinstate(publicId, jwt.subject)
        return ResponseEntity.ok(ApiResponse.success(graduation.toDto()))
    }

    @GetMapping
    fun get(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<GraduationStateDto>> {
        val current = graduationService.openGraduation(publicId)
        val history = graduationService.history(publicId)
        return ResponseEntity.ok(
            ApiResponse.success(
                GraduationStateDto(
                    graduated = current != null,
                    current = current?.toDto(),
                    history = history.map { it.toDto() },
                ),
            ),
        )
    }
}

private fun MemberGraduation.toDto(): GraduationDto =
    GraduationDto(
        publicId = this.publicId.toString(),
        graduatedOn = this.graduatedOn,
        reason = this.reason,
        note = this.note,
        revertedAt = this.revertedAt,
        open = this.isOpen(),
    )
