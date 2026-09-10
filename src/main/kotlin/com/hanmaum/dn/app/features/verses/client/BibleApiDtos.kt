package com.hanmaum.dn.app.features.verses.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Upstream wire shapes for `bible.asher.design/api/v1`.
 *
 * Every response is HTTP 200 with an `ok` flag; a business-level empty result — no quiet
 * time on a Sunday — is `ok:true` with `found:false`, not an error status. Only auth
 * failures use a status code (403).
 *
 * The upstream is snake_case. It is mapped field by field rather than through a global
 * naming strategy, so nothing about this third-party shape can leak into how the rest of
 * the API serializes.
 *
 * Unknown properties are ignored on purpose: the upstream carries far more than this proxy
 * needs and is maintained by someone else.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BibleEnvelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: BibleError? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BibleError(
    val code: String? = null,
    val message: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BibleAppConfig(
    @param:JsonProperty("server_date") val serverDate: String? = null,
    @param:JsonProperty("default_translation_id") val defaultTranslationId: Int? = null,
    val translations: List<BibleTranslation> = emptyList(),
    val books: List<BibleBook> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BibleTranslation(
    val id: Int = 0,
    val title: String? = null,
    @param:JsonProperty("language_code") val languageCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BibleBook(
    val id: Int = 0,
    @param:JsonProperty("chapter_count") val chapterCount: Int = 0,
    /** Localized full names keyed by language: ko, en, de, es. */
    val names: Map<String, String> = emptyMap(),
    val abbr: Map<String, String> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuietTimeData(
    val date: String? = null,
    val found: Boolean = false,
    val item: QuietTimeItem? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuietTimeItem(
    val date: String? = null,
    val book: Int = 0,
    @param:JsonProperty("chapter_start") val chapterStart: Int = 0,
    @param:JsonProperty("verse_start") val verseStart: Int = 0,
    @param:JsonProperty("chapter_end") val chapterEnd: Int = 0,
    @param:JsonProperty("verse_end") val verseEnd: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VerseData(
    @param:JsonProperty("translation_id") val translationId: Int? = null,
    val book: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    @param:JsonProperty("verse_to") val verseTo: Int = 0,
    val found: Boolean = false,
    val gospel: List<VerseLine> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VerseLine(
    val verse: Int = 0,
    val text: String = "",
)

/**
 * One week's 주간 암송 verse, as the congregation's old front-end serves it.
 *
 * A different shape from everything above, because it comes from a different place: not
 * `api/v1` but `_call_weekly.php` on the old site, which answers an unenveloped DataTables
 * payload — `recordsTotal` plus `data` — with no `ok` flag and no authentication. It is what
 * the homepage's own 주간 암송 block renders.
 *
 * Only Korean, and only prose: the reference arrives as a finished string ("신명기 1:33 ",
 * trailing space and all), never as book/chapter/verse coordinates. Parsing it back into
 * numbers would mean guessing at abbreviated and spelled-out book names, ranges and
 * one-offs, so the string is passed through as-is and [com.hanmaum.dn.app.features.verses.api.v1.dto.VerseReference]
 * carries Korean alone for these.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyVerseItem(
    @param:JsonProperty("weekly_verse_from") val reference: String = "",
    @param:JsonProperty("weekly_verse_gospel") val text: String = "",
    /** Human-readable span, e.g. "8월 30일(일) ~ 9월 5일(토)". Kept for logs, not served. */
    @param:JsonProperty("weekly_date") val weekLabel: String? = null,
)
