package com.hanmaum.dn.app.features.attendance.domain

/** How a long-absence phase ended (HDN-379). */
enum class AbsencePhaseEndReason {
    /** A qualifying Sunday check-in after the anchor; closedOn is the visit date. */
    CHECK_IN,

    /** A pastor or admin marked the member as back without a check-in. */
    MANUAL_RETURN,
}

/** What one history entry of a phase records; the entry holds the new value. */
enum class AbsenceChangeKind {
    CONTACT_CHANGED,
    MEMO_CHANGED,
}

enum class AbsenceChangeOrigin {
    /** A write through the API, with actor and server time. */
    ACTION,

    /** The value a phase had when HDN-379 was introduced; actor and time may be unknown. */
    LEGACY_SNAPSHOT,
}
