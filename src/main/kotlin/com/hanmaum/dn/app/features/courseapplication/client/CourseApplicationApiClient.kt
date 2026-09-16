package com.hanmaum.dn.app.features.courseapplication.client

import com.hanmaum.dn.app.features.courseapplication.config.CourseApplicationApiProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The external API could not be asked: unreachable, unconfigured, a 5xx, a rejected key, or
 * a body this client cannot read.
 *
 * An infrastructure fault, never the applicant's. It surfaces as 503 and the app shows
 * 준비중입니다.
 */
class CourseApplicationApiUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The external API answered and said no — `capacity_full`, `registration_closed`,
 * `validation_failed`, and so on.
 *
 * [code] is the API's stable error code; [message] is its Korean prose, written for the
 * applicant. [fieldErrors] is filled for `validation_failed`, keyed by field name.
 */
class CourseApplicationApiRejectedException(
    val httpStatus: Int,
    val code: String,
    message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) : RuntimeException(message)

/** application.hanmaum.de, as much of it as the 양육 proxy needs. */
interface CourseApplicationApiClient {
    /** All courses the API offers, across every department. */
    fun listCourses(): List<ExternalCourse>

    /** Creates an application, or returns the existing one when the id was sent before. */
    fun createApplication(request: ExternalCreateApplicationRequest): ExternalCreatedApplication

    /** The application created with [clientApplicationId] by this key, or null if there is none. */
    fun findApplicationByClientId(clientApplicationId: UUID): ExternalApplication?

    /**
     * Cancels an application. The API keeps the row as `cancelled` rather than deleting it,
     * and answers the same for a repeat.
     */
    fun cancelApplication(externalApplicationId: Long): ExternalApplication
}

private fun timeoutedBuilder(properties: CourseApplicationApiProperties): RestClient.Builder =
    RestClient.builder().requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        },
    )

@Component
class HttpCourseApplicationApiClient(
    private val properties: CourseApplicationApiProperties,
    // Defaulted rather than injected, as in HttpBibleApiClient: spring-boot-starter-webmvc
    // contributes no RestClient.Builder bean, and a test binding a mock server keeps the
    // request factory it set.
    restClientBuilder: RestClient.Builder = timeoutedBuilder(properties),
) : CourseApplicationApiClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = restClientBuilder.clone().baseUrl(properties.baseUrl).build()

    private class CachedCourses(
        val courses: List<ExternalCourse>,
        val fetchedAtNanos: Long,
    )

    /**
     * The course list, remembered briefly. A failure is not cached, so an outage recovers on
     * the next request instead of being frozen in.
     */
    private val cachedCourses = AtomicReference<CachedCourses?>(null)

    private class Outcome<T>(
        val status: Int,
        val envelope: ExternalEnvelope<T>?,
    )

    override fun listCourses(): List<ExternalCourse> {
        val ttlNanos = TimeUnit.SECONDS.toNanos(properties.courseCacheSeconds)
        cachedCourses
            .get()
            ?.takeIf { System.nanoTime() - it.fetchedAtNanos < ttlNanos }
            ?.let { return it.courses }

        val outcome =
            send(COURSES_PATH, object : ParameterizedTypeReference<ExternalEnvelope<List<ExternalCourse>>>() {}) {
                restClient.get().uri(COURSES_PATH)
            }
        val courses = requireData(outcome, COURSES_PATH)
        cachedCourses.set(CachedCourses(courses, System.nanoTime()))
        return courses
    }

    override fun createApplication(request: ExternalCreateApplicationRequest): ExternalCreatedApplication {
        val outcome =
            send(APPLICATIONS_PATH, object : ParameterizedTypeReference<ExternalEnvelope<ExternalApplication>>() {}) {
                restClient
                    .post()
                    .uri(APPLICATIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
            }
        val application = requireData(outcome, APPLICATIONS_PATH)
        return ExternalCreatedApplication(application, outcome.envelope?.meta?.idempotentReplay ?: false)
    }

    override fun findApplicationByClientId(clientApplicationId: UUID): ExternalApplication? {
        val outcome =
            send(BY_CLIENT_ID_PATH, object : ParameterizedTypeReference<ExternalEnvelope<ExternalApplication>>() {}) {
                restClient.get().uri("$APPLICATIONS_PATH/by-client-id/{id}", clientApplicationId)
            }
        if (outcome.status == 404 && outcome.envelope?.error?.code == APPLICATION_NOT_FOUND) return null
        return requireData(outcome, BY_CLIENT_ID_PATH)
    }

    override fun cancelApplication(externalApplicationId: Long): ExternalApplication {
        val outcome =
            send(APPLICATION_PATH, object : ParameterizedTypeReference<ExternalEnvelope<ExternalApplication>>() {}) {
                restClient.delete().uri(APPLICATION_PATH, externalApplicationId)
            }
        return requireData(outcome, APPLICATION_PATH)
    }

    /**
     * Sends one request and returns the status with whatever envelope could be read.
     *
     * Non-2xx responses are not exceptions here: the API's 4xx bodies carry the error code the
     * caller needs, and RestClient's default handling would throw them away. [pathForLog] is
     * a template, never a URL with ids or query values in it.
     */
    private fun <T> send(
        pathForLog: String,
        type: ParameterizedTypeReference<ExternalEnvelope<T>>,
        request: () -> RestClient.RequestHeadersSpec<*>,
    ): Outcome<T> {
        if (!properties.isConfigured()) {
            throw CourseApplicationApiUnavailableException("Course application API key is not configured")
        }
        val outcome =
            try {
                request()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.clientApiKey}")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange { _, response ->
                        val status = response.statusCode.value()
                        val envelope =
                            try {
                                response.bodyTo(type)
                            } catch (e: RestClientException) {
                                // A proxy's HTML error page, most likely. Reported as an
                                // unreadable body below rather than as a transport failure.
                                log.warn(
                                    "Course application API body unreadable path={} status={} reason={}",
                                    pathForLog,
                                    status,
                                    e.javaClass.simpleName,
                                )
                                null
                            }
                        Outcome(status, envelope)
                    }
            } catch (e: RestClientException) {
                // Only the path template is logged, never a header — the key is in one of them.
                log.warn("Course application API call failed path={} reason={}", pathForLog, e.javaClass.simpleName)
                throw CourseApplicationApiUnavailableException("Course application API request failed for $pathForLog", e)
            }
        return outcome ?: throw CourseApplicationApiUnavailableException("Course application API gave no response for $pathForLog")
    }

    private fun <T> requireData(
        outcome: Outcome<T>,
        pathForLog: String,
    ): T {
        val status = outcome.status
        if (status in 200..299) {
            return outcome.envelope?.data
                ?: throw CourseApplicationApiUnavailableException("Course application API returned no data for $pathForLog")
        }
        val error = outcome.envelope?.error
        if (status == 401 || status == 403) {
            // Our configuration is wrong, not the applicant's input. Error level: nobody can
            // apply until the key is fixed.
            log.error("Course application API rejected the configured key path={} status={} code={}", pathForLog, status, error?.code)
            throw CourseApplicationApiUnavailableException("Course application API rejected the configured key")
        }
        if (status >= 500 || error == null || error.code.isBlank()) {
            log.warn("Course application API failed path={} status={} code={}", pathForLog, status, error?.code)
            throw CourseApplicationApiUnavailableException("Course application API answered $status for $pathForLog")
        }
        log.info("Course application API rejected request path={} status={} code={}", pathForLog, status, error.code)
        throw CourseApplicationApiRejectedException(
            httpStatus = status,
            code = error.code,
            message = error.message,
            fieldErrors = error.details.mapValues { (_, value) -> value?.toString().orEmpty() },
        )
    }

    private companion object {
        const val COURSES_PATH = "/courses"
        const val APPLICATIONS_PATH = "/applications"
        const val APPLICATION_PATH = "/applications/{id}"
        const val BY_CLIENT_ID_PATH = "/applications/by-client-id/{id}"
        const val APPLICATION_NOT_FOUND = "application_not_found"
    }
}
