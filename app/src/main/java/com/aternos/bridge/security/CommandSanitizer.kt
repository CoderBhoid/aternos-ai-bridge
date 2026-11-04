package com.aternos.bridge.security

/**
 * Utility for sanitizing player names, truncating prompts, and splitting in-game chat messages.
 */
object CommandSanitizer {

    private val PLAYER_NAME_REGEX = Regex("^[a-zA-Z0-9_]{1,16}$")

    fun sanitizePlayerName(player: String?): String {
        if (player.isNullOrBlank()) return "Player"
        val trimmed = player.trim()
        return if (PLAYER_NAME_REGEX.matches(trimmed)) trimmed else "Player"
    }

    fun sanitizeInboundPrompt(prompt: String, maxLength: Int = 250): String {
        val cleaned = prompt
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > maxLength) cleaned.substring(0, maxLength).trim() else cleaned
    }

    fun splitChatLines(text: String, maxLineLength: Int = 130): List<String> {
        val clean = text.replace(Regex("[\\r\\n]+"), " ").trim()
        if (clean.length <= maxLineLength) {
            return if (clean.isEmpty()) emptyList() else listOf(clean)
        }

        val chunks = mutableListOf<String>()
        val words = clean.split(" ")
        val currentChunk = StringBuilder()

        for (word in words) {
            if (currentChunk.isEmpty()) {
                if (word.length > maxLineLength) {
                    var start = 0
                    while (start < word.length) {
                        val end = (start + maxLineLength).coerceAtMost(word.length)
                        chunks.add(word.substring(start, end))
                        start = end
                    }
                } else {
                    currentChunk.append(word)
                }
            } else {
                if (currentChunk.length + 1 + word.length <= maxLineLength) {
                    currentChunk.append(" ").append(word)
                } else {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                    currentChunk.append(word)
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }

    /**
     * Formats an in-game broadcast message using the user-specified format template.
     * Replaces '/w @a' with 'say' so the Minecraft server console does not reject it.
     */
    fun formatInGameBroadcast(
        message: String,
        targetPlayer: String,
        template: String = "say [AI] {message}"
    ): String {
        val cleanMsg = message.replace("\"", "'").replace("\n", " ").trim()
        var effectiveTemplate = template
        if (effectiveTemplate.contains("/w @a") || effectiveTemplate.contains("w @a")) {
            effectiveTemplate = effectiveTemplate
                .replace("/w @a", "say")
                .replace("w @a", "say")
        }
        return effectiveTemplate
            .replace("{player}", targetPlayer)
            .replace("{message}", cleanMsg)
            .removePrefix("/")
            .trim()
    }
}
