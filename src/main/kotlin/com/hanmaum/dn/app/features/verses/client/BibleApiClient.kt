package com.hanmaum.dn.app.features.verses.client

import com.hanmaum.dn.app.features.verses.config.BibleApiProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
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

/**
 * A builder with explicit connect and read timeouts.
 *
 * Without them a hung upstream holds request threads until the container gives up. The verse
 * card is decoration on a home screen and the upstream is a small PHP host, so failing fast
 * and taking the already-handled "unavailable" path is strictly better than waiting.
 */
private fun timeoutedBuilder(properties: BibleApiProperties): RestClient.Builder =
    RestClient.builder().requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        },
    )

@Component
class HttpBibleApiClient(
    private val properties: BibleApiProperties,
    // Defaulted rather than injected: spring-boot-starter-webmvc alone contributes no
    // RestClient.Builder bean. The timeouts live in the default rather than being applied
    // here, so a caller that supplies its own builder — a test binding a mock server — keeps
    // the request factory it set.
    restClientBuilder: RestClient.Builder = timeoutedBuilder(properties),
) : BibleApiClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    /**
     * One quiet-time lookup per date, process-wide.
     *
     * The answer depends on the date and nothing else — it is the same for every member —
     * and both the verse card and the streak's markability ask for it. Uncached, a
     * congregation opening Home on a Sunday morning was hundreds of requests to a small PHP
     * host for one unchanging answer.
     *
     * A miss is cached too: "no passage today" is a fact worth remembering, and it is the
     * common case. Failures are not, so an outage recovers on the next request rather than
     * being frozen in for the day. Bounded so a long-running process cannot accumulate a
     * year of dates.
     */
    private val quietTimeByDate = ConcurrentHashMap<LocalDate, Optional<QuietTimeItem>>()

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
        quietTimeByDate[date]?.let { return it.orElse(null) }

        val data =
            get("/quiet-time.php?date=$date", object : ParameterizedTypeReference<BibleEnvelope<QuietTimeData>>() {})
                ?: throw BibleApiUnavailableException("Bible API returned no quiet-time payload")
        // found:false is the documented empty state — Sundays and gaps in the plan. A miss,
        // not a failure, so it returns rather than throws, and is remembered like any answer.
        val item = if (data.found) data.item else null

        if (quietTimeByDate.size >= MAX_CACHED_DATES) quietTimeByDate.clear()
        quietTimeByDate[date] = Optional.ofNullable(item)
        return item
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

    private companion object {
        /**
         * A handful of days is all that is ever asked for — today, and whatever a client
         * looked at recently. The cap exists so a process running for months cannot hold a
         * year of dates; clearing wholesale is fine at this size and needs no eviction
         * policy to reason about.
         */
        const val MAX_CACHED_DATES = 64
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
