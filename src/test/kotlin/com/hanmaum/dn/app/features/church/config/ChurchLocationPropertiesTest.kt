package com.hanmaum.dn.app.features.church.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChurchLocationPropertiesTest {
    @Test
    fun `valid location is accepted`() {
        val properties =
            ChurchLocationProperties(
                latitude = 50.1281518,
                longitude = 8.5843494,
                radiusMeters = 100,
            )

        assertEquals(50.1281518, properties.latitude)
        assertEquals(8.5843494, properties.longitude)
        assertEquals(100, properties.radiusMeters)
    }

    @Test
    fun `invalid coordinates and radius are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ChurchLocationProperties(latitude = 91.0, longitude = 6.5678, radiusMeters = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            ChurchLocationProperties(latitude = 51.1234, longitude = 181.0, radiusMeters = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            ChurchLocationProperties(latitude = 51.1234, longitude = 6.5678, radiusMeters = 0)
        }
    }
}
