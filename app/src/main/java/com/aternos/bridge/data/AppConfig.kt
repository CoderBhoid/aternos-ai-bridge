package com.aternos.bridge.data

import com.aternos.bridge.ai.AiProviderType
import kotlinx.serialization.Serializable

/**
 * Persisted application configuration for AI provider, bridge parameters, and security rules.
 */
@Serializable
data class AppConfig(
    val provider: String = AiProviderType.GOOGLE_GENAI.id,
    val apiKey: String = "",
    val modelName: String = AiProviderType.GOOGLE_GENAI.defaultModel,
    val customBaseUrl: String = "",
    val triggerPrefix: String = "@server",
    val authorizedOperatorPlayer: String = "GamerBhoid",
    val enforceOperatorOnlyCommands: Boolean = true,
    val rateLimitSeconds: Int = 10,
    val maxOutboundPromptLength: Int = 250,
    val chatBroadcastFormat: String = "say [AI] {message}",
    val autoRestartServiceOnBoot: Boolean = false,
    val serverStatusGateEnabled: Boolean = true,
    val keepAliveIntervalSeconds: Int = 45,
    val lastSelectedServerId: String = ""
) {
    val providerType: AiProviderType
        get() = AiProviderType.fromId(provider)

    val effectiveBaseUrl: String
        get() = if (customBaseUrl.isNotBlank()) customBaseUrl.trim().removeSuffix("/") else providerType.defaultBaseUrl

    val effectiveModel: String
        get() = if (modelName.isNotBlank()) modelName.trim() else providerType.defaultModel
}
