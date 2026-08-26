package com.example.core.domain.time

/**
 * Deterministic time provider abstraction for A-RISH.
 * Allows reproducible temporal testing (deadlines, TTL, expiry) without system clock flakiness.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun currentEpochSeconds(): Long = currentTimeMillis() / 1000L
}

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

class TestTimeProvider(private var fixedTime: Long = 1_700_000_000_000L) : TimeProvider {
    override fun currentTimeMillis(): Long = fixedTime

    fun setTime(epochMillis: Long) {
        fixedTime = epochMillis
    }

    fun advanceBy(millis: Long) {
        fixedTime += millis
    }

    fun advanceSeconds(seconds: Long) {
        fixedTime += (seconds * 1000L)
    }

    fun advanceDays(days: Long) {
        fixedTime += (days * 86_400_000L)
    }
}
