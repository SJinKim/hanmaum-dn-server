package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.features.verses.api.v1.dto.DailyVerseResponse
import com.hanmaum.dn.app.features.verses.api.v1.dto.DailyVerseState
import com.hanmaum.dn.app.features.verses.api.v1.dto.SetWeeklyVerseRequest
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseReference
import com.hanmaum.dn.app.features.verses.api.v1.dto.WeeklyVerseResponse
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.config.BibleApiProperties
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import com.hanmaum.dn.app.features.verses.repository.WeeklyVerseRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class VerseService(
    private val client: BibleApiClient,
    private val weeklyVerseRepository: WeeklyVerseRepository,
    private val properties: BibleApiProperties,
    private val clock: Clock,
) {
    /**
     * Today's quiet-time passage, or an empty response on a day the plan has none.
     *
     * The empty response is a real answer — Sundays carry no passage upstream, verified
     * across four of them. An unreachable upstream is a different thing and surfaces as 503,
     * so the app never renders "no passage today" because a third party was down.
     */
    @Transactional(readOnly = true)
    fun getToday(): DailyVerseResponse {
        val today = LocalDate.now(clock)
        val item =
            unavailableAs503 { client.quietTime(today) }
                // Upstream is still asked on Sundays rather than short-circuited: should the
                // congregation ever publish a Sunday passage, it wins over the notice.
                ?: return DailyVerseResponse(
                    state =
                        if (today.dayOfWeek == DayOfWeek.SUNDAY) {
                            DailyVerseState.SUNDAY_SERVICE
                        } else {
                            DailyVerseState.NO_PLAN
                        },
                )
        val config = unavailableAs503 { client.appConfig() }

        return DailyVerseResponse(
            reference =
                buildReference(
                    config.books
                        .firstOrNull { it.id == item.book }
                        ?.names
                        .orEmpty(),
                    item.book,
                    item.chapterStart,
                    item.verseStart,
                    item.chapterEnd,
                    item.verseEnd,
                ),
            book = item.book,
            chapter = item.chapterStart,
            verseFrom = item.verseStart,
            verseTo = item.verseEnd,
            translation = translationTitle(config, properties.defaultTranslationId),
            // Deliberately not fetched: a quiet-time passage is 8-25 verses and the card
            // shows a reference and a link. See the field's own note.
            text = null,
            sourceUrl = "${properties.readerBaseUrl}/quiettime.php?qt_date=$today",
            state = DailyVerseState.PASSAGE,
        )
    }

    /**
     * This week's memory verse, or an empty response while none has been chosen.
     *
     * Unlike the daily passage the text is fetched: a memory verse is one or two verses, and
     * reciting it is the entire point of the card.
     */
    @Transactional(readOnly = true)
    fun getWeekly(): WeeklyVerseResponse {
        val verse = currentWeeklyVerse() ?: return WeeklyVerseResponse()
        val translationId = verse.translationId ?: properties.defaultTranslationId
        val config = unavailableAs503 { client.appConfig() }
        val lines =
            unavailableAs503 {
                client.verses(verse.book, verse.chapterStart, verse.verseStart, verse.verseEnd, translationId)
            }

        return WeeklyVerseResponse(
            reference =
                buildReference(
                    config.books
                        .firstOrNull { it.id == verse.book }
                        ?.names
                        .orEmpty(),
                    verse.book,
                    verse.chapterStart,
                    verse.verseStart,
                    verse.chapterEnd,
                    verse.verseEnd,
                ),
            book = verse.book,
            chapter = verse.chapterStart,
            verseFrom = verse.verseStart,
            verseTo = verse.verseEnd,
            translation = translationTitle(config, translationId),
            text = lines.joinToString(" ") { it.text }.ifBlank { null },
            weekStart = verse.weekStart,
            weekEnd = verse.weekEnd,
            sourceUrl = "${properties.readerBaseUrl}/reader.php?book=${verse.book}&chapter=${verse.chapterStart}",
        )
    }

    /** Admin choice for a week. Re-setting the same week overwrites rather than duplicating. */
    @Transactional
    fun setWeekly(request: SetWeeklyVerseRequest): WeeklyVerseResponse {
        if (request.chapterEnd < request.chapterStart ||
            (request.chapterEnd == request.chapterStart && request.verseEnd < request.verseStart)
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "구절의 끝이 시작보다 앞설 수 없습니다.")
        }
        val weekStart = request.weekStart?.let(::weekStartOf) ?: weekStartOf(LocalDate.now(clock))

        val existing = weeklyVerseRepository.findByWeekStartAndDeletedAtIsNull(weekStart)
        val verse =
            existing?.apply {
                book = request.book
                chapterStart = request.chapterStart
                verseStart = request.verseStart
                chapterEnd = request.chapterEnd
                verseEnd = request.verseEnd
                translationId = request.translationId
            } ?: WeeklyVerse(
                weekStart = weekStart,
                book = request.book,
                chapterStart = request.chapterStart,
                verseStart = request.verseStart,
                chapterEnd = request.chapterEnd,
                verseEnd = request.verseEnd,
                translationId = request.translationId,
            )
        weeklyVerseRepository.save(verse)
        return getWeekly()
    }

    /** The row for the running week, or null when the admin has not chosen one. */
    fun currentWeeklyVerse(): WeeklyVerse? = weeklyVerseRepository.findByWeekStartAndDeletedAtIsNull(weekStartOf(LocalDate.now(clock)))

    /** Sunday-based, matching 주일 as the start of the week everywhere else in this app. */
    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    private fun buildReference(
        names: Map<String, String>,
        book: Int,
        chapterStart: Int,
        verseStart: Int,
        chapterEnd: Int,
        verseEnd: Int,
    ): VerseReference {
        // Falls back to the book number rather than failing: an unnamed book still renders
        // a usable reference, and the upstream adding a book must not break the card.
        fun name(lang: String) = names[lang] ?: names["en"] ?: "Book $book"

        val range =
            if (chapterStart == chapterEnd) {
                if (verseStart == verseEnd) "$chapterStart:$verseStart" else "$chapterStart:$verseStart-$verseEnd"
            } else {
                "$chapterStart:$verseStart-$chapterEnd:$verseEnd"
            }
        return VerseReference(
            ko = "${name("ko")} $range",
            en = "${name("en")} $range",
            // German cites chapter and verse with a comma: "5. Mose 3,1-11".
            de = "${name("de")} ${range.replaceFirst(':', ',')}",
        )
    }

    private fun translationTitle(
        config: com.hanmaum.dn.app.features.verses.client.BibleAppConfig,
        translationId: Int,
    ): String? = config.translations.firstOrNull { it.id == translationId }?.title

    /**
     * The upstream being unreachable is an infrastructure fault, not an empty reading plan.
     * It becomes a 503 so the client can tell "no passage today" from "could not ask".
     */
    private fun <T> unavailableAs503(block: () -> T): T =
        try {
            block()
        } catch (e: BibleApiUnavailableException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "말씀 데이터를 불러올 수 없습니다.", e)
        }
}
