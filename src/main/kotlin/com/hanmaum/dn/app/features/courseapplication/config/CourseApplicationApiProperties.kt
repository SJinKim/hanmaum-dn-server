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
 * Both keys default to blank so a deployment — and every test — boots without them. Without
 * [clientApiKey] the 양육 endpoints answer 503, which the app shows as 준비중입니다, rather
 * than a course list without registration status.
 */
@ConfigurationProperties("hanmaum.course-application-api")
data class CourseApplicationApiProperties(
    val baseUrl: String = "https://application.hanmaum.de/api/v1",
    /** The `client` key every call is made with. It only sees applications it created. */
    val clientApiKey: String = "",
    /**
     * The `admin` key, which can read every application (GET /admin/applications). Bound so
     * the deployment carries it, but nothing uses it yet; live remaining seats (#168) may.
     */
    val adminApiKey: String = "",
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
    fun isConfigured(): Boolean = clientApiKey.isNotBlank()

    // The generated toString would print both keys into any log line that renders the
    // properties object.
    override fun toString(): String =
        "CourseApplicationApiProperties(baseUrl=$baseUrl, configured=${isConfigured()}, adminKeySet=${adminApiKey.isNotBlank()})"
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CourseApplicationApiProperties::class)
class CourseApplicationApiConfiguration
