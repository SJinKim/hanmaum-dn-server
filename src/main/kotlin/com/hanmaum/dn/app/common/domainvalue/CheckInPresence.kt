package com.hanmaum.dn.app.common.domainvalue

/**
 * What the server could establish about where a member was when they checked in.
 *
 * This is evidence attached to the record, never a gate in front of it: a check-in is
 * accepted on the time window alone. The church building has no WiFi, so an indoor fix
 * falls back to GPS through walls or to cell towers, whose accuracy runs wider than the
 * geofence radius itself. Blocking on that would lock out people who are demonstrably
 * sitting in the room, so a bad reading costs a label here, not someone's attendance.
 *
 * [UNCONFIRMED] is the reason this is not a boolean. Collapsing "declined to share" and
 * "the fix was too vague to judge" into [OUTSIDE] would put a claim in the record that the
 * data does not support — an admin would read "was not there" where the truth is "we do
 * not know".
 */
enum class CheckInPresence {
    /** A position was sent, precise enough to judge, and it fell inside the radius. */
    IN_PLACE,

    /** A position was sent, precise enough to judge, and it fell outside the radius. */
    OUTSIDE,

    /**
     * No position was sent, none could be judged, or the deployment has no geofence.
     * Also the value every row recorded before this shipped carries — it was never measured.
     */
    UNCONFIRMED,
}
