package com.hanmaum.dn.app.features.church.api.v1

import com.hanmaum.dn.app.common.api.ErrorResponse
import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.service.ChurchLocationService
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping("/church")
class ChurchLocationController(
    private val churchLocationService: ChurchLocationService,
) {
    /**
     * Returns the server-controlled church geofence used by authenticated mobile clients.
     *
     * Both responses are declared because a deployment may legitimately have no geofence
     * configured: the coordinates are no longer shipped as defaults, so an environment that
     * does not set them serves 503 rather than a made-up location. Declaring 200 explicitly
     * is not redundant — springdoc drops the inferred default response as soon as one
     * response is declared by hand, which would strip the success schema from the spec.
     */
    @GetMapping("/location")
    @OpenApiResponse(
        responseCode = "200",
        description = "The configured church geofence.",
        content = [Content(schema = Schema(implementation = ChurchLocationResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "503",
        description = "This deployment has no church location configured. Clients should hide geofenced features rather than retry.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun getLocation(): ChurchLocationResponse = churchLocationService.getLocation()
}
