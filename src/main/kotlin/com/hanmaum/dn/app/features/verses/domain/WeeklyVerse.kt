package com.hanmaum.dn.app.features.verses.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * An admin's override for the week's 암송 구절.
 *
 * Not the primary source any more, and never should have been the only one: the
 * congregation publishes the weekly verse itself, on its old front-end rather than under
 * `api/v1`, which is why an earlier search for it came up empty and this table was made to
 * carry the whole job. It no longer does — see
 * [com.hanmaum.dn.app.features.verses.service.VerseService.currentWeeklyVerse].
 *
 * A row here still wins for its week. That is deliberate: it is what the congregation has
 * when the source publishes nothing, publishes late, or publishes the wrong thing. The text
 * is fetched upstream from these coordinates; only the choice is ours.
 *
 * One row per week: [weekStart] is unique, and it is always a Sunday, matching 주일 as the
 * start of the week everywhere else in this app.
 */
@Entity
@Table(
    name = "weekly_verses",
    uniqueConstraints = [UniqueConstraint(name = "uq_weekly_verse_week", columnNames = ["week_start"])],
)
class WeeklyVerse(
    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,
    @Column(name = "book", nullable = false)
    var book: Int,
    @Column(name = "chapter_start", nullable = false)
    var chapterStart: Int,
    @Column(name = "verse_start", nullable = false)
    var verseStart: Int,
    @Column(name = "chapter_end", nullable = false)
    var chapterEnd: Int,
    @Column(name = "verse_end", nullable = false)
    var verseEnd: Int,
    /** Null means "use the deployment default", so a translation change does not rewrite history. */
    @Column(name = "translation_id")
    var translationId: Int? = null,
) : BaseEntity() {
    /** Saturday of the same week. Derived rather than stored — a week is always seven days. */
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}
