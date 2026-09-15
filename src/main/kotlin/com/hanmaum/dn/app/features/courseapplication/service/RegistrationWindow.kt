package com.hanmaum.dn.app.features.courseapplication.service

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * When an external course takes applications. A null bound is open on that side.
 *
 * Built with [of] from the API's raw strings, which come in two dialects: the documented
 * contract (`2026-08-01T00:00:00+02:00`, null) and the live deployment it replaces
 * (`2026-08-01 00:00:00` in Berlin time, `0000-00-00 00:00:00` for "no value").
 */
data class RegistrationWindow(
    val startsAt: OffsetDateTime?,
    val endsAt: OffsetDateTime?,
) {
    fun isOpen(now: Instant): Boolean =
        (startsAt == null || !now.isBefore(startsAt.toInstant())) &&
            (endsAt == null || !now.isAfter(endsAt.toInstant()))

    /**
     * 상시 접수: open now, and the end is so far away it is not a real deadline. The
     * congregation writes that as an end date in 2099 rather than leaving it empty.
     *
     * Only a label. The end date is still honoured — the day it passes, [isOpen] is false.
     */
    fun isAlwaysOpen(now: Instant): Boolean {
        if (!isOpen(now)) return false
        val horizon = now.atZone(BERLIN).plusYears(ALWAYS_OPEN_YEARS).toInstant()
        return endsAt == null || endsAt.toInstant().isAfter(horizon)
    }

    companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        const val ALWAYS_OPEN_YEARS = 10L

        private val LEGACY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /**
         * Null when either bound is present but unreadable. The caller treats such a course as
         * closed: reading an unknown value as "no bound" would open a course nobody opened.
         */
        fun of(
            startsAtRaw: String?,
            endsAtRaw: String?,
        ): RegistrationWindow? {
            val start = parse(startsAtRaw) ?: return null
            val end = parse(endsAtRaw) ?: return null
            return RegistrationWindow(start.value, end.value)
        }

        /** Wraps the result so "no value" (Parsed(null)) and "unreadable" (null) stay distinct. */
        private class Parsed(
            val value: OffsetDateTime?,
        )

        private fun parse(raw: String?): Parsed? {
            val trimmed = raw?.trim()
            if (trimmed.isNullOrEmpty() || trimmed.startsWith("0000-00-00")) return Parsed(null)
            return try {
                Parsed(OffsetDateTime.parse(trimmed))
            } catch (_: DateTimeParseException) {
                try {
                    Parsed(LocalDateTime.parse(trimmed, LEGACY_FORMAT).atZone(BERLIN).toOffsetDateTime())
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
