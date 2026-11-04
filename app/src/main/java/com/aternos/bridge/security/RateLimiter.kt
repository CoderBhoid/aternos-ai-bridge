package com.aternos.bridge.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe rate limiter that prevents prompt spam and API credit burnout.
 * Tracks timestamps per player username with automatic eviction of stale records.
 */
class RateLimiter(private val defaultCooldownSeconds: Int = 10) {

    private val playerLastPromptTime = ConcurrentHashMap<String, Long>()

    /**
     * Attempts to acquire a rate limit slot for the specified player.
     *
     * @param player The Minecraft player username.
     * @param customCooldownSec Optional override cooldown in seconds.
     * @return Pair of Boolean (true if allowed, false if rate limited) and remaining cooldown seconds.
     */
    fun checkAndRecord(player: String, customCooldownSec: Int? = null): Pair<Boolean, Long> {
        val normalizedPlayer = player.trim().lowercase()
        val cooldownMillis = (customCooldownSec ?: defaultCooldownSeconds) * 1000L
        val now = System.currentTimeMillis()

        val lastTime = playerLastPromptTime[normalizedPlayer] ?: 0L
        val timeSinceLast = now - lastTime

        if (timeSinceLast < cooldownMillis) {
            val remainingSec = ((cooldownMillis - timeSinceLast) + 999) / 1000
            return Pair(false, remainingSec)
        }

        playerLastPromptTime[normalizedPlayer] = now

        // Periodic maintenance cleanup if map exceeds 500 entries
        if (playerLastPromptTime.size > 500) {
            val staleThreshold = now - (60 * 60 * 1000L) // 1 hour
            playerLastPromptTime.entries.removeIf { it.value < staleThreshold }
        }

        return Pair(true, 0L)
    }

    /**
     * Clears rate limit records (e.g. when testing or reset).
     */
    fun reset() {
        playerLastPromptTime.clear()
    }
}
