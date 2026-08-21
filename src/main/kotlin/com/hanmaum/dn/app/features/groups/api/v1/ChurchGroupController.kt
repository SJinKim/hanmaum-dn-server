package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.groups.api.v1.dto.AssignGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.service.ChurchGroupService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/church-groups")
class ChurchGroupController(
    private val churchGroupService: ChurchGroupService,
) {
    /**
     * GET /api/v1/church-groups
     * Role: ADMIN — list all church groups for selection (e.g. the member edit form).
     * Each entry carries the group's current leader, or nulls while the group has none.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getChurchGroups(): ResponseEntity<ApiResponse<List<ChurchGroupSummaryDto>>> {
        val groups = churchGroupService.getGroups()
        return ResponseEntity.ok(ApiResponse.success(data = groups))
    }

    /**
     * PUT /api/v1/church-groups/{publicId}/leader
     * Role: ADMIN — makes the given member the group's current leader.
     *
     * The sitting leader's tenure is closed as of the new start date; past tenures are retained.
     * The member must already belong to the group. Re-sending the current leader is a no-op.
     */
    @PutMapping("/{publicId}/leader")
    @PreAuthorize("hasRole('ADMIN')")
    fun assignLeader(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: AssignGroupLeaderRequest,
    ): ResponseEntity<ApiResponse<ChurchGroupSummaryDto>> {
        val updated = churchGroupService.assignLeader(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated, message = "그룹 리더가 지정되었습니다."))
    }

    /**
     * DELETE /api/v1/church-groups/{publicId}/leader
     * Role: ADMIN — ends the group's current leader tenure, leaving the group without a 순장.
     *
     * Past tenures are retained. A group that already has no sitting leader is a no-op 200
     * so retries and double-unchecks cannot 404.
     */
    @DeleteMapping("/{publicId}/leader")
    @PreAuthorize("hasRole('ADMIN')")
    fun clearLeader(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<ChurchGroupSummaryDto>> {
        val updated = churchGroupService.clearLeader(publicId)
        return ResponseEntity.ok(ApiResponse.success(data = updated, message = "그룹 리더가 해제되었습니다."))
    }
}
