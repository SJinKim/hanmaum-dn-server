package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.service.ChurchGroupService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/church-groups")
class ChurchGroupController(
    private val churchGroupService: ChurchGroupService,
) {
    /**
     * GET /api/v1/church-groups
     * Role: ADMIN — list all church groups for selection (e.g. the member edit form).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getChurchGroups(): ResponseEntity<ApiResponse<List<ChurchGroupSummaryDto>>> {
        val groups = churchGroupService.getGroups()
        return ResponseEntity.ok(ApiResponse.success(data = groups))
    }
}
