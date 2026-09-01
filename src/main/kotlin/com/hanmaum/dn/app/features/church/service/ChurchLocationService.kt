package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChurchLocationService(
    private val properties: ChurchLocationProperties,
) {
    fun getLocation(): ChurchLocationResponse {
        if (!properties.isConfigured()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Church location is not configured.")
        }
        return ChurchLocationResponse(
            latitude = requireNotNull(properties.latitude),
            longitude = requireNotNull(properties.longitude),
            radiusMeters = requireNotNull(properties.radiusMeters),
        )
    }
}
