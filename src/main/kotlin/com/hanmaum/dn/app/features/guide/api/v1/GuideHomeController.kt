package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeResponse
import com.hanmaum.dn.app.features.guide.service.GuideHomeService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiDocResponse

@RestController
@RequestMapping("/guide/worship-counts")
@PreAuthorize("hasAnyRole('ADMIN','GUIDE_VIEWER','GUIDE_EDITOR')")
class GuideHomeController(
    private val service: GuideHomeService,
) {
    /** Read one fresh transaction snapshot; clients must use each report/metric date rather than the wall-clock week. */
    @GetMapping("/home")
    @ApiDocResponse(responseCode = "403", description = "Guide read permission required.")
    @Operation(
        operationId = "getGuideHome",
        summary = "Consistent Guide Home snapshot through the current Sunday in the configured church timezone.",
        description =
            "Viewer/editor/admin/pastor may read. No personal attendance data. " +
                "Empty drafts do not replace the latest confirmed count.",
    )
    fun get(): ResponseEntity<ApiResponse<GuideHomeResponse>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.get()))
}
