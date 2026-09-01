package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChurchLocationService(
    private val properties: ChurchLocationProperties,
    private val operationalMetrics: OperationalMetrics,
) {
    private val log = LoggerFactory.getLogger(ChurchLocationService::class.java)

    /**
     * A deployment without a configured geofence answers 503 rather than inventing a
     * position. Both outcomes are counted: the mobile client enforces the check-in radius
     * itself, so a geofence that goes dark disables attendance check-in in the app without
     * anything else failing. The failure rate is the signal worth alerting on.
     */
    fun getLocation(): ChurchLocationResponse {
        if (!properties.isConfigured()) {
            operationalMetrics.recordChurchLocationRequest(OperationOutcome.FAILURE)
            log.warn(
                "Church location requested but unavailable enabled={} hasCoordinates={}",
                properties.enabled,
                properties.latitude != null && properties.longitude != null && properties.radiusMeters != null,
            )
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Church location is not configured.")
        }
        operationalMetrics.recordChurchLocationRequest(OperationOutcome.SUCCESS)
        return ChurchLocationResponse(
            latitude = requireNotNull(properties.latitude),
            longitude = requireNotNull(properties.longitude),
            radiusMeters = requireNotNull(properties.radiusMeters),
        )
    }
}
