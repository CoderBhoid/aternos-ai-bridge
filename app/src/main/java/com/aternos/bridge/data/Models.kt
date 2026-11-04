package com.aternos.bridge.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents server status extracted from the Aternos DOM.
 */
enum class ServerStatus(val displayName: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    STARTING("Starting..."),
    STOPPING("Stopping..."),
    QUEUE("In Queue"),
    SAVING("Saving..."),
    UNKNOWN("Checking...");

    companion object {
        fun fromText(text: String): ServerStatus {
            val lower = text.lowercase().trim()
            return when {
                lower.contains("online") -> ONLINE
                lower.contains("offline") -> OFFLINE
                lower.contains("starting") || lower.contains("preparing") || lower.contains("loading") -> STARTING
                lower.contains("stopping") -> STOPPING
                lower.contains("queue") || lower.contains("waiting") -> QUEUE
                lower.contains("saving") -> SAVING
                else -> UNKNOWN
            }
        }
    }
}

/**
 * A line captured from the Aternos live console stream.
 */
data class ConsoleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawText: String,
    val level: String = "INFO",
    val isAiAction: Boolean = false,
    val isSystemNotice: Boolean = false
)

/**
 * Status of an AI prompt processing request.
 */
enum class AiExecutionStatus {
    PENDING,
    EXECUTING_COMMAND,
    COMMAND_SUCCESS,
    COMMAND_BLOCKED,
    CHAT_REPLIED,
    RATE_LIMITED,
    ERROR
}

/**
 * Historical record of an AI prompt intercepted from Minecraft chat.
 */
data class AiHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val player: String,
    val prompt: String,
    val responseText: String? = null,
    val commandDispatched: String? = null,
    val explanation: String? = null,
    val status: AiExecutionStatus = AiExecutionStatus.PENDING,
    val blockedReason: String? = null,
    val providerUsed: String = "Google GenAI"
)

/**
 * Detected Aternos server card on the servers list page.
 */
data class AternosServer(
    val id: String,
    val name: String,
    val address: String,
    val software: String,
    val status: String
)
