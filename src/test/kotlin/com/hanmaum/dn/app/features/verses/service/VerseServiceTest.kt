package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.features.verses.api.v1.dto.DailyVerseState
import com.hanmaum.dn.app.features.verses.api.v1.dto.SetWeeklyVerseRequest
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.client.BibleAppConfig
import com.hanmaum.dn.app.features.verses.client.BibleBook
import com.hanmaum.dn.app.features.verses.client.BibleTranslation
import com.hanmaum.dn.app.features.verses.client.QuietTimeItem
import com.hanmaum.dn.app.features.verses.client.VerseLine
import com.hanmaum.dn.app.features.verses.client.WeeklyVerseItem
import com.hanmaum.dn.app.features.verses.config.BibleApiProperties
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import com.hanmaum.dn.app.features.verses.repository.WeeklyVerseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class VerseServiceTest {
    @Mock private lateinit var client: BibleApiClient

    @Mock private lateinit var repository: WeeklyVerseRepository

    private lateinit var service: VerseService

    private val zone = ZoneId.of("Europe/Berlin")

    // Tuesday 2026-09-08; the Sunday that starts its week is 2026-09-06.
    private val clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), zone)
    private val today = LocalDate.of(2026, 9, 8)

    // Sunday 2026-09-06.
    private val sundayClock = Clock.fixed(Instant.parse("2026-09-06T09:00:00Z"), zone)
    private val sunday = LocalDate.of(2026, 9, 6)
    private val weekStart = LocalDate.of(2026, 9, 6)

    // Sunday 2026-08-30, the week before.
    private val previousWeek = LocalDate.of(2026, 8, 30)

    // As measured: the reference is Korean prose and carries a trailing space.
    private val published =
        WeeklyVerseItem(
            reference = "신명기 1:33 ",
            text = "그는 너희보다 먼저 그 길을 가시며",
            weekLabel = "8월 30일(일) ~ 9월 5일(토)",
        )

    private val appConfig =
        BibleAppConfig(
            defaultTranslationId = 92,
            translations = listOf(BibleTranslation(id = 92, title = "개역개정", languageCode = "KO")),
            books =
                listOf(
                    BibleBook(
                        id = 5,
                        chapterCount = 34,
                        names = mapOf("ko" to "신명기", "en" to "Deuteronomy", "de" to "5. Mose"),
                    ),
                ),
        )

    @BeforeEach
    fun setUp() {
        service = VerseService(client, repository, BibleApiProperties(), clock)
    }

    @Test
    fun `today resolves the reference into every language the app renders`() {
        `when`(client.quietTime(today)).thenReturn(
            QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 11),
        )
        `when`(client.appConfig()).thenReturn(appConfig)

        val result = service.getToday()

        // The upstream only ever returns "신 3:1-11" — abbreviated and Korean regardless of
        // the requested translation. The card shows Korean and English, so the proxy builds
        // both from the book table the app never sees.
        assertEquals("신명기 3:1-11", result.reference?.ko)
        assertEquals("Deuteronomy 3:1-11", result.reference?.en)
        // German cites chapter and verse with a comma.
        assertEquals("5. Mose 3,1-11", result.reference?.de)
        assertEquals("개역개정", result.translation)
        assertEquals(DailyVerseState.PASSAGE, result.state)
        assertEquals("https://bible.asher.design/quiettime.php?qt_date=2026-09-08", result.sourceUrl)
    }

    @Test
    fun `today leaves the passage text unfetched`() {
        `when`(client.quietTime(today)).thenReturn(
            QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 11),
        )
        `when`(client.appConfig()).thenReturn(appConfig)

        // A quiet-time passage runs 8-25 verses and does not belong on a home card, so the
        // proxy must not spend an upstream call on text nobody renders.
        assertNull(service.getToday().text)
        verify(client, never()).verses(any(), any(), any(), any(), any())
    }

    @Test
    fun `a day without a passage is an empty answer, not a failure`() {
        // A gap in the plan, which happens at a year boundary. Nothing to read and nothing
        // to announce, so the card stays blank.
        `when`(client.quietTime(today)).thenReturn(null)

        val result = service.getToday()

        assertNull(result.reference)
        assertNull(result.sourceUrl)
        assertEquals(DailyVerseState.NO_PLAN, result.state)
    }

    @Test
    fun `a sunday without a passage names the service instead of going blank`() {
        val sundayService = VerseService(client, repository, BibleApiProperties(), sundayClock)
        `when`(client.quietTime(sunday)).thenReturn(null)

        val result = sundayService.getToday()

        // A state, not a sentence: the app is localized and the server is not, so the
        // wording stays where the member's language is known.
        assertEquals(DailyVerseState.SUNDAY_SERVICE, result.state)
        assertNull(result.reference)
    }

    @Test
    fun `a sunday the congregation does publish a passage for shows the passage`() {
        val sundayService = VerseService(client, repository, BibleApiProperties(), sundayClock)
        `when`(client.quietTime(sunday)).thenReturn(
            QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 11),
        )
        `when`(client.appConfig()).thenReturn(appConfig)

        // Upstream is asked on Sundays rather than short-circuited, so a passage published
        // for one wins over the notice instead of being hidden by a local weekday rule.
        val result = sundayService.getToday()

        assertEquals("신명기 3:1-11", result.reference?.ko)
        assertEquals(DailyVerseState.PASSAGE, result.state)
    }

    @Test
    fun `an unreachable upstream is a 503, never an empty passage`() {
        `when`(client.quietTime(today)).thenThrow(BibleApiUnavailableException("boom"))

        // Otherwise the card would claim there is no passage today on a day when there is
        // one, and the member would never know the difference.
        val exception = assertThrows<ResponseStatusException> { service.getToday() }

        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `weekly is empty only when the source has nothing either`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(any())).thenReturn(null)

        val result = service.getWeekly()

        assertNull(result.reference)
        assertNull(result.weekStart)
    }

    @Test
    fun `the congregation's own publication fills the card, with nobody maintaining it`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(weekStart)).thenReturn(published)

        val result = service.getWeekly()

        // The reference arrives with a trailing space and is passed through otherwise: it is
        // finished Korean prose, and parsing it back into book/chapter/verse would mean
        // guessing at abbreviations, ranges and one-offs.
        assertEquals("신명기 1:33", result.reference?.ko)
        assertEquals("그는 너희보다 먼저 그 길을 가시며", result.text)
        assertEquals(weekStart, result.weekStart)
        assertEquals(LocalDate.of(2026, 9, 12), result.weekEnd)
    }

    @Test
    fun `a published verse carries korean alone, and costs no further upstream call`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(weekStart)).thenReturn(published)

        val result = service.getWeekly()

        // No coordinates come with it, so there is no book to name in English or German —
        // and nothing to look up either. The card shows Korean; see hanmaum-dn-mobile-app#192.
        assertNull(result.reference?.en)
        assertNull(result.reference?.de)
        assertNull(result.translation)
        verify(client, never()).verses(any(), any(), any(), any(), any())
        verify(client, never()).appConfig()
    }

    @Test
    fun `an unpublished running week shows the newest verse there is, with its own week`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        // Measured on a Thursday: the congregation had not published the running week yet.
        `when`(client.weeklyVerse(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(previousWeek)).thenReturn(published)

        val result = service.getWeekly()

        // Not the running week's dates. The card says which week the verse is for, so this
        // misleads nobody — and it beats a blank card, which is what 0.8.0 shipped.
        assertEquals("신명기 1:33", result.reference?.ko)
        assertEquals(previousWeek, result.weekStart)
        assertEquals(LocalDate.of(2026, 9, 5), result.weekEnd)
    }

    @Test
    fun `the walk backwards is bounded, so a source gone quiet costs a fixed number of calls`() {
        val bounded = VerseService(client, repository, BibleApiProperties(weeklyLookbackWeeks = 2), clock)
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(any())).thenReturn(null)

        assertNull(bounded.getWeekly().reference)

        // The running week plus two back, and then it stops rather than walking to 1970.
        verify(client, times(3)).weeklyVerse(any())
    }

    @Test
    fun `an admin choice overrides what the congregation published`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(
            WeeklyVerse(weekStart = weekStart, book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 1),
        )
        `when`(client.appConfig()).thenReturn(appConfig)
        `when`(client.verses(5, 3, 1, 1, 92)).thenReturn(listOf(VerseLine(1, "관리자가 고른 구절")))

        val result = service.getWeekly()

        // This is what keeps the congregation able to act when the source publishes nothing,
        // publishes late, or publishes the wrong thing.
        assertEquals("관리자가 고른 구절", result.text)
        assertEquals("신명기 3:1", result.reference?.ko)
        verify(client, never()).weeklyVerse(any())
    }

    @Test
    fun `an unreachable source is a 503, never an empty weekly card`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)
        `when`(client.weeklyVerse(weekStart)).thenThrow(BibleApiUnavailableException("boom"))

        // Same reason as the daily passage: "nothing published" and "could not ask" must
        // not arrive at the client looking alike.
        val exception = assertThrows<ResponseStatusException> { service.getWeekly() }

        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `weekly carries its text and a derived week end`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(
            WeeklyVerse(
                weekStart = weekStart,
                book = 5,
                chapterStart = 3,
                verseStart = 1,
                chapterEnd = 3,
                verseEnd = 2,
            ),
        )
        `when`(client.appConfig()).thenReturn(appConfig)
        `when`(client.verses(5, 3, 1, 2, 92)).thenReturn(
            listOf(VerseLine(1, "첫째 구절"), VerseLine(2, "둘째 구절")),
        )

        val result = service.getWeekly()

        // Unlike the daily passage this one is short and reciting it is the point.
        assertEquals("첫째 구절 둘째 구절", result.text)
        assertEquals(weekStart, result.weekStart)
        assertEquals(LocalDate.of(2026, 9, 12), result.weekEnd)
    }

    @Test
    fun `setWeekly defaults to the sunday that starts the running week`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)

        service.setWeekly(
            SetWeeklyVerseRequest(book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 2),
        )

        val captor = argumentCaptor<WeeklyVerse>()
        verify(repository).save(captor.capture())
        // Sunday, matching 주일 as the start of the week everywhere else in this app.
        assertEquals(weekStart, captor.firstValue.weekStart)
    }

    @Test
    fun `setWeekly overwrites the row for a week instead of adding a second one`() {
        val existing =
            WeeklyVerse(weekStart = weekStart, book = 1, chapterStart = 1, verseStart = 1, chapterEnd = 1, verseEnd = 1)
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(existing)
        `when`(client.appConfig()).thenReturn(appConfig)
        `when`(client.verses(5, 3, 1, 11, 92)).thenReturn(emptyList())

        service.setWeekly(
            SetWeeklyVerseRequest(book = 5, chapterStart = 3, verseStart = 1, chapterEnd = 3, verseEnd = 11),
        )

        val captor = argumentCaptor<WeeklyVerse>()
        verify(repository).save(captor.capture())
        assertEquals(5, captor.firstValue.book)
        assertEquals(existing.publicId, captor.firstValue.publicId)
    }

    @Test
    fun `setWeekly rejects a range that ends before it starts`() {
        val exception =
            assertThrows<ResponseStatusException> {
                service.setWeekly(
                    SetWeeklyVerseRequest(book = 5, chapterStart = 3, verseStart = 11, chapterEnd = 3, verseEnd = 1),
                )
            }

        assertEquals(400, exception.statusCode.value())
    }

    @Test
    fun `an unnamed book still renders a usable reference`() {
        `when`(client.quietTime(today)).thenReturn(
            QuietTimeItem(book = 66, chapterStart = 1, verseStart = 1, chapterEnd = 1, verseEnd = 1),
        )
        `when`(client.appConfig()).thenReturn(appConfig)

        // The upstream adding a book this proxy has no name for must not break the card.
        assertEquals("Book 66 1:1", service.getToday().reference?.ko)
    }
}
