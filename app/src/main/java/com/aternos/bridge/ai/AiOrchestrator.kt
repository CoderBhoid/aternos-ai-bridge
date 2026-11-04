package com.aternos.bridge.ai

import android.util.Log
import com.aternos.bridge.data.AppConfig
import com.aternos.bridge.security.CommandSanitizer
import com.aternos.bridge.security.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * High-level AI Orchestrator that coordinates AI providers, prompt sanitization,
 * rate limiting, timeout handling, and automatic retry on transient failure.
 */
class AiOrchestrator(
    private val rateLimiter: RateLimiter = RateLimiter()
) {

    private val tag = "AiOrchestrator"

    private val googleProvider = GoogleGenAiProvider()
    private val universalOpenAiProvider = UniversalOpenAiProvider()
    private val anthropicProvider = AnthropicProvider()

    /**
     * Resolves the appropriate provider implementation for the given provider type.
     */
    fun getProvider(type: AiProviderType): AiProvider {
        return when (type) {
            AiProviderType.GOOGLE_GENAI -> googleProvider
            AiProviderType.ANTHROPIC -> anthropicProvider
            else -> universalOpenAiProvider
        }
    }

    /**
     * Processes an incoming in-game player trigger prompt.
     *
     * @param rawPlayer The raw player username from chat.
     * @param rawPrompt The prompt text after '!ai'.
     * @param config The current active app configuration.
     * @return An [AgentResult] describing the action to perform.
     */
    suspend fun processPrompt(
        rawPlayer: String,
        rawPrompt: String,
        config: AppConfig
    ): AgentResult = withContext(Dispatchers.IO) {
        val player = CommandSanitizer.sanitizePlayerName(rawPlayer)
        val prompt = CommandSanitizer.sanitizeInboundPrompt(rawPrompt, config.maxOutboundPromptLength)

        if (prompt.isBlank()) {
            return@withContext AgentResult.ChatMessage("Hello $player! How can I assist you?")
        }

        // 1. Rate Limiting Check
        val (allowed, remainingSec) = rateLimiter.checkAndRecord(player, config.rateLimitSeconds)
        if (!allowed) {
            Log.w(tag, "Player '$player' rate limited for ${remainingSec}s")
            return@withContext AgentResult.ChatMessage(
                "Please wait ${remainingSec}s before sending another AI prompt."
            )
        }

        val provider = getProvider(config.providerType)
        Log.i(tag, "Dispatching prompt from '$player' to [${config.providerType.displayName} | ${config.effectiveModel}]")

        // 2. Execute with Retry-Once on Transient Network Failure
        var result: AgentResult
        try {
            result = provider.processPrompt(player, prompt, config)
            if (result is AgentResult.Error && isTransientError(result.errorMessage)) {
                Log.w(tag, "Transient failure: ${result.errorMessage}. Retrying in 1s...")
                delay(1000)
                result = provider.processPrompt(player, prompt, config)
            }
        } catch (e: Exception) {
            Log.w(tag, "Exception during first attempt: ${e.message}. Retrying once...")
            delay(1000)
            result = try {
                provider.processPrompt(player, prompt, config)
            } catch (retryEx: Exception) {
                AgentResult.Error("AI request failed: ${retryEx.message}")
            }
        }

        result
    }

    /**
     * Validates connection with the configured provider and API key.
     */
    suspend fun validateConnection(config: AppConfig): Result<String> {
        val provider = getProvider(config.providerType)
        return provider.validateApiKey(config)
    }

    private fun isTransientError(msg: String): Boolean {
        val lower = msg.lowercase()
        return lower.contains("timeout") ||
                lower.contains("503") ||
                lower.contains("502") ||
                lower.contains("429") ||
                lower.contains("connection reset") ||
                lower.contains("econnreset")
    }
}
