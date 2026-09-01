package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChurchLocationServiceTest {
    private fun serviceWith(properties: ChurchLocationProperties): Pair<ChurchLocationService, MeterRegistry> {
        val registry = SimpleMeterRegistry()
        return ChurchLocationService(properties, OperationalMetrics(registry)) to registry
    }

    private fun MeterRegistry.churchLocationCount(outcome: String): Double =
        find("hanmaum.church.location.requests").tag("outcome", outcome).counter()?.count() ?: 0.0

    @Test
    fun `getLocation returns configured coordinates and radius`() {
        val (service, registry) =
            serviceWith(
                ChurchLocationProperties(
                    latitude = 51.1234,
                    longitude = 6.5678,
                    radiusMeters = 125,
                ),
            )

        val result = service.getLocation()

        assertEquals(51.1234, result.latitude)
        assertEquals(6.5678, result.longitude)
        assertEquals(125, result.radiusMeters)
        assertEquals(1.0, registry.churchLocationCount("success"))
        assertEquals(0.0, registry.churchLocationCount("failure"))
    }

    @Test
    fun `getLocation returns service unavailable while deployment is unconfigured`() {
        val (service, registry) = serviceWith(ChurchLocationProperties())

        val exception = assertFailsWith<ResponseStatusException> { service.getLocation() }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.statusCode)
        assertEquals(1.0, registry.churchLocationCount("failure"))
        assertEquals(0.0, registry.churchLocationCount("success"))
    }

    /**
     * The switch is the only way a deployment with the shipped defaults can have no
     * geofence, so it has to reach the same 503 — and be counted as a failure, since the
     * app cannot enforce the check-in radius either way.
     */
    @Test
    fun `disabling the geofence reaches the same 503 despite configured coordinates`() {
        val (service, registry) =
            serviceWith(
                ChurchLocationProperties(
                    enabled = false,
                    latitude = 51.1234,
                    longitude = 6.5678,
                    radiusMeters = 125,
                ),
            )

        val exception = assertFailsWith<ResponseStatusException> { service.getLocation() }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.statusCode)
        assertEquals(1.0, registry.churchLocationCount("failure"))
    }
}
