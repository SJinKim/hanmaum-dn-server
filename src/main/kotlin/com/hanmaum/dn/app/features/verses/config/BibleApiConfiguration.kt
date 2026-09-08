package com.hanmaum.dn.app.features.verses.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Access to the congregation's bible API, which backs both verse cards on Home.
 *
 * The credentials live here and never in the app binary: a static bearer secret is
 * extractable from an APK or IPA, and rotating it would break every installed client at
 * once. That is the main reason this proxy exists at all.
 *
 * Both credential fields default to blank so a deployment — and every test — can boot
 * without them. An unconfigured deployment serves an empty verse card rather than failing;
 * see [com.hanmaum.dn.app.features.verses.client.BibleApiClient].
 */
@ConfigurationProperties("hanmaum.bible-api")
data class BibleApiProperties(
    val baseUrl: String = "https://bible.asher.design/api/v1",
    val keyId: String = "",
    val secret: String = "",
    /** 92 is 개역개정, the upstream default and the one the Home card shows. */
    val defaultTranslationId: Int = 92,
    /** Deeplink base for "read on" — the congregation's own reader page, not the API. */
    val readerBaseUrl: String = "https://bible.asher.design",
) {
    fun isConfigured(): Boolean = keyId.isNotBlank() && secret.isNotBlank()
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BibleApiProperties::class)
class BibleApiConfiguration
