package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideEligibleRoomResponse
import com.hanmaum.dn.app.features.guide.service.GuideAreaService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiDocResponse

@RestController
@RequestMapping("/guide/counting-areas")
@PreAuthorize("hasRole('ADMIN') or hasRole('GUIDE_EDITOR')")
class GuideAreaController(
    private val service: GuideAreaService,
) {
    /** Current configuration including inactive areas; reports retain their own immutable membership. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE_VIEWER','GUIDE_EDITOR')")
    @Operation(operationId = "listGuideCountingAreas", summary = "List configured Guide counting areas, including inactive areas.")
    fun list(): ApiResponse<List<GuideAreaResponse>> = ApiResponse.success(service.list())

    /** Select from existing active rooms on active floors; never create duplicate physical rooms. */
    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE_VIEWER','GUIDE_EDITOR')")
    @Operation(operationId = "listGuideEligibleRooms", summary = "List existing church rooms eligible for Guide counting areas.")
    fun rooms(): ApiResponse<List<GuideEligibleRoomResponse>> = ApiResponse.success(service.rooms())

    /** Create a counting area for future reports; multiple areas may reference the same room. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGuideCountingArea", summary = "Add a room-backed counting area for new reports.")
    fun create(
        @Valid @RequestBody request: GuideAreaCreateRequest,
    ): ApiResponse<GuideAreaResponse> = ApiResponse.success(service.create(request))

    /** Update with expected version (409 if stale, 404 if absent); existing report snapshots do not change. */
    @PutMapping("/{publicId}")
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses(
        value = [
            ApiDocResponse(
                responseCode = "404",
                description = "Counting area missing.",
                content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiDocResponse(
                responseCode = "409",
                description = "Counting area version changed; reload before saving.",
                content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @Operation(operationId = "updateGuideCountingArea", summary = "Update label, room, order or activation with expected version.")
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: GuideAreaUpdateRequest,
    ): ApiResponse<GuideAreaResponse> = ApiResponse.success(service.update(publicId, request))
}
