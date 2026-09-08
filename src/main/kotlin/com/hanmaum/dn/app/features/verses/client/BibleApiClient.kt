package com.hanmaum.dn.app.features.verses.client

import com.hanmaum.dn.app.features.verses.config.BibleApiProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/**
 * The upstream is unreachable, unconfigured, or answered something this proxy cannot use.
 *
 * Deliberately distinct from an empty result. "No quiet time today" is a fact about the
 * reading plan; "we could not ask" is a fact about the network. Collapsing the two would
 * tell the app there is no passage on a day when there is one.
 */
class BibleApiUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The congregation's bible API, as much of it as this proxy needs.
 *
 * Lookups return null for a genuine miss — a Sunday, a gap in the plan — and throw
 * [BibleApiUnavailableException] when the question could not be asked at all.
 */
interface BibleApiClient {
    /** Book and translation metadata. Effectively static; cached for the process lifetime. */
    fun appConfig(): BibleAppConfig

    /** Quiet-time passage coordinates for [date], or null on days without one. */
    fun quietTime(date: LocalDate): QuietTimeItem?

    /** Verse text for a coordinate range; empty when the upstream has no such passage. */
    fun verses(
        book: Int,
        chapter: Int,
        verseFrom: Int,
        verseTo: Int,
        translationId: Int,
    ): List<VerseLine>
}

@Component
class HttpBibleApiClient(
    private val properties: BibleApiProperties,
) : BibleApiClient {
    private val log = LoggerFactory.getLogger(javaClass)

    // Built here rather than injected: spring-boot-starter-webmvc alone contributes no
    // RestClient.Builder bean, and this client wants its own base URL anyway.
    private val restClient = RestClient.builder().baseUrl(properties.baseUrl).build()

    /**
     * app-config carries 66 books and 100 translations, changes about never, and every
     * lookup needs it to build a reference. Fetched once and kept. A failure is not cached,
     * so an outage during startup recovers on the next request instead of poisoning the
     * process.
     */
    private val cachedConfig = AtomicReference<BibleAppConfig?>(null)

    override fun appConfig(): BibleAppConfig {
        cachedConfig.get()?.let { return it }
        val fetched =
            get("/app-config.php", object : ParameterizedTypeReference<BibleEnvelope<BibleAppConfig>>() {})
                ?: throw BibleApiUnavailableException("Bible API returned no app-config")
        cachedConfig.compareAndSet(null, fetched)
        return fetched
    }

    override fun quietTime(date: LocalDate): QuietTimeItem? {
        val data =
            get("/quiet-time.php?date=$date", object : ParameterizedTypeReference<BibleEnvelope<QuietTimeData>>() {})
                ?: throw BibleApiUnavailableException("Bible API returned no quiet-time payload")
        // found:false is the documented empty state — Sundays and gaps in the plan. A miss,
        // not a failure, so it returns rather than throws.
        return if (data.found) data.item else null
    }

    override fun verses(
        book: Int,
        chapter: Int,
        verseFrom: Int,
        verseTo: Int,
        translationId: Int,
    ): List<VerseLine> {
        val path =
            "/verse.php?book=$book&chapter=$chapter&verse=$verseFrom" +
                "&verse_to=$verseTo&translation=$translationId"
        val data =
            get(path, object : ParameterizedTypeReference<BibleEnvelope<VerseData>>() {})
                ?: throw BibleApiUnavailableException("Bible API returned no verse payload")
        if (!data.found) return emptyList()
        // The German translations arrive with a trailing \r\n.
        return data.gospel.map { it.copy(text = it.text.trim()) }
    }

    private fun <T> get(
        path: String,
        type: ParameterizedTypeReference<BibleEnvelope<T>>,
    ): T? {
        if (!properties.isConfigured()) {
            throw BibleApiUnavailableException("Bible API credentials are not configured")
        }
        val envelope =
            try {
                restClient
                    .get()
                    .uri(path)
                    .header("X-API-Key-Id", properties.keyId)
                    .header("Authorization", "Bearer ${properties.secret}")
                    .retrieve()
                    .body(type)
            } catch (e: RestClientException) {
                // Wrapped with context and re-thrown rather than flattened into an empty
                // result: the caller decides what a dead upstream means for its endpoint.
                // Only the path is logged, never a header — the secret is in one of them.
                log.warn("Bible API call failed path={} reason={}", path.substringBefore('?'), e.javaClass.simpleName)
                throw BibleApiUnavailableException("Bible API request failed for ${path.substringBefore('?')}", e)
            }

        if (envelope == null || !envelope.ok) {
            log.warn(
                "Bible API returned a business error path={} code={}",
                path.substringBefore('?'),
                envelope?.error?.code,
            )
            throw BibleApiUnavailableException("Bible API reported ${envelope?.error?.code ?: "an empty response"}")
        }
        return envelope.data
    }
}
