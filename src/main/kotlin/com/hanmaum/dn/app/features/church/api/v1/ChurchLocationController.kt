package com.hanmaum.dn.app.features.church.api.v1

import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.service.ChurchLocationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/church")
class ChurchLocationController(
    private val churchLocationService: ChurchLocationService,
) {
    /** Returns the server-controlled church geofence used by authenticated mobile clients. */
    @GetMapping("/location")
    fun getLocation(): ChurchLocationResponse = churchLocationService.getLocation()
}
