package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChurchLocationServiceTest {
    @Test
    fun `getLocation returns configured coordinates and radius`() {
        val service =
            ChurchLocationService(
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
    }

    @Test
    fun `getLocation returns service unavailable while deployment is unconfigured`() {
        val exception =
            assertFailsWith<ResponseStatusException> {
                ChurchLocationService(ChurchLocationProperties()).getLocation()
            }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.statusCode)
    }
}
