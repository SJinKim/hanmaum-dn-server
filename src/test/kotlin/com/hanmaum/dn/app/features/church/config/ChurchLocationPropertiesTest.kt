package com.hanmaum.dn.app.features.church.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChurchLocationPropertiesTest {
    @Test
    fun `complete valid location is configured`() {
        val properties =
            ChurchLocationProperties(
                latitude = 51.1234,
                longitude = 6.5678,
                radiusMeters = 100,
            )

        assertTrue(properties.isConfigured())
    }

    @Test
    fun `empty location remains unconfigured without breaking application startup`() {
        val properties = ChurchLocationProperties()

        assertTrue(!properties.isConfigured())
    }

    @Test
    fun `partial location is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ChurchLocationProperties(latitude = 51.1234, longitude = 6.5678)
        }
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
