package com.hanmaum.dn.app.common.domainvalue

/**
 * The two things a member can mark off on the Home verse cards.
 *
 * They are separate streaks on purpose: reading the day's passage and reciting the week's
 * verse are different practices, and a single counter would let one hide the other.
 */
enum class VerseRecordKind {
    /** 오늘의 말씀 — the day's quiet-time passage was read. */
    QUIET_TIME,

    /** 주간 암송 — the week's memory verse was recited. */
    RECITATION,
}
