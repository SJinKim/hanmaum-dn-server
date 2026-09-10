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
    /**
     * The congregation's own site, not the API.
     *
     * Two things hang off it: the "read on" deeplink for the daily passage, and the weekly
     * verse itself — `_call_weekly.php` lives here rather than under [baseUrl], which is why
     * the endpoint went unfound long enough for the verse to be maintained by hand instead.
     */
    val readerBaseUrl: String = "https://bible.asher.design",
    /**
     * How long a weekly lookup is remembered, in minutes.
     *
     * Long enough that a congregation opening Home costs one request rather than hundreds,
     * short enough that a week published mid-week — the normal case — reaches the card
     * without a deploy.
     */
    val weeklyCacheMinutes: Long = 30,
    /**
     * How many weeks back to look when the running week has no verse published yet.
     *
     * The card then shows the newest verse there is, with the week it belongs to, rather
     * than going blank; the congregation's own homepage does the same. The bound exists so a
     * source that has gone quiet for good costs a fixed number of requests and then stops.
     */
    val weeklyLookbackWeeks: Int = 8,
    /**
     * Connect and read timeouts, in milliseconds.
     *
     * A verse card is decoration on a home screen and the upstream is a small PHP host. With
     * no timeout a hung one holds request threads until the container gives up; failing fast
     * turns that into the already-handled "unavailable" path instead.
     */
    val connectTimeoutMillis: Long = 2_000,
    val readTimeoutMillis: Long = 4_000,
) {
    fun isConfigured(): Boolean = keyId.isNotBlank() && secret.isNotBlank()
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BibleApiProperties::class)
class BibleApiConfiguration
