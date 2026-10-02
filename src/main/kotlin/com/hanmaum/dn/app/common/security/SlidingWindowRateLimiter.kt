package com.hanmaum.dn.app.common.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory limiter for the anonymous write paths (#237): at most [limit] calls per key
 * within [window].
 *
 * The map holds at most [maxKeys] keys. When it is full, expired keys are dropped first; if
 * it is still full, calls for new keys are refused until old ones expire. Under a flood from
 * many addresses the endpoint is refused for a while rather than growing the heap without bound.
 *
 * The state is per instance and is lost on restart. That is enough for one backend container.
 */
class SlidingWindowRateLimiter(
    private val limit: Int,
    private val window: Duration,
    private val maxKeys: Int = DEFAULT_MAX_KEYS,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val attempts = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    init {
        require(limit > 0) { "limit must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
        require(maxKeys > 0) { "maxKeys must be positive" }
    }

    /** Records a call for [key] and returns false if the key is over its limit. */
    fun tryAcquire(key: String): Boolean {
        val now = clock.instant()
        if (!attempts.containsKey(key) && attempts.size >= maxKeys) {
            evictExpired(now)
            if (attempts.size >= maxKeys) return false
        }
        val queue = attempts.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            dropExpired(queue, now)
            if (queue.size >= limit) return false
            queue.addLast(now)
            return true
        }
    }

    internal fun trackedKeys(): Int = attempts.size

    private fun evictExpired(now: Instant) {
        attempts.entries.removeIf { (_, queue) ->
            synchronized(queue) {
                dropExpired(queue, now)
                queue.isEmpty()
            }
        }
    }

    private fun dropExpired(
        queue: ArrayDeque<Instant>,
        now: Instant,
    ) {
        val cutoff = now.minus(window)
        while (queue.firstOrNull()?.isAfter(cutoff) == false) queue.removeFirst()
    }

    companion object {
        const val DEFAULT_MAX_KEYS = 10_000
    }
}
