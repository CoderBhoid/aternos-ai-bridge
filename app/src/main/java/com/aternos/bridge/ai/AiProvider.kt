package com.aternos.bridge.ai

import com.aternos.bridge.data.AppConfig

/**
 * Result returned by an AI provider model execution.
 */
sealed class AgentResult {
    /**
     * The AI model decided a server command is needed.
     * @param command The Minecraft command to execute (without leading slash).
     * @param explanation Optional short message broadcast to in-game chat explaining the action.
     */
    data class ExecuteCommand(
        val command: String,
        val explanation: String? = null
    ) : AgentResult()

    /**
     * The AI model answered with a conversational in-game chat message without running a command.
     * @param replyText The text reply to whisper or broadcast.
     */
    data class ChatMessage(
        val replyText: String
    ) : AgentResult()

    /**
     * The AI model execution failed.
     * @param errorMessage Description of the error.
     */
    data class Error(
        val errorMessage: String
    ) : AgentResult()
}

/**
 * Common interface for all AI model providers.
 */
interface AiProvider {
    /**
     * Processes an incoming player prompt and determines if a command or chat reply is appropriate.
     */
    suspend fun processPrompt(player: String, prompt: String, config: AppConfig): AgentResult

    /**
     * Validates that the provided API key and model identifier are operational.
     */
    suspend fun validateApiKey(config: AppConfig): Result<String>
}
