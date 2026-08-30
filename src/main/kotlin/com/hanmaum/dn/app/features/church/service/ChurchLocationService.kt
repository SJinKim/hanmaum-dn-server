package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import org.springframework.stereotype.Service

@Service
class ChurchLocationService(
    private val properties: ChurchLocationProperties,
) {
    fun getLocation(): ChurchLocationResponse =
        ChurchLocationResponse(
            latitude = properties.latitude,
            longitude = properties.longitude,
            radiusMeters = properties.radiusMeters,
        )
}
