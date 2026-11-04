# Aternos AI Console Bridge (Android)

A production-ready native Android application built with **Kotlin** and **Jetpack Compose** (Material 3 Dark Slate theme) that bridges in-game Minecraft chat commands with multi-provider AI models (Google Gemini / Gemma, OpenAI GPT-4o, Anthropic Claude 3.5, OpenRouter, Groq, DeepSeek, Mistral, NVIDIA NIM, and 10+ others) via an embedded, foreground-managed Aternos server web console.

---

## 🌟 Features

- **Multi-Provider AI Engine**: Native support for **Google AI Studio (Gemini 2.0 Flash, 1.5 Flash, Gemma 2)**, **OpenAI (GPT-4o / GPT-4o-mini)**, **Anthropic Claude (3.5 Sonnet / Haiku)**, **OpenRouter (Unified)**, **Groq (Ultra-Fast)**, **DeepSeek (V3 / R1)**, **xAI Grok**, **Mistral AI**, **NVIDIA NIM**, **Together AI**, **Fireworks AI**, **Cerebras**, **Perplexity**, **Cohere**, **Ollama (Local / LAN)**, and custom OpenAI-compatible endpoints with custom model entry and tool calling (`execute_minecraft_command`).
- **Encrypted Storage**: Sensitive API keys and endpoint credentials are stored securely via `EncryptedSharedPreferences` backed by hardware-backed Android Keystore (`MasterKey` AES-256).
- **Embedded Handshake & Multi-Server Navigation**: Embedded WebView for Aternos login, Google OAuth, and automatic routing to the server console. If your account has multiple servers, an intuitive server picker appears.
- **In-Game Trigger Hook (`console_hook.js`)**: Real-time DOM `MutationObserver` detects chat triggers (`!ai <prompt>`) directly from Aternos console WebSocket stream.
- **Strict Security Guardrail**: Unbypassable allowlist-leaning filter blocking destructive commands (`stop`, `restart`, `reload`), privilege escalation (`op`, `deop`, `whitelist`), player discipline (`ban`, `ban-ip`, `kick`, `pardon`), and nested `/execute ... run ...` exploits.
- **Tamper-Proof JSON-Quoted Dispatch**: Commands dispatched to the console DOM are escaped via `JSONObject.quote()` and submitted with synthetic `input` and `Enter` keydown events.
- **Android 14+ Foreground Service**: Continuous background operation with `foregroundServiceType="dataSync"`, bounded periodic WakeLock renewal (10 min timer), and 45s synthetic focus event keep-alive.
- **Edge Hardening**:
  - Cloudflare Turnstile challenge detection with heads-up alert and haptic vibration.
  - Server status gating (prevents parsing when server is offline or in queue).
  - Paced in-game chat broadcasts (`/w @a [AI] <message>`) split into &lt;140 character chunks with ~400ms delay.
- **Picture-in-Picture (PiP) & Test Bench**: Compact floating PiP mode and built-in interactive simulator to test prompts, guardrails, and AI responses without an active server.

---

## 🛠️ Architecture & Package Structure

```
com.aternos.bridge
├── AternosBridgeApp.kt           # Application lifecycle entry & Keystore init
├── MainActivity.kt               # Edge-to-edge UI host & PiP lifecycle handler
├── ai/
│   ├── AgentResult.kt            # ExecuteCommand vs ChatMessage vs Error
│   ├── AiOrchestrator.kt         # Model coordinator, timeout, retry & rate limiting
│   ├── AiProvider.kt             # Common AI provider interface
│   ├── AiProviderType.kt         # Enum of 18+ supported AI providers
│   ├── AnthropicProvider.kt      # Anthropic Claude 3.5 tool-use client
│   ├── GoogleGenAiProvider.kt    # Google GenAI SDK (Gemini / Gemma) provider
│   └── UniversalOpenAiProvider.kt# Universal OpenAI/OpenRouter/Groq/etc. client
├── bridge/
│   ├── AternosConsoleClient.kt   # Custom WebViewClient & hook injector
│   ├── AternosNativeBridge.kt    # @JavascriptInterface exposed as window.AndroidBridge
│   ├── BridgeEventManager.kt     # Central reactive StateFlow event bus
│   └── CommandDispatcher.kt      # Safe JSON-quoted script generator & paced dispatcher
├── data/
│   ├── AppConfig.kt              # App configuration model
│   ├── EncryptedConfigRepository.kt # AndroidX Security Crypto storage
│   └── Models.kt                 # ConsoleLogEntry, AiHistoryEntry, ServerStatus
├── security/
│   ├── CommandSanitizer.kt       # Player sanitization, prompt truncation, chat chunker
│   ├── RateLimiter.kt            # Per-player token bucket rate limiter
│   └── SecurityGuardrailFilter.kt# Allowlist security & composite/nested command validator
├── service/
│   ├── AternosBridgeService.kt   # Foreground Service (dataSync) & headless WebView
│   ├── ServiceNotificationHelper.kt # Persistent notification & Cloudflare alert channels
│   └── WakeLockManager.kt        # Bounded partial wake lock with periodic renewal
└── ui/
    ├── components/               # ServerStatusPill, ConsoleLogView, AiHistoryView, etc.
    ├── navigation/               # AppNavHost (Setup, Handshake, HUD)
    ├── screens/                  # SetupScreen, HandshakeScreen, HudScreen, SimulatorScreen
    └── theme/                    # Dark Slate Material 3 theme & monospace typography
```

---

## 🔒 Security Guardrail Specifications

Commands proposed by an AI model or triggered in Minecraft chat are subject to strict checks before console execution:

1. **Normalization**: Command is lowercased, stripped of leading slashes, and redundant spaces are collapsed.
2. **Composite / Chaining Prohibition**: Semicolons (`;`), logical operators (`&&`, `||`, `|`), newlines (`\n`, `\r`), and backticks (`` ` ``) are unconditionally blocked.
3. **Prohibited Roots**:
   - Destruction / Restart: `stop`, `restart`, `reload`, `save-off`, `save-all`
   - Permissions: `op`, `deop`, `whitelist`
   - Player Discipline: `ban`, `ban-ip`, `pardon`, `pardon-ip`, `kick`
   - Mass Kill: `kill @a`, `kill @e`
4. **Nested Execute Inspection**: Commands matching `/execute ... run <subcommand>` are recursively unwrapped and validated against all blocked sub-commands.
5. **Allowlist Matching**: Only valid gameplay commands (`time`, `weather`, `give`, `tp`, `teleport`, `gamemode`, `summon`, `effect`, `say`, `tellraw`, `w`, `msg`, `title`, `particle`, `playsound`, `clear`, `difficulty`, `seed`, `locate`, `scoreboard`) are dispatched.

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- JDK 17+
- Android SDK (minSdk 31, targetSdk 35, compileSdk 35)

### Build & Run Debug
```bash
# Clean and assemble debug APK
./gradlew assembleDebug

# Run unit tests (Security filter, rate limiter, sanitizers)
./gradlew test

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚠️ Important Play Store Review Caveats

> [!WARNING]
> - **Foreground Service (`FOREGROUND_SERVICE_DATA_SYNC`)**: Google Play policies closely scrutinize persistent background foreground services. The app uses `dataSync` to maintain the active WebSocket log stream between the Aternos console and the AI engine while Minecraft is running in full screen. When submitting to Google Play, ensure you provide a video demonstration demonstrating why the persistent service is indispensable to the user's workflow.
> - **Continuous Background WebView**: Continuous background WebView execution consumes CPU/battery. The app implements bounded WakeLocks (10 min renewal cycle) and 45s keep-alive pings to minimize overhead.

---

## ⚖️ Disclaimer & Terms of Service Notice

This application is an independent third-party tool that automates interaction with the Aternos web console via DOM scripting. It is not affiliated with, endorsed by, or sponsored by Aternos GmbH or Mojang Synergies AB. Aternos does not provide an official public API; this software interacts with the standard web interface. Users are responsible for ensuring that their use of automation complies with Aternos Terms of Service.
