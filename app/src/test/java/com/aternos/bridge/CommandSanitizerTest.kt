package com.aternos.bridge

import com.aternos.bridge.security.CommandSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSanitizerTest {

    @Test
    fun testPlayerNameSanitization() {
        assertEquals("Steve", CommandSanitizer.sanitizePlayerName("Steve"))
        assertEquals("Alex_123", CommandSanitizer.sanitizePlayerName("Alex_123"))
        assertEquals("Player", CommandSanitizer.sanitizePlayerName(""))
        assertEquals("Player", CommandSanitizer.sanitizePlayerName(null))
        assertEquals("Player", CommandSanitizer.sanitizePlayerName("Invalid!Player@Name"))
    }

    @Test
    fun testInboundPromptTruncationAndCleanup() {
        val longPrompt = "a".repeat(300)
        val sanitized = CommandSanitizer.sanitizeInboundPrompt(longPrompt, maxLength = 250)
        assertEquals(250, sanitized.length)

        val dirtyPrompt = "give me\n\n5 diamonds\r\nand   apples  "
        val cleaned = CommandSanitizer.sanitizeInboundPrompt(dirtyPrompt)
        assertEquals("give me 5 diamonds and apples", cleaned)
    }

    @Test
    fun testChatLineSplitting() {
        val shortText = "This is a short reply under 130 characters."
        val shortChunks = CommandSanitizer.splitChatLines(shortText, maxLineLength = 130)
        assertEquals(1, shortChunks.size)
        assertEquals(shortText, shortChunks[0])

        val longText = "This is a very long message designed to simulate an in-game AI monologue where the model explains how to craft Netherite armor step by step in full detail without overflowing chat boundaries."
        val chunks = CommandSanitizer.splitChatLines(longText, maxLineLength = 80)
        assertTrue("Expected multiple chunks for long message", chunks.size > 1)
        for (chunk in chunks) {
            assertTrue("Chunk length (${chunk.length}) must not exceed limit 80", chunk.length <= 80)
        }
    }

    @Test
    fun testBroadcastFormatting() {
        val legacyConverted = CommandSanitizer.formatInGameBroadcast(
            message = "Giving Steve 3 diamonds",
            targetPlayer = "Steve",
            template = "/w @a [AI] {message}"
        )
        assertEquals("say [AI] Giving Steve 3 diamonds", legacyConverted)

        val defaultFormatted = CommandSanitizer.formatInGameBroadcast(
            message = "Server restart soon",
            targetPlayer = "Steve"
        )
        assertEquals("say [AI] Server restart soon", defaultFormatted)

        val playerTell = CommandSanitizer.formatInGameBroadcast(
            message = "Here is your diamond",
            targetPlayer = "Steve",
            template = "tell {player} [AI] {message}"
        )
        assertEquals("tell Steve [AI] Here is your diamond", playerTell)
    }
}
