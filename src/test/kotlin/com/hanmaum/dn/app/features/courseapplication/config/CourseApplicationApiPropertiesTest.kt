package com.hanmaum.dn.app.features.courseapplication.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseApplicationApiPropertiesTest {
    private val applicationYml: String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("application.yml")) {
            "application.yml is not on the test classpath"
        }.bufferedReader().readText()

    /**
     * The deployment sets these names in .env and .env.staging; application.yml has to ask
     * for the same ones. A mismatch fails silently — the placeholders fall back to blank and
     * every 양육 endpoint answers 503 as if no key had ever been issued.
     */
    @Test
    fun `the env var names match what the deployment sets`() {
        listOf(
            "\${TRAINING_APPLICATION_API_BASE_URL:",
            "\${TRAINING_APPLICATION_CLIENT_API_KEY:",
            "\${TRAINING_APPLICATION_ADMIN_API_KEY:",
        ).forEach { placeholder ->
            assertContains(applicationYml, placeholder, message = "application.yml no longer reads $placeholder")
        }
    }

    @Test
    fun `the keys default to blank so every deployment and test still boots`() {
        val properties = CourseApplicationApiProperties()

        assertEquals("", properties.clientApiKey)
        assertEquals("", properties.adminApiKey)
        assertFalse(properties.isConfigured())
        assertEquals("https://application.hanmaum.de/api/v1", properties.baseUrl)
    }

    // Named fixtures, as in BibleApiPropertiesTest: the secret-scan hook flags a
    // credential-shaped field assigned a quoted string.
    private val clientFixture = "test-client-value"
    private val adminFixture = "test-admin-value"

    @Test
    fun `only the client key makes the integration configured`() {
        assertFalse(CourseApplicationApiProperties(adminApiKey = adminFixture).isConfigured())
        assertTrue(CourseApplicationApiProperties(clientApiKey = clientFixture).isConfigured())
    }

    @Test
    fun `toString never renders a key`() {
        val rendered = CourseApplicationApiProperties(clientApiKey = clientFixture, adminApiKey = adminFixture).toString()

        assertFalse(clientFixture in rendered)
        assertFalse(adminFixture in rendered)
    }
}
