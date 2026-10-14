package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.Authz
import com.hanmaum.dn.app.features.groups.api.v1.dto.NextGroupLeaderResponse
import com.hanmaum.dn.app.features.groups.api.v1.dto.TrainingMatrixMemberDto
import com.hanmaum.dn.app.features.groups.api.v1.dto.UpdateNextGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.service.TrainingMatrixService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/** The 양육 screen's scoped alternative to the administrative member endpoints. */
@RestController
@RequestMapping("/church-groups/members")
@PreAuthorize("hasRole('ADMIN')")
class TrainingMatrixController(
    private val service: TrainingMatrixService,
) {
    /** Reads only active members' matrix fields. Pagination: page >= 0, size between 1 and 10000. */
    @GetMapping
    @PreAuthorize(Authz.TRAINING_READ)
    @Operation(
        operationId = "listTrainingMatrixMembers",
        description = "ACTIVE members only, sorted by name; exposes only the fields needed by the 양육 matrix.",
    )
    @OpenApiResponse(responseCode = "400", description = "Page must be non-negative and size between 1 and 10000.")
    fun getMembers(
        @Parameter(schema = Schema(minimum = "0"))
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(schema = Schema(minimum = "1", maximum = "10000"))
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<TrainingMatrixMemberDto>>> = ResponseEntity.ok(ApiResponse.success(service.getMembers(page, size)))

    /** Sets 예비순장 for an active member; this does not appoint a sitting church-group leader. */
    @PatchMapping("/{memberPublicId}/next-leader")
    @PreAuthorize(Authz.TRAINING_WRITE)
    @Operation(operationId = "setNextGroupLeaderFlag")
    @OpenApiResponse(responseCode = "400", description = "An explicit non-null isNextGroupLeader value is required.")
    @OpenApiResponse(responseCode = "404", description = "The member does not exist, is deleted or is not ACTIVE.")
    fun setNextGroupLeader(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: UpdateNextGroupLeaderRequest,
    ): ResponseEntity<ApiResponse<NextGroupLeaderResponse>> =
        ResponseEntity.ok(ApiResponse.success(service.setNextGroupLeader(memberPublicId, requireNotNull(request.isNextGroupLeader))))
}
