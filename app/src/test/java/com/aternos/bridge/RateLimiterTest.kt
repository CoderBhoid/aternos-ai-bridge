package com.aternos.bridge

import com.aternos.bridge.security.RateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun testRateLimiterAllowsFirstPromptAndBlocksImmediateSecond() {
        val limiter = RateLimiter(defaultCooldownSeconds = 5)

        // First prompt from Steve
        val (allowed1, _) = limiter.checkAndRecord("Steve")
        assertTrue("First prompt should be allowed", allowed1)

        // Immediate second prompt from Steve
        val (allowed2, remainingSec) = limiter.checkAndRecord("Steve")
        assertFalse("Immediate second prompt should be rate limited", allowed2)
        assertTrue("Remaining seconds should be between 1 and 5", remainingSec in 1..5)
    }

    @Test
    fun testRateLimiterIsIsolatedPerPlayer() {
        val limiter = RateLimiter(defaultCooldownSeconds = 10)

        // Steve prompts
        val (steveAllowed, _) = limiter.checkAndRecord("Steve")
        assertTrue(steveAllowed)

        // Alex prompts immediately -> should be allowed because it's a different player
        val (alexAllowed, _) = limiter.checkAndRecord("Alex")
        assertTrue("Alex should be allowed independently of Steve", alexAllowed)

        // Steve tries again -> blocked
        val (steveAgain, _) = limiter.checkAndRecord("Steve")
        assertFalse(steveAgain)
    }
}
