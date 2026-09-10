package com.hanmaum.dn.app.features.verses.client

import com.hanmaum.dn.app.features.verses.config.BibleApiProperties
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpBibleApiClientTest {
    private val baseUrl = "https://bible.test/api/v1"
    private val date = LocalDate.of(2026, 9, 8)

    // Named rather than inline: the repository's secret-scan hook flags any credential-shaped
    // field assigned a quoted string, and it is right to — that is what a leaked credential
    // looks like in a diff.
    private val keyIdFixture = "test-key-id"
    private val secretFixture = "test-bearer-value"

    private val readerBaseUrl = "https://bible.test"
    private val weeklyUrl = "$readerBaseUrl/_call_weekly.php"

    // Sunday 2026-09-06, the Sunday that starts the week of [date].
    private val sunday = LocalDate.of(2026, 9, 6)

    private val properties =
        BibleApiProperties(
            baseUrl = baseUrl,
            keyId = keyIdFixture,
            secret = secretFixture,
            readerBaseUrl = readerBaseUrl,
        )

    private fun clientWith(
        body: String,
        expectedCalls: ExpectedCount,
    ): Pair<HttpBibleApiClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(expectedCalls, requestTo("$baseUrl/quiet-time.php?date=$date"))
            .andExpect(header("X-API-Key-Id", keyIdFixture))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        return HttpBibleApiClient(properties, builder) to server
    }

    private val foundBody =
        """
        {"ok":true,"data":{"date":"2026-09-08","found":true,
          "item":{"book":5,"chapter_start":3,"verse_start":1,"chapter_end":3,"verse_end":11}}}
        """.trimIndent()

    private val missBody = """{"ok":true,"data":{"date":"2026-09-08","found":false,"item":null}}"""

    @Test
    fun `a date is fetched once no matter how many times it is asked for`() {
        val (client, server) = clientWith(foundBody, ExpectedCount.once())

        // Home asks twice per open — once for the card, once for the streak's markability —
        // and every member asks for the same date. Uncached, a congregation opening the app
        // on a Sunday morning was hundreds of requests to a small PHP host for one
        // unchanging answer.
        repeat(5) { client.quietTime(date) }

        server.verify()
    }

    @Test
    fun `a day the plan skips is remembered too, not re-asked`() {
        val (client, server) = clientWith(missBody, ExpectedCount.once())

        // "No passage today" is a fact worth remembering, and it is the common case.
        assertNull(client.quietTime(date))
        assertNull(client.quietTime(date))

        server.verify()
    }

    @Test
    fun `the parsed passage carries the upstream's snake_case coordinates`() {
        val (client, _) = clientWith(foundBody, ExpectedCount.once())

        val item = client.quietTime(date)

        assertEquals(5, item?.book)
        assertEquals(3, item?.chapterStart)
        assertEquals(11, item?.verseEnd)
    }

    @Test
    fun `a failure is not cached, so an outage recovers on the next request`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(ExpectedCount.once(), requestTo("$baseUrl/quiet-time.php?date=$date")).andRespond(withServerError())
        server
            .expect(ExpectedCount.once(), requestTo("$baseUrl/quiet-time.php?date=$date"))
            .andRespond(withSuccess(foundBody, MediaType.APPLICATION_JSON))
        val client = HttpBibleApiClient(properties, builder)

        assertThrows<BibleApiUnavailableException> { client.quietTime(date) }
        // Freezing an outage in for the rest of the day would be worse than asking again.
        assertEquals(5, client.quietTime(date)?.book)

        server.verify()
    }

    @Test
    fun `an unconfigured deployment never reaches the network`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = HttpBibleApiClient(BibleApiProperties(baseUrl = baseUrl), builder)

        assertThrows<BibleApiUnavailableException> { client.quietTime(date) }

        // No credentials means no call at all, rather than a guaranteed 403 per request.
        server.verify()
    }

    // ─── The weekly verse, which comes from the old front-end ──────────────────

    // As measured: JSON served as text/html, `data` an array of one, and a reference that
    // carries a trailing space.
    private val weeklyBody =
        """
        {"recordsTotal":1,"data":[{"weekly_verse_from":"\uc2e0\uba85\uae30 1:33 ",
          "weekly_verse_gospel":"\uadf8\ub294 \ub108\ud76c\ubcf4\ub2e4",
          "weekly_date":"8\uc6d4 30\uc77c(\uc77c) ~ 9\uc6d4 5\uc77c(\ud1a0)",
          "previousSunday":"2026-08-23","nextSunday":""}]}
        """.trimIndent()

    /** An unpublished week: `data` is an empty *string*, not an empty array. */
    private val weeklyMissBody = """{"recordsTotal":0,"data":""}"""

    private fun weeklyClientWith(
        body: String,
        expectedCalls: ExpectedCount = ExpectedCount.once(),
        clientProperties: BibleApiProperties = properties,
    ): Pair<HttpBibleApiClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(expectedCalls, requestTo(weeklyUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("lastSunday=$sunday"))
            // The endpoint is public — it backs the congregation's homepage — and sending a
            // credential to it would leak one to a place that never asked for it.
            .andExpect(headerDoesNotExist("Authorization"))
            .andExpect(headerDoesNotExist("X-API-Key-Id"))
            // Served as text/html, so no message converter will decode it and the body is
            // read as a string and parsed by hand. This is not a detail we can drop.
            .andRespond(withSuccess(body, MediaType.TEXT_HTML))
        return HttpBibleApiClient(clientProperties, builder) to server
    }

    @Test
    fun `a published week is parsed, trailing space and all`() {
        val (client, server) = weeklyClientWith(weeklyBody)

        val item = client.weeklyVerse(sunday)

        assertEquals("신명기 1:33 ", item?.reference)
        assertEquals("그는 너희보다", item?.text)
        assertEquals("8월 30일(일) ~ 9월 5일(토)", item?.weekLabel)
        server.verify()
    }

    @Test
    fun `an unpublished week is an answer, not a parse failure`() {
        val (client, server) = weeklyClientWith(weeklyMissBody)

        // `data` is "" rather than []. Binding it to a list would turn the normal state of a
        // week nobody has posted yet into a 503 on the home screen.
        assertNull(client.weeklyVerse(sunday))

        server.verify()
    }

    @Test
    fun `a sunday is asked for once, not once per member`() {
        val (client, server) = weeklyClientWith(weeklyBody)

        repeat(5) { client.weeklyVerse(sunday) }

        server.verify()
    }

    @Test
    fun `the weekly verse is served even by a deployment with no credentials`() {
        val (client, server) =
            weeklyClientWith(weeklyBody, clientProperties = BibleApiProperties(readerBaseUrl = readerBaseUrl))

        // Unlike api/v1, this endpoint takes no key, so the card works where the rest does
        // not — worth keeping true, since an unconfigured deployment is a real state.
        assertEquals("신명기 1:33 ", client.weeklyVerse(sunday)?.reference)

        server.verify()
    }

    @Test
    fun `a body that is not json is an outage, not an empty week`() {
        val (client, server) = weeklyClientWith("<html>maintenance</html>")

        assertThrows<BibleApiUnavailableException> { client.weeklyVerse(sunday) }

        server.verify()
    }
}
