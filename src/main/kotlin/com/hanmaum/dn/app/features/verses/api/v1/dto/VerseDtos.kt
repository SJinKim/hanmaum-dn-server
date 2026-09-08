package com.hanmaum.dn.app.features.verses.api.v1.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

/**
 * A passage reference in every language the app renders.
 *
 * The upstream returns one string, always Korean and always abbreviated — `요 3:16` even
 * when another translation was requested. The Home card shows Korean and English under one
 * another, and the app cannot build the names itself because it never sees the upstream's
 * book table. So the proxy resolves them.
 */
data class VerseReference(
    val ko: String,
    val en: String,
    val de: String,
)

data class DailyVerseResponse(
    /** Null on a day the reading plan has no passage — Sundays, and gaps between years. */
    val reference: VerseReference? = null,
    val book: Int? = null,
    val chapter: Int? = null,
    val verseFrom: Int? = null,
    val verseTo: Int? = null,
    /** Display name of the translation, e.g. 개역개정. */
    val translation: String? = null,
    /**
     * Passage text. Left null for the daily reading on purpose: a quiet-time passage runs
     * 8-25 verses and does not belong on a home card. The field exists so a detail screen
     * can be served later without a contract change.
     */
    val text: String? = null,
    /** Deeplink into the congregation's own reader, so the card can offer "read on". */
    val sourceUrl: String? = null,
)

data class WeeklyVerseResponse(
    /** Null while no verse has been chosen for the current week. */
    val reference: VerseReference? = null,
    val book: Int? = null,
    val chapter: Int? = null,
    val verseFrom: Int? = null,
    val verseTo: Int? = null,
    val translation: String? = null,
    /** Filled here, unlike the daily passage — a memory verse is short and is the point. */
    val text: String? = null,
    val weekStart: LocalDate? = null,
    val weekEnd: LocalDate? = null,
    val sourceUrl: String? = null,
)

/**
 * Admin choice of the week's memory verse. [weekStart] is optional and defaults to the
 * current week, which is the common case — setting next week's ahead of time is the
 * exception, not the default.
 */
data class SetWeeklyVerseRequest(
    val weekStart: LocalDate? = null,
    @field:Min(1) @field:Max(66)
    val book: Int,
    @field:Min(1)
    val chapterStart: Int,
    @field:Min(1)
    val verseStart: Int,
    @field:Min(1)
    val chapterEnd: Int,
    @field:Min(1)
    val verseEnd: Int,
    val translationId: Int? = null,
)
