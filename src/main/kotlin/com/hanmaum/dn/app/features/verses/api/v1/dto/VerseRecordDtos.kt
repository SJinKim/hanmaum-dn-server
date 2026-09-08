package com.hanmaum.dn.app.features.verses.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/**
 * One streak, as the Home card draws it: seven pills and a total.
 */
data class VerseRecordBlock(
    /** Sunday the shown week starts on, matching 주일 as the start of the week. */
    val weekStart: LocalDate,
    /**
     * Marked days inside the running week only. Seven pills are all the client draws, so
     * sending more would be data nobody renders — a member with years of history would
     * otherwise transfer years of dates for a seven-day strip.
     */
    val days: List<LocalDate>,
    val todayMarked: Boolean,
    /**
     * Whether today can be marked at all, so the client does not have to derive the reason.
     * False on Sundays for 오늘의 말씀 (the reading plan has no passage) and on any day
     * without one, and false for 암송 while no verse has been chosen for the week.
     */
    val todayMarkable: Boolean,
    /** Every mark ever, not just this week — the figure under the pills. */
    val totalDays: Long,
)

/** Both streaks in one payload, so Home loads once rather than twice. */
data class VerseRecordsResponse(
    val quietTime: VerseRecordBlock,
    val recitation: VerseRecordBlock,
)

/**
 * Marks today. Carries no date on purpose: the server stamps the day, so "today only" is
 * not a client-side rule that a changed device clock defeats.
 */
data class MarkVerseRecordRequest(
    @field:NotNull
    val kind: VerseRecordKind,
)
