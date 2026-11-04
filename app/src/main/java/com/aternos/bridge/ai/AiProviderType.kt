package com.aternos.bridge.ai

/**
 * Enumeration of supported AI Providers with their endpoints and default models.
 */
enum class AiProviderType(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val popularModels: List<String>,
    val requiresCustomEndpoint: Boolean = false,
    val apiKeyPortalUrl: String = ""
) {
    GOOGLE_GENAI(
        id = "google",
        displayName = "Google AI Studio (Gemini / Gemma)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.0-flash",
        popularModels = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemma-2-27b-it",
            "gemma-2-9b-it"
        ),
        apiKeyPortalUrl = "https://aistudio.google.com/app/apikey"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        popularModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4-turbo",
            "gpt-3.5-turbo"
        ),
        apiKeyPortalUrl = "https://platform.openai.com/api-keys"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-5-haiku-20241022",
        popularModels = listOf(
            "claude-3-5-haiku-20241022",
            "claude-3-5-sonnet-20241022",
            "claude-3-haiku-20240307"
        ),
        apiKeyPortalUrl = "https://console.anthropic.com/settings/keys"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter (All Models)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.0-flash-001",
        popularModels = listOf(
            "google/gemini-2.0-flash-001",
            "anthropic/claude-3.5-haiku",
            "openai/gpt-4o-mini",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct"
        ),
        apiKeyPortalUrl = "https://openrouter.ai/keys"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq (Ultra Fast)",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        popularModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        ),
        apiKeyPortalUrl = "https://console.groq.com/keys"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        popularModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        apiKeyPortalUrl = "https://platform.deepseek.com/api_keys"
    ),
    XAI_GROK(
        id = "xai",
        displayName = "xAI Grok",
        defaultBaseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-2-latest",
        popularModels = listOf(
            "grok-2-latest",
            "grok-beta"
        ),
        apiKeyPortalUrl = "https://console.x.ai/"
    ),
    MISTRAL(
        id = "mistral",
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        popularModels = listOf(
            "mistral-small-latest",
            "mistral-large-latest",
            "codestral-latest",
            "open-mixtral-8x22b"
        ),
        apiKeyPortalUrl = "https://console.mistral.ai/api-keys/"
    ),
    NVIDIA_NIM(
        id = "nvidia",
        displayName = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "meta/llama-3.3-70b-instruct",
        popularModels = listOf(
            "meta/llama-3.3-70b-instruct",
            "mistralai/mistral-large-2-instruct",
            "google/gemma-2-27b-it"
        ),
        apiKeyPortalUrl = "https://build.nvidia.com/"
    ),
    TOGETHER_AI(
        id = "together",
        displayName = "Together AI",
        defaultBaseUrl = "https://api.together.xyz/v1",
        defaultModel = "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
        popularModels = listOf(
            "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
            "mistralai/Mixtral-8x7B-Instruct-v0.1",
            "Qwen/Qwen2.5-72B-Instruct-Turbo"
        ),
        apiKeyPortalUrl = "https://api.together.ai/settings/api-keys"
    ),
    FIREWORKS_AI(
        id = "fireworks",
        displayName = "Fireworks AI",
        defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
        defaultModel = "accounts/fireworks/models/llama-v3p1-70b-instruct",
        popularModels = listOf(
            "accounts/fireworks/models/llama-v3p1-70b-instruct",
            "accounts/fireworks/models/llama-v3p1-8b-instruct"
        ),
        apiKeyPortalUrl = "https://fireworks.ai/api-keys"
    ),
    CEREBRAS(
        id = "cerebras",
        displayName = "Cerebras (High Speed)",
        defaultBaseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "llama3.1-70b",
        popularModels = listOf(
            "llama3.1-70b",
            "llama3.1-8b"
        ),
        apiKeyPortalUrl = "https://cloud.cerebras.ai/"
    ),
    PERPLEXITY(
        id = "perplexity",
        displayName = "Perplexity AI",
        defaultBaseUrl = "https://api.perplexity.ai",
        defaultModel = "sonar",
        popularModels = listOf(
            "sonar",
            "sonar-pro"
        ),
        apiKeyPortalUrl = "https://www.perplexity.ai/settings/api"
    ),
    COHERE(
        id = "cohere",
        displayName = "Cohere",
        defaultBaseUrl = "https://api.cohere.com/v1",
        defaultModel = "command-r-plus-08-2024",
        popularModels = listOf(
            "command-r-plus-08-2024",
            "command-r-08-2024"
        ),
        apiKeyPortalUrl = "https://dashboard.cohere.com/api-keys"
    ),
    OLLAMA_LOCAL(
        id = "ollama",
        displayName = "Ollama (Local / LAN Server)",
        defaultBaseUrl = "http://10.0.2.2:11434/v1",
        defaultModel = "llama3.2",
        popularModels = listOf(
            "llama3.2",
            "gemma2",
            "mistral",
            "qwen2.5"
        ),
        requiresCustomEndpoint = true,
        apiKeyPortalUrl = ""
    ),
    CUSTOM_OPENAI_COMPATIBLE(
        id = "custom",
        displayName = "Custom OpenAI-Compatible API",
        defaultBaseUrl = "https://api.example.com/v1",
        defaultModel = "custom-model",
        popularModels = emptyList(),
        requiresCustomEndpoint = true,
        apiKeyPortalUrl = ""
    );

    companion object {
        fun fromId(id: String): AiProviderType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOOGLE_GENAI
        }
    }
}
