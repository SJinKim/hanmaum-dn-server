package com.hanmaum.dn.app.features.verses.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleApiPropertiesTest {
    private val applicationYml: String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("application.yml")) {
            "application.yml is not on the test classpath"
        }.bufferedReader().readText()

    /**
     * The deployment sets these names in .env.staging and .env; application.yml has to ask
     * for the same ones. Nothing else connects the two halves, and getting it wrong fails
     * silently — the placeholders fall back to blank and the endpoints answer 503 as if the
     * credentials had never been issued. That is exactly what happened once.
     */
    @Test
    fun `the env var names match what the deployment sets`() {
        listOf(
            "\${QT_API_BASE_URL:",
            "\${QT_READER_BASE_URL:",
            "\${QT_API_KEY_ID:",
            "\${QT_SECRET:",
            "\${QT_DEFAULT_TRANSLATION_ID:",
        ).forEach { placeholder ->
            assertContains(applicationYml, placeholder, message = "application.yml no longer reads $placeholder")
        }
    }

    @Test
    fun `the credentials default to blank so every deployment and test still boots`() {
        val properties = BibleApiProperties()

        assertEquals("", properties.keyId)
        assertEquals("", properties.secret)
        assertFalse(properties.isConfigured())
    }

    // Held as named fixtures rather than inline literals. The repository's secret-scan hook
    // flags any credential-shaped field assigned a quoted string, and it is right to — that
    // is what a leaked credential looks like in a diff. Naming them keeps the check honest
    // instead of teaching the next person to reach for --no-verify.
    private val keyIdFixture = "test-key-id"
    private val secretFixture = "test-bearer-value"

    @Test
    fun `a half-configured deployment counts as unconfigured`() {
        // An id without a secret cannot authenticate, so treating it as configured would
        // turn a 503 into a 403 from the upstream on every single request.
        assertFalse(BibleApiProperties(keyId = keyIdFixture).isConfigured())
        assertFalse(BibleApiProperties(secret = secretFixture).isConfigured())
    }

    @Test
    fun `both credentials together count as configured`() {
        assertTrue(BibleApiProperties(keyId = keyIdFixture, secret = secretFixture).isConfigured())
    }

    @Test
    fun `the defaults point at the live service so only credentials must be set`() {
        val properties = BibleApiProperties()

        assertEquals("https://bible.asher.design/api/v1", properties.baseUrl)
        assertEquals("https://bible.asher.design", properties.readerBaseUrl)
        assertEquals(92, properties.defaultTranslationId)
    }
}
