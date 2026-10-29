package com.hanmaum.dn.app.features.attendance.service

import org.springframework.http.HttpStatus

/**
 * A rejected 장기결석자 request. [safeDetail] is a fixed text: it never echoes a memo, a
 * request value or an old phase, because the response and the log may both carry it.
 */
class LongAbsenceException(
    val status: HttpStatus,
    val code: String,
    val safeDetail: String,
) : RuntimeException(code) {
    companion object {
        const val NOT_CURRENT = "LONG_ABSENCE_NOT_CURRENT"
        const val VERSION_CONFLICT = "LONG_ABSENCE_VERSION_CONFLICT"
        const val INVALID_REQUEST = "LONG_ABSENCE_INVALID_REQUEST"
        const val MEMBER_NOT_FOUND = "LONG_ABSENCE_MEMBER_NOT_FOUND"

        fun notCurrent() =
            LongAbsenceException(
                HttpStatus.CONFLICT,
                NOT_CURRENT,
                "The member is not on the long-absence list for this phase anymore; reload.",
            )

        fun versionConflict() =
            LongAbsenceException(HttpStatus.CONFLICT, VERSION_CONFLICT, "The follow-up was changed meanwhile; reload and retry.")

        fun memberNotFound() = LongAbsenceException(HttpStatus.NOT_FOUND, MEMBER_NOT_FOUND, "Member not found.")
    }
}
