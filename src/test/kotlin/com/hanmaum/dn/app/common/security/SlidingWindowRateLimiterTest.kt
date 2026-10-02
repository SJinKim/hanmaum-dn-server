package com.hanmaum.dn.app.common.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlidingWindowRateLimiterTest {
    private class MutableClock(
        var now: Instant = Instant.parse("2026-10-02T10:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }

    private val clock = MutableClock()

    @Test
    fun `allows up to the limit per key and refuses the next call`() {
        val limiter = SlidingWindowRateLimiter(2, Duration.ofMinutes(1), clock = clock)

        assertTrue(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
    }

    @Test
    fun `a key is allowed again once its calls leave the window`() {
        val limiter = SlidingWindowRateLimiter(1, Duration.ofMinutes(1), clock = clock)

        assertTrue(limiter.tryAcquire("a"))
        clock.advance(Duration.ofSeconds(59))
        assertFalse(limiter.tryAcquire("a"))
        clock.advance(Duration.ofSeconds(1))
        assertTrue(limiter.tryAcquire("a"))
    }

    @Test
    fun `a full map drops expired keys before taking a new one`() {
        val limiter = SlidingWindowRateLimiter(1, Duration.ofMinutes(1), maxKeys = 2, clock = clock)
        limiter.tryAcquire("a")
        limiter.tryAcquire("b")

        clock.advance(Duration.ofMinutes(1))

        assertTrue(limiter.tryAcquire("c"))
        assertEquals(1, limiter.trackedKeys())
    }

    @Test
    fun `a full map with only live keys refuses new keys but serves known ones`() {
        val limiter = SlidingWindowRateLimiter(2, Duration.ofMinutes(1), maxKeys = 2, clock = clock)
        limiter.tryAcquire("a")
        limiter.tryAcquire("b")

        assertFalse(limiter.tryAcquire("c"))
        assertTrue(limiter.tryAcquire("a"))
        assertEquals(2, limiter.trackedKeys())
    }
}
