package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.features.verses.client.WeeklyVerseItem
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import java.time.LocalDate

/**
 * Which verse the 주간 암송 card is showing, and where it came from.
 *
 * Two sources, and they carry different things. The congregation publishes the verse itself
 * on its old front-end, as finished Korean prose with no coordinates; an admin setting one
 * through `PUT /verses/weekly` gives coordinates, from which the text is fetched. Rendering
 * differs, so the difference is a type rather than a nullable field to remember to check.
 *
 * What both always carry is [weekStart] — the week the verse belongs to, which is not
 * necessarily the running week. The recitation streak hangs off it, and asking for it must
 * stay cheap: it is read on every Home open, for every member. Neither branch fetches
 * anything to answer it.
 */
sealed interface WeeklyVerseSelection {
    val weekStart: LocalDate

    /** Saturday of the same week. Derived — a week is always seven days. */
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

/** An admin's choice for the running week. Overrides the source; see [VerseService]. */
data class AdminWeeklyVerse(
    val verse: WeeklyVerse,
) : WeeklyVerseSelection {
    override val weekStart: LocalDate get() = verse.weekStart
}

/**
 * What the congregation published for [weekStart].
 *
 * [weekStart] is the Sunday that was asked for, not something parsed out of the answer: the
 * lookup is by Sunday, so the week is known before the payload is read. The payload's own
 * `weekly_date` says the same thing in Korean prose and is not relied on.
 */
data class SourceWeeklyVerse(
    override val weekStart: LocalDate,
    val item: WeeklyVerseItem,
) : WeeklyVerseSelection
