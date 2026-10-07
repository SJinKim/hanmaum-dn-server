package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.ConflictingDefinitionDto

/**
 * A check-in window that would overlap [conflicting], another active window on the same day.
 *
 * [message] is Korean and written for the admin; the dashboard may show it as it is.
 */
class AttendanceWindowOverlapException(
    val conflicting: ConflictingDefinitionDto,
) : RuntimeException(
        "'${conflicting.title}' (${conflicting.windowStart}–${conflicting.windowEnd}) 시간과 겹칩니다.",
    ) {
    val fieldErrors: Map<String, String> =
        mapOf(
            "windowStart" to "다른 출석 시간과 겹칩니다.",
            "windowEnd" to "다른 출석 시간과 겹칩니다.",
        )
}
