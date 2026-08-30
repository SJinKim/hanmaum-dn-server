package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class ChurchLocationServiceTest {
    @Test
    fun `getLocation returns configured coordinates and radius`() {
        val service =
            ChurchLocationService(
                ChurchLocationProperties(
                    latitude = 50.1281518,
                    longitude = 8.5843494,
                    radiusMeters = 100,
                ),
            )

        val result = service.getLocation()

        assertEquals(50.1281518, result.latitude)
        assertEquals(8.5843494, result.longitude)
        assertEquals(100, result.radiusMeters)
    }
}
