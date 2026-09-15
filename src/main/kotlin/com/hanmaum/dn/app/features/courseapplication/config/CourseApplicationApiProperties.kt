package com.hanmaum.dn.app.features.courseapplication.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Access to application.hanmaum.de, where 양육 applications are actually recorded.
 *
 * The key lives here and never in the app binary: a static bearer key is extractable from
 * an APK or IPA, and it lets the holder create applications in anyone's name. That is the
 * reason the app goes through this server instead of calling the API itself.
 *
 * [apiKey] defaults to blank so a deployment — and every test — boots without it. An
 * unconfigured deployment answers 503 on the 양육 endpoints, which the app shows as
 * 준비중입니다, rather than a course list without registration status.
 */
@ConfigurationProperties("hanmaum.course-application-api")
data class CourseApplicationApiProperties(
    val baseUrl: String = "https://application.hanmaum.de/api/v1",
    /** A `client` key. The `admin` key is not needed for anything this server does. */
    val apiKey: String = "",
    /**
     * Connect and read timeouts, in milliseconds.
     *
     * The upstream is a small PHP host. Without a timeout a hung one holds request threads
     * until the container gives up; failing fast takes the handled 503 path instead.
     */
    val connectTimeoutMillis: Long = 2_000,
    val readTimeoutMillis: Long = 5_000,
    /**
     * How long the course list is remembered, in seconds.
     *
     * Every member opening the 양육 tab asks the identical question. Short enough that a
     * registration window opening or closing reaches the app within a minute.
     */
    val courseCacheSeconds: Long = 60,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    // The generated toString would print the key into any log line that renders the
    // properties object.
    override fun toString(): String = "CourseApplicationApiProperties(baseUrl=$baseUrl, configured=${isConfigured()})"
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CourseApplicationApiProperties::class)
class CourseApplicationApiConfiguration
