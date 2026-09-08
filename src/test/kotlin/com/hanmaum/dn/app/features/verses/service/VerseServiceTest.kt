package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.features.verses.api.v1.dto.SetWeeklyVerseRequest
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.client.BibleAppConfig
import com.hanmaum.dn.app.features.verses.client.BibleBook
import com.hanmaum.dn.app.features.verses.client.BibleTranslation
import com.hanmaum.dn.app.features.verses.client.QuietTimeItem
import com.hanmaum.dn.app.features.verses.client.VerseLine
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
    private val weekStart = LocalDate.of(2026, 9, 6)

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
        verify(client, org.mockito.Mockito.never()).verses(any(), any(), any(), any(), any())
    }

    @Test
    fun `a day without a passage is an empty answer, not a failure`() {
        // Sundays carry no quiet time upstream — verified on four of them.
        `when`(client.quietTime(today)).thenReturn(null)

        val result = service.getToday()

        assertNull(result.reference)
        assertNull(result.sourceUrl)
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
    fun `weekly is empty while no verse has been chosen`() {
        `when`(repository.findByWeekStartAndDeletedAtIsNull(weekStart)).thenReturn(null)

        val result = service.getWeekly()

        assertNull(result.reference)
        assertNull(result.weekStart)
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
