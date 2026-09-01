package com.hanmaum.dn.app.features.church.config

import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `the shipped defaults configure the congregation's own geofence`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ChurchLocationConfiguration::class.java)
            .run { context ->
                val properties = context.getBean(ChurchLocationProperties::class.java)
                assertTrue(properties.isConfigured())
                assertEquals(50.1281518, properties.latitude)
                assertEquals(8.5843494, properties.longitude)
                assertEquals(100, properties.radiusMeters)
            }
    }

    /**
     * The 503 on GET /api/v1/church/location is documented in the OpenAPI spec, so it has
     * to be reachable. Clearing the coordinate variables does not do it — the placeholder
     * defaults in application.yml still apply, which an earlier version of this test caught.
     * The explicit switch is the only route, so it is pinned here.
     */
    @Test
    fun `a deployment that switches the geofence off ends up unconfigured`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ChurchLocationConfiguration::class.java)
            .withSystemProperties("HANMAUM_CHURCH_LOCATION_ENABLED=false")
            .run { context ->
                assertTrue(!context.getBean(ChurchLocationProperties::class.java).isConfigured())
            }
    }
}
