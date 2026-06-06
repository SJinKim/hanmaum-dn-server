package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.service.MinistryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/ministries")
class MinistryController(
    private val ministryService: MinistryService,
) {
    /**
     * POST /api/v1/ministries
     * Role: ADMIN — create a new ministry.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createMinistry(
        @Valid @RequestBody request: CreateMinistryRequest,
    ): ResponseEntity<ApiResponse<MinistryDto>> {
        val created = ministryService.createMinistry(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = created, message = "부서가 생성되었습니다."))
    }

    /**
     * GET /api/v1/ministries
     * Role: MEMBER — list ministries; optional ?active=true|false filter.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getMinistries(
        @RequestParam(required = false) active: Boolean?,
    ): ResponseEntity<ApiResponse<List<MinistrySummaryDto>>> {
        val ministries = ministryService.getMinistries(active)
        return ResponseEntity.ok(ApiResponse.success(data = ministries))
    }

    /**
     * GET /api/v1/ministries/{publicId}
     * Role: MEMBER — full detail including leader info.
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    fun getMinistry(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<MinistryDto>> {
        val ministry = ministryService.getMinistry(publicId)
        return ResponseEntity.ok(ApiResponse.success(data = ministry))
    }

    /**
     * PATCH /api/v1/ministries/{publicId}
     * Role: ADMIN — partial update.
     */
    @PatchMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateMinistry(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateMinistryRequest,
    ): ResponseEntity<ApiResponse<MinistryDto>> {
        val updated = ministryService.updateMinistry(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    /**
     * DELETE /api/v1/ministries/{publicId}
     * Role: ADMIN — deactivate ministry (isActive=false, not hard delete).
     */
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateMinistry(
        @PathVariable publicId: UUID,
    ) {
        ministryService.deactivateMinistry(publicId)
    }
}
