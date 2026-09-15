package com.hanmaum.dn.app.features.courseapplication.service

import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationWindowTest {
    // 2026-09-14 12:00 in Berlin (CEST).
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `the legacy dialect is read as Berlin time and zero dates as no bound`() {
        val window = assertNotNull(RegistrationWindow.of("0000-00-00 00:00:00", "2099-03-12 23:59:59"))

        assertNull(window.startsAt)
        assertEquals(OffsetDateTime.parse("2099-03-12T23:59:59+01:00"), window.endsAt)
    }

    @Test
    fun `the documented ISO dialect and null are read as they are`() {
        val window = assertNotNull(RegistrationWindow.of("2026-08-01T00:00:00+02:00", null))

        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"), window.startsAt)
        assertNull(window.endsAt)
    }

    @Test
    fun `an unreadable bound yields no window rather than an open one`() {
        assertNull(RegistrationWindow.of("next monday", "2026-12-31 23:59:59"))
    }

    @Test
    fun `일대일 제자양육 with no start and an end in 2099 is open and 상시 접수`() {
        val window = assertNotNull(RegistrationWindow.of("0000-00-00 00:00:00", "2099-03-12 23:59:59"))

        assertTrue(window.isOpen(now))
        assertTrue(window.isAlwaysOpen(now))
    }

    @Test
    fun `a started course ending in 2099 is also 상시 접수 — the start does not matter`() {
        // 파더와이즈 7기: started 2024-01-23, ends 2099.
        val window = assertNotNull(RegistrationWindow.of("2024-01-23 09:00:00", "2099-02-21 23:59:59"))

        assertTrue(window.isAlwaysOpen(now))
    }

    @Test
    fun `an ordinary deadline is open but not 상시 접수`() {
        val window = assertNotNull(RegistrationWindow.of("2026-09-01 00:00:00", "2026-09-30 23:59:59"))

        assertTrue(window.isOpen(now))
        assertFalse(window.isAlwaysOpen(now))
    }

    @Test
    fun `a passed deadline and a future start are both closed`() {
        val passed = assertNotNull(RegistrationWindow.of("2026-01-22 07:00:00", "2026-02-28 23:59:59"))
        val future = assertNotNull(RegistrationWindow.of("2026-10-01 00:00:00", "2026-10-31 23:59:59"))

        assertFalse(passed.isOpen(now))
        assertFalse(future.isOpen(now))
        assertFalse(passed.isAlwaysOpen(now))
    }

    @Test
    fun `both bounds are inclusive`() {
        val window =
            assertNotNull(RegistrationWindow.of("2026-09-14T12:00:00+02:00", "2026-09-14T12:00:00+02:00"))

        assertTrue(window.isOpen(now))
    }

    @Test
    fun `an end ten years away or less is a real deadline`() {
        val window = assertNotNull(RegistrationWindow.of(null, "2036-09-14T12:00:00+02:00"))

        assertFalse(window.isAlwaysOpen(now))
    }
}
