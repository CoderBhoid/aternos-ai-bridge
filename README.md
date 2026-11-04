# Aternos AI Console Bridge (Android)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/CoderBhoid/aternos-ai-bridge)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Light%20Orange-EA580C.svg)](https://m3.material.io)
[![Android](https://img.shields.io/badge/Android-SDK%2031%E2%80%9335-3DDC84.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A production-grade, native Android application built with **Kotlin** and **Jetpack Compose** (Material 3 Light Orange theme) that bridges in-game Minecraft chat commands and assistant queries with multi-provider Large Language Models (Google Gemini, OpenAI GPT-4o, Anthropic Claude 3.5, OpenRouter, Groq, DeepSeek, Ollama, and 15+ others). The app interacts directly with an embedded, headless foreground Aternos server web console via DOM mutation observation and safe script dispatch.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [1. Clone Repository](#1-clone-repository)
  - [2. Environment & Android SDK Setup](#2-environment--android-sdk-setup)
  - [3. Build Debug APK](#3-build-debug-apk)
  - [4. Install & Launch on Device](#4-install--launch-on-device)
  - [5. Initial In-App Configuration](#5-initial-in-app-configuration)
- [Architecture & Internals](#architecture--internals)
  - [Directory Structure](#directory-structure)
  - [In-Game Execution Lifecycle](#in-game-execution-lifecycle)
  - [Console Hook & DOM Mutation Engine](#console-hook--dom-mutation-engine)
  - [Anti-Bot & Turnstile Hardening](#anti-bot--turnstile-hardening)
  - [Multi-Provider AI Abstraction](#multi-provider-ai-abstraction)
  - [Curated Minecraft Wiki Engine](#curated-minecraft-wiki-engine)
- [Security & Guardrail Engine](#security--guardrail-engine)
  - [Command Normalization & Tree Inspection](#command-normalization--tree-inspection)
  - [Blocked Commands & Exploit Prevention](#blocked-commands--exploit-prevention)
  - [Authorized Operator Access Model](#authorized-operator-access-model)
  - [Token Bucket Rate Limiting](#token-bucket-rate-limiting)
  - [Encrypted Hardware Keystore Storage](#encrypted-hardware-keystore-storage)
- [In-Game Chat Usage Guide](#in-game-chat-usage-guide)
  - [Assistant Prompts & Commands](#assistant-prompts--commands)
  - [Supported Command Reference](#supported-command-reference)
- [Configuration Reference](#configuration-reference)
- [Available Gradle Tasks](#available-gradle-tasks)
- [Testing & Quality Assurance](#testing--quality-assurance)
  - [Executing Unit Tests](#executing-unit-tests)
  - [Test Suite Breakdown](#test-suite-breakdown)
  - [Adding New Tests](#adding-new-tests)
- [Deployment & Release Packaging](#deployment--release-packaging)
  - [Keystore Generation](#keystore-generation)
  - [Release Build Creation](#release-build-creation)
  - [Google Play Store Policy Caveats](#google-play-store-policy-caveats)
- [Troubleshooting & Diagnostics](#troubleshooting--diagnostics)
- [Disclaimer & Terms of Service](#disclaimer--terms-of-service)

---

## Overview

Minecraft server administrators hosting on free services like Aternos lack native programmatic webhook endpoints or custom server plugins to integrate AI assistants. **Aternos AI Console Bridge** bridges this gap on Android by hosting an embedded, background-resilient Chromium WebView that authenticates to Aternos, tracks the real-time WebSocket console stream using a lightweight JavaScript `MutationObserver`, extracts player chat triggers (`@server <query>` or `!ai <prompt>`), routes queries through configured AI models, validates returned commands against an unbypassable security guardrail, and types sanitized commands or paced chat responses back into the Aternos server console.

---

## Key Features

- **Multi-Provider AI Engine (18+ Providers):**
  - First-party native integration with **Google AI Studio** (`gemini-2.0-flash`, `gemini-1.5-flash`, `gemini-1.5-pro`, `gemma-2-27b-it`, `gemma-2-9b-it`).
  - Unified HTTP tool-calling integration for **OpenAI** (`gpt-4o`, `gpt-4o-mini`), **Anthropic Claude** (`claude-3-5-haiku`, `claude-3-5-sonnet`), **OpenRouter** (unified routing), **Groq** (low-latency LLaMA 3.3 70B), **DeepSeek** (`deepseek-chat`, `deepseek-reasoner`), **xAI Grok**, **Mistral**, **NVIDIA NIM**, **Together AI**, **Fireworks AI**, **Cerebras**, **Perplexity**, **Cohere**, and self-hosted **Ollama** LAN servers.
- **Strict Security Guardrail System:**
  - Multi-tier command validator checking syntax normalization, nested `/execute ... run` unwrapping, composite chaining prohibition (`;`, `&&`, `|`), and hard blocks on destructive roots (`stop`, `restart`, `op`, `deop`, `whitelist`, `ban`, `kick`, `kill @a`).
  - Operator-only enforcement restricting command execution to a designated admin player while allowing regular players to use chat Q&A.
- **Hardened Headless WebView & Anti-Bot Bypass:**
  - Emulates desktop Chromium browser signatures (`navigator.webdriver = false`, standard plugin lists, `window.chrome`).
  - Suppresses WebView-identifying `X-Requested-With` headers using `WebSettingsCompat`.
  - Supports Google OAuth popups and secondary window creation (`onCreateWindow`).
  - Preserves Cloudflare Turnstile TLS/JA3 socket fingerprints by delegating challenge requests natively to Chromium.
- **Reliable Console DOM Typing:**
  - Multi-candidate selector matrix (`#console-input`, `.console-input-button`, `div[role=button]`, `form.requestSubmit()`).
  - Synthetic event lifecycle dispatch (`focus`, `input`, `change`, jQuery event dispatch, Enter keydown/keyup).
  - 80ms retry verification confirming DOM input clearance before confirming dispatch.
- **Curated Minecraft Wiki Engine:**
  - In-memory structured knowledge base containing updates through 1.20, 1.21 Tricky Trials, 1.21.4 Pale Garden (Creaking, Resin), 26.1 Tiny Takeover, and 26.2 Chaos Cubed (Sulfur Caves, Sulfur Cubes).
  - Live MediaWiki fallback querying the official Minecraft Wiki API for dynamic queries.
- **Modern Jetpack Compose UI:**
  - Material 3 Light Mode styling with warm Orange 600 accents (`#EA580C`), Slate card surfaces, and responsive layouts.
  - Interactive test bench simulator to test prompts, guardrails, and model outputs without connecting to an active server.
  - Picture-in-Picture (PiP) support for monitoring the console log while playing Minecraft on the same mobile device.
- **Foreground Background Service:**
  - Android 14+ compliant `FOREGROUND_SERVICE_DATA_SYNC` foreground service with persistent notifications.
  - Periodic bounded `WakeLock` management (10-minute renewal cycles) and 45-second keep-alive focus pings.
- **Hardware-Backed Keystore:**
  - Sensitive API keys, endpoints, and server IDs are encrypted using AES-256 GCM through AndroidX `EncryptedSharedPreferences`.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language & Tooling** | Kotlin 2.0.21, Java 17, Gradle 8.11.1 (Kotlin DSL) |
| **Android Architecture** | Android SDK 31–35 (targetSdk 35, compileSdk 35), Modern Single-Activity Architecture |
| **UI Framework** | Jetpack Compose BOM 2024.10.01, Material 3, Navigation Compose, Compose Extended Icons |
| **Security & Crypto** | AndroidX Security Crypto (`MasterKey` AES-256 GCM, `EncryptedSharedPreferences`) |
| **Networking & HTTP** | OkHttp 4.12.0, OkHttp Logging Interceptor, Kotlinx Serialization JSON 1.7.3 |
| **AI Client SDKs** | Google Generative AI Android SDK (`com.google.ai.client.generativeai:generativeai:0.9.0`), Universal OpenAI/Claude Engine |
| **WebView Engine** | AndroidX WebKit 1.12.1 (`WebSettingsCompat`), Chromium System WebView |
| **Concurrency** | Kotlin Coroutines 1.9.0 (`StateFlow`, `SharedFlow`, `Dispatchers.IO`) |
| **Testing** | JUnit 4.13.2, Kotlinx Coroutines Test, Espresso Core, Compose UI Test JUnit4 |

---

## Prerequisites

Before setting up the project locally, ensure you have the following installed on your development machine:

1. **Java Development Kit (JDK):** Version 17 or higher (OpenJDK 17 / Eclipse Temurin 17).
   ```bash
   java -version
   ```
2. **Android SDK Platform & Tools:**
   - Compile SDK: `35`
   - Build Tools: `35.0.0`
   - Android SDK Command-line Tools (`cmdline-tools;latest`)
   - Platform Tools (`platform-tools` with `adb`)
3. **Android Studio (Optional but Recommended):** Android Studio Ladybug (2024.2.1) or newer.
4. **Physical Android Device or Emulator:** Running Android 12 (API level 31) or higher.
5. **Aternos Account:** An active server on [aternos.org](https://aternos.org).
6. **AI Provider API Key:** An API key from Google AI Studio, OpenAI, Anthropic, OpenRouter, or Groq (or a running local Ollama instance).

---

## Getting Started

### 1. Clone Repository

```bash
git clone https://github.com/CoderBhoid/aternos-ai-bridge.git
cd aternos-ai-bridge
```

### 2. Environment & Android SDK Setup

Ensure your `ANDROID_HOME` or `ANDROID_SDK_ROOT` environment variable is set in your shell:

```bash
# macOS / Linux (in ~/.bashrc or ~/.zshrc)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin

# Verify ADB
adb version
```

Create a `local.properties` file in the project root if Android Studio or Gradle does not generate one automatically:

```properties
sdk.dir=/home/username/Android/Sdk
```

### 3. Build Debug APK

Compile the project and package the debug APK using the Gradle wrapper:

```bash
./gradlew assembleDebug
```

Upon completion, the signed debug APK will be available at:
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Install & Launch on Device

Connect your Android device via USB with **USB Debugging** enabled, or start an emulator:

```bash
# Verify connected devices
adb devices

# Install APK to device
./gradlew installDebug

# Or install manually via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the main activity
adb shell am start -n com.aternos.bridge.debug/com.aternos.bridge.MainActivity
```

### 5. Initial In-App Configuration

1. **Setup Screen:**
   - Select your preferred **AI Provider** (e.g., *Google AI Studio (Gemini / Gemma)*).
   - Enter your **API Key** (keys are immediately encrypted in the Android Keystore).
   - Select your desired **Model** (e.g., `gemini-2.0-flash`).
   - Specify your in-game **Authorized Operator Player** username (e.g., `GamerBhoid`).
   - Define your **Chat Broadcast Format** (defaults to `say [AI] {message}`).
   - Adjust the **Rate Limit** slider (defaults to 10 seconds per player).
   - Tap **Save & Proceed**.
2. **Handshake Screen:**
   - The embedded WebView will load `https://aternos.org/go/`.
   - Log in using your Aternos username and password, or tap **Sign in with Google** (popup OAuth windows are fully supported).
   - Complete any Cloudflare Turnstile human verification challenges if presented.
   - If your account manages multiple servers, select the target server from the dashboard list.
   - Once the live console terminal DOM appears, the app automatically navigates to the **HUD Screen** and starts the foreground background service.

---

## Architecture & Internals

### Directory Structure

```
aternos-ai-bridge/
├── .gitignore                        # Git exclusion rules for Android & Gradle builds
├── README.md                         # Comprehensive project documentation
├── build.gradle.kts                  # Root Gradle build script
├── settings.gradle.kts               # Gradle project & repository settings
├── gradle.properties                 # JVM arguments & AndroidX flags
├── gradlew                           # Unix Gradle wrapper executable
├── gradle/
│   └── libs.versions.toml            # Version catalog managing all dependencies
└── app/
    ├── build.gradle.kts              # Application-level build script & dependencies
    ├── proguard-rules.pro            # R8 / ProGuard obfuscation rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # Manifest permissions, activities & services
        │   ├── assets/
        │   │   └── console_hook.js   # Injected JavaScript MutationObserver
        │   ├── java/com/aternos/bridge/
        │   │   ├── AternosBridgeApp.kt          # Application lifecycle entrypoint
        │   │   ├── MainActivity.kt              # Edge-to-edge UI & PiP lifecycle handler
        │   │   ├── ai/
        │   │   │   ├── AgentResult.kt           # AI output types (Command vs Chat vs Error)
        │   │   │   ├── AiOrchestrator.kt        # Multi-turn coordinator, retries & context
        │   │   │   ├── AiProvider.kt            # Common AI client interface
        │   │   │   ├── AiProviderType.kt        # Enum of 18+ supported AI providers
        │   │   │   ├── AnthropicProvider.kt     # Claude 3.5 tool-use client
        │   │   │   ├── GoogleGenAiProvider.kt   # Official Google GenAI SDK client
        │   │   │   └── UniversalOpenAiProvider.kt # Universal OpenAI/OpenRouter/Groq client
        │   │   ├── bridge/
        │   │   │   ├── AternosConsoleClient.kt  # Custom WebViewClient & fingerprint spoofing
        │   │   │   ├── AternosNativeBridge.kt   # JavaScriptInterface (@JavascriptInterface)
        │   │   │   ├── BridgeEventManager.kt    # Reactive StateFlow event bus
        │   │   │   ├── CommandDispatcher.kt     # DOM command injector & paced broadcaster
        │   │   │   └── WebViewHolder.kt         # Persistent WebView container
        │   │   ├── data/
        │   │   │   ├── AppConfig.kt             # Configuration data class
        │   │   │   ├── EncryptedConfigRepository.kt # Hardware-backed encrypted storage
        │   │   │   └── Models.kt                # ConsoleLogEntry, AiHistoryEntry, ServerStatus
        │   │   ├── security/
        │   │   │   ├── CommandSanitizer.kt      # Output chunker, name cleaner, token replacer
        │   │   │   ├── RateLimiter.kt           # Per-player token bucket rate limiter
        │   │   │   └── SecurityGuardrailFilter.kt # Allowlist parser & exploit validator
        │   │   ├── service/
        │   │   │   ├── AternosBridgeService.kt  # Foreground Service (dataSync)
        │   │   │   ├── ServiceNotificationHelper.kt # Status notification & alerts
        │   │   │   └── WakeLockManager.kt       # Bounded CPU WakeLock coordinator
        │   │   ├── ui/
        │   │   │   ├── components/              # Reusable UI widgets
        │   │   │   │   ├── AiHistoryView.kt
        │   │   │   │   ├── CloudflareAlertBanner.kt
        │   │   │   │   ├── ConsoleLogView.kt
        │   │   │   │   ├── ManualCommandBar.kt
        │   │   │   │   └── ServerStatusPill.kt
        │   │   │   ├── navigation/
        │   │   │   │   └── AppNavigation.kt     # Jetpack Compose Navigation Graph
        │   │   │   ├── screens/
        │   │   │   │   ├── HandshakeScreen.kt   # WebView auth & server selector
        │   │   │   │   ├── HudScreen.kt         # Live monitoring dashboard & controls
        │   │   │   │   ├── SetupScreen.kt       # Settings & AI provider configuration
        │   │   │   │   └── SimulatorScreen.kt   # Standalone prompt & guardrail test bench
        │   │   │   └── theme/
        │   │   │       ├── Color.kt             # Material 3 Light Orange color palette
        │   │   │       ├── Theme.kt             # Light theme configuration
        │   │   │       └── Type.kt              # Typography & monospace console font
        │   │   └── wiki/
        │   │       └── MinecraftWikiEngine.kt   # Local & remote Minecraft knowledge engine
        │   └── res/                             # Android layouts, drawables & XML config
        └── test/java/com/aternos/bridge/
            ├── CommandSanitizerTest.kt          # Sanitization & chunking unit tests
            ├── MinecraftWikiEngineTest.kt       # Knowledge base & semantic matching tests
            ├── RateLimiterTest.kt               # Token bucket rate limiting tests
            └── SecurityGuardrailFilterTest.kt   # Exploit injection & allowlist tests
```

### In-Game Execution Lifecycle

```
[In-Game Player Chat]
   │  Player types: "@server can you give GamerBhoid 5 diamonds and make it day?"
   ▼
[Aternos WebSocket Log Stream]
   │  Output: "[Server thread/INFO]: <GamerBhoid> @server can you give GamerBhoid 5 diamonds..."
   ▼
[Aternos Console DOM]
   │  HTML div element appended to terminal output list
   ▼
[MutationObserver in console_hook.js]
   │  Detects node insertion, parses player name ("GamerBhoid") and prompt
   ▼
[AternosNativeBridge.kt]
   │  JavaScript calls: window.AndroidBridge.onConsoleMessage(rawLog)
   ▼
[AiOrchestrator.kt]
   │  1. Verifies server state is ONLINE (ServerStatusGate)
   │  2. Checks RateLimiter for "GamerBhoid" (Token Bucket)
   │  3. Consults MinecraftWikiEngine for contextual knowledge
   │  4. Invokes configured LLM with system prompt and tool definitions
   ▼
[AI Model Response]
   │  Tool Call: execute_minecraft_command("give GamerBhoid diamond 5")
   │  Assistant Text: "Giving you 5 diamonds and setting the time to morning!"
   ▼
[SecurityGuardrailFilter.kt]
   │  1. Normalizes command: "give GamerBhoid diamond 5"
   │  2. Checks for injection/chaining: No semicolons, pipes, or newlines
   │  3. Checks blocked roots: Not stop, op, ban, kill @a
   │  4. Validates operator authority: GamerBhoid == authorizedOperatorPlayer
   │  5. Status: ALLOWED
   ▼
[CommandDispatcher.kt]
   │  1. Quotes command using JSONObject.quote()
   │  2. Evaluates script in WebView: types into #console-input & triggers Enter
   │  3. Verifies input field clearance after 80ms
   │  4. Broadcasts formatted chat: "say [AI] Giving you 5 diamonds..." (<140 char chunks)
   ▼
[Minecraft Server Console Executes Commands]
   │  - Gives 5 diamonds to GamerBhoid
   │  - Displays chat response to all players in-game
```

### Console Hook & DOM Mutation Engine

The bridge monitors the server console using an injected JavaScript observer ([console_hook.js](file:///run/media/bhoid/StorageVault/WEbs/Aternos%20Bridge/app/src/main/assets/console_hook.js)). When the console output container updates, the hook parses log lines matching the standard Minecraft server pattern:

```regex
\[\d{2}:\d{2}:\d{2}\]\s+\[.*?/INFO\]:\s+<([^>]+)>\s+(.*)
```

The parsed player and message are transmitted directly to native Kotlin code via `@JavascriptInterface` on `AternosNativeBridge`.

### Anti-Bot & Turnstile Hardening

To prevent Aternos and Cloudflare anti-bot systems from halting execution:
- **No Fetch / XHR Monkey Patching:** Requests are never intercepted via custom JavaScript proxies, preventing CSRF token desynchronization.
- **Native TLS/JA3 Fingerprinting:** The Android WebView natively handles challenge requests without proxying through Java-level HTTP sockets.
- **Request Header Sanitization:** Calls `WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())` to eliminate the `X-Requested-With: com.aternos.bridge` header.
- **User-Agent Spoofing:** Configures a modern desktop Chrome Linux user agent string to match desktop web behavior.
- **Multi-Window OAuth:** Implements `WebChromeClient.onCreateWindow` to capture and redirect Google OAuth popups back into the primary authentication WebView.

### Multi-Provider AI Abstraction

The `AiProvider` interface standardizes communication across diverse AI backends:

```kotlin
interface AiProvider {
    suspend fun generateResponse(
        prompt: String,
        player: String,
        conversationHistory: List<AiHistoryEntry>,
        config: AppConfig
    ): AgentResult
}
```

Implementations include:
- `GoogleGenAiProvider`: Uses the official Google Generative AI SDK with Gemini function declarations for native tool execution.
- `UniversalOpenAiProvider`: Uses OkHttp to communicate with OpenAI-compatible chat completion endpoints, supporting JSON schema function calling for tool execution.
- `AnthropicProvider`: Formats tool definitions conforming to Anthropic Claude 3.5 Sonnet / Haiku tool-use schemas.

### Curated Minecraft Wiki Engine

The [MinecraftWikiEngine.kt](file:///run/media/bhoid/StorageVault/WEbs/Aternos%20Bridge/app/src/main/java/com/aternos/bridge/wiki/MinecraftWikiEngine.kt) provides real-time game intelligence to the AI model so it never hallucinates recipes, command syntax, or item identifiers:
- **In-Memory Knowledge Base:** Detailed entries covering modern features (Trial Chambers, Ominous Vaults, Wind Charges, Bogged, Breeze, Mace, Crafter, Pale Garden, Creaking, Resin, and 2026 Game Drops including Sulfur Caves and Sulfur Cubes).
- **Fast Semantic Search:** Keyword-weighted matching returns the most relevant entry in under 5 milliseconds.
- **Live MediaWiki Queries:** Automatically queries the live Minecraft Wiki REST API (`minecraft.wiki/api.php`) for specialized or unindexed topics.

---

## Security & Guardrail Engine

Because AI outputs can be unpredictable or susceptible to prompt injection from players in Minecraft chat, the bridge enforces an unbypassable, deterministic security barrier before any command touches the Aternos console.

### Command Normalization & Tree Inspection

1. **Normalization:** The command is stripped of leading slashes (`///weather clear` -> `weather clear`), trimmed, lowercased, and collapsed of redundant whitespace.
2. **Composite / Chaining Detection:** Semicolons (`;`), logical operators (`&&`, `||`, `|`), newlines (`\n`, `\r`), backticks (`` ` ``), and dollar expansions (`$`) are rejected immediately.
3. **Recursive Unwrapping:** Commands starting with `/execute` are parsed recursively:
   ```
   /execute at GamerBhoid run op HackerSteve
   Unwrapped Subcommand: "op HackerSteve" -> BLOCKED (Root command 'op' is forbidden)
   ```

### Blocked Commands & Exploit Prevention

The following command categories are hard-blocked and can never be executed under any circumstance:

| Category | Blocked Commands |
| :--- | :--- |
| **Server Lifecycle** | `stop`, `restart`, `reload`, `save-off`, `save-all` |
| **Privilege Escalation** | `op`, `deop`, `whitelist` |
| **Player Discipline** | `ban`, `ban-ip`, `pardon`, `pardon-ip`, `kick` |
| **Mass Destruction** | `kill @a`, `kill @e` |
| **Diagnostic & Profiling** | `debug`, `jfr`, `perf`, `publish`, `datapack disable` |

### Authorized Operator Access Model

The `enforceOperatorOnlyCommands` configuration setting ensures that only the player matching `authorizedOperatorPlayer` (configured in settings) can trigger state-changing commands (`give`, `tp`, `gamemode`, `summon`, `weather`, `time`). Commands triggered by unauthorized players are rejected with an audit log:

```
[BLOCKED] Command execution rejected: Player 'Steve' is not authorized operator 'GamerBhoid'.
```

Non-operator players can still use the bridge for questions, recipes, lore, and game guidance.

### Token Bucket Rate Limiting

The [RateLimiter.kt](file:///run/media/bhoid/StorageVault/WEbs/Aternos%20Bridge/app/src/main/java/com/aternos/bridge/security/RateLimiter.kt) enforces per-player rate limits using a token bucket algorithm to prevent chat spam from exhausting AI provider rate limits or credits.

### Encrypted Hardware Keystore Storage

The [EncryptedConfigRepository.kt](file:///run/media/bhoid/StorageVault/WEbs/Aternos%20Bridge/app/src/main/java/com/aternos/bridge/data/EncryptedConfigRepository.kt) utilizes AndroidX Security Crypto:
- Keys are generated in the hardware-backed Android Keystore (`MasterKeys.AES256_GCM_SPEC`).
- Values in `EncryptedSharedPreferences` are encrypted at rest using AES-256 GCM for values and AES-256 SIV for keys.
- Cleartext API keys are never stored on disk or committed to version control.

---

## In-Game Chat Usage Guide

### Assistant Prompts & Commands

Players communicate with the bridge directly in Minecraft chat using the configured trigger prefix (defaults to `@server` or `!ai`):

```
# Question / Wiki Query (Available to all players)
<Steve> @server How do I craft a Crafter?
[AI]: A Crafter is crafted with 5 Iron Ingots, 1 Crafting Table, 2 Redstone Dust, and 1 Dropper!

# Operator Command (Restricted to Authorized Operator)
<GamerBhoid> @server set the time to morning and give me a mace
[AI]: Setting time to day and giving GamerBhoid 1 mace.
[Server]: Set the time to 1000
[Server]: Gave 1 [Mace] to GamerBhoid

# Unauthorized Attempt (Blocked)
<Griefer99> @server /op Griefer99
[AI]: Security Alert: Execution of 'op' is blocked by server guardrails.
```

### Supported Command Reference

Safe commands permitted by the allowlist:

| Command | Usage Example |
| :--- | :--- |
| `time` | `time set day`, `time set night` |
| `weather` | `weather clear`, `weather thunder` |
| `give` | `give GamerBhoid diamond 64` |
| `tp` / `teleport` | `tp GamerBhoid 100 64 -200` |
| `gamemode` | `gamemode survival GamerBhoid` |
| `summon` | `summon allay ~ ~ ~` |
| `effect` | `effect give GamerBhoid night_vision 60 1` |
| `say` | `say [AI] Warning: Server restart in 10 minutes` |
| `difficulty` | `difficulty hard` |
| `locate` | `locate structure trial_chambers` |

---

## Configuration Reference

The following settings are configurable via the in-app **Setup Screen** and persisted in encrypted storage:

| Setting Key | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `provider` | String | `google` | Active AI provider ID (`google`, `openai`, `anthropic`, `groq`, etc.) |
| `apiKey` | String | `""` | Provider API authentication token (Encrypted at rest) |
| `modelName` | String | `gemini-2.0-flash` | Selected model identifier |
| `customBaseUrl` | String | `""` | Custom API endpoint for OpenAI-compatible providers or local Ollama |
| `triggerPrefix` | String | `@server` | Chat trigger keyword detected in console logs |
| `authorizedOperatorPlayer`| String | `GamerBhoid` | Minecraft username authorized to execute server commands |
| `enforceOperatorOnlyCommands` | Boolean | `true` | Restrict command execution exclusively to authorized operator |
| `rateLimitSeconds` | Int | `10` | Minimum cooldown between prompts per individual player |
| `maxOutboundPromptLength` | Int | `250` | Maximum character length of player prompt passed to AI |
| `chatBroadcastFormat` | String | `say [AI] {message}` | Template for public chat broadcasts (`say` or `tell {player}`) |
| `serverStatusGateEnabled` | Boolean | `true` | Prevent AI processing when server is offline or loading |
| `keepAliveIntervalSeconds`| Int | `45` | Interval for background WebView focus keep-alive pings |

---

## Available Gradle Tasks

| Command | Description |
| :--- | :--- |
| `./gradlew assembleDebug` | Compiles and packages the debug APK (`app-debug.apk`) |
| `./gradlew assembleRelease` | Compiles and packages the unsigned/signed release APK |
| `./gradlew bundleRelease` | Generates the release Android App Bundle (`.aab`) for Google Play |
| `./gradlew test` | Executes all local JVM unit tests across all build variants |
| `./gradlew testDebugUnitTest` | Runs unit tests specifically on debug classes |
| `./gradlew lint` | Runs Android Lint static analysis to find potential bugs and code issues |
| `./gradlew installDebug` | Builds and installs the debug APK directly to a connected ADB device |
| `./gradlew clean` | Deletes the `build/` directories and clears compile caches |

---

## Testing & Quality Assurance

### Executing Unit Tests

Run the test suite using Gradle:

```bash
./gradlew test
```

Test reports are generated in HTML format at:
`app/build/reports/tests/testDebugUnitTest/index.html`

### Test Suite Breakdown

1. **`SecurityGuardrailFilterTest`:**
   - Validates allowlisted commands with diverse prefix styles (`/time`, `///weather`).
   - Verifies blocking of forbidden roots (`stop`, `op`, `ban`, `kick`, `kill @a`).
   - Verifies recursive unwrapping of composite `/execute` commands.
   - Tests injection character rejections (`;`, `&&`, `|`, `\n`).
   - Enforces operator-only permission checks.
2. **`CommandSanitizerTest`:**
   - Verifies message chunking into &lt;140 character segments without breaking words.
   - Tests legacy `/w @a` replacement with `say`.
   - Validates player placeholder substitution (`{player}`).
3. **`RateLimiterTest`:**
   - Tests token bucket depletion and recovery over time.
   - Verifies per-player isolation (Player A being throttled does not block Player B).
4. **`MinecraftWikiEngineTest`:**
   - Verifies semantic search relevance and knowledge base query responses for 1.20, 1.21, and 2026 Game Drops.

### Adding New Tests

Unit tests are located in `app/src/test/java/com/aternos/bridge/`. When adding new command rules, create a test case in `SecurityGuardrailFilterTest`:

```kotlin
@Test
fun testNewSecurityRule() {
    val result = SecurityGuardrailFilter.validate(
        rawCommand = "give @p emerald 10",
        player = "GamerBhoid",
        authorizedOperator = "GamerBhoid",
        enforceOperatorOnly = true
    )
    assertTrue(result is SecurityValidationResult.Allowed)
}
```

---

## Deployment & Release Packaging

### Keystore Generation

To build a signed release APK or Android App Bundle (AAB), generate a production release keystore:

```bash
keytool -genkey -v -keystore release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias aternos-bridge-key
```

### Release Build Creation

Set environment variables or configure `signingConfigs` in `app/build.gradle.kts`:

```bash
# Build production AAB for Google Play
./gradlew bundleRelease

# Or assemble standalone release APK
./gradlew assembleRelease
```

Output artifacts will be generated at:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### Google Play Store Policy Caveats

> [!WARNING]
> - **Foreground Service (`FOREGROUND_SERVICE_DATA_SYNC`):** Google Play policies strictly audit apps using continuous background foreground services. The app uses `dataSync` to maintain an active log stream between the Aternos console WebSocket and the AI engine while players are in full-screen Minecraft. When submitting to Google Play, provide a video demonstration illustrating why the background service is essential to the gameplay loop.
> - **Battery Optimization:** For uninterrupted background operation, exclude the app from Android Battery Optimization (Settings > Apps > Aternos AI Bridge > Battery > Unrestricted).

---

## Troubleshooting & Diagnostics

### 1. Stuck on Cloudflare Turnstile Challenge
- **Symptom:** Handshake screen shows a Cloudflare challenge spinner or verification checkbox that fails to progress.
- **Cause:** Cloudflare detected automated headers or TLS fingerprint desynchronization.
- **Resolution:** The app now preserves native Chromium TLS sockets and suppresses the `X-Requested-With` header. If a manual checkbox appears, tap the checkbox directly on the Handshake screen. The app will trigger a haptic pulse once verification succeeds.

### 2. Google OAuth Popup Not Loading
- **Symptom:** Tapping "Sign in with Google" does nothing.
- **Resolution:** Popups are now intercepted via `WebChromeClient.onCreateWindow` and routed directly into the canonical session WebView. Ensure your device has an up-to-date Google Play Services / Chrome WebView installed.

### 3. AI Types in App but Players Cannot See in Game
- **Symptom:** Logs appear in the app's Console Log view, but nothing shows up in Minecraft.
- **Cause:** Using `/w @a` is rejected by Minecraft server consoles (*"Only one player can be specified for /w"*).
- **Resolution:** Verify your **Chat Broadcast Format** in Settings is set to `say [AI] {message}`. The bridge automatically translates legacy `/w @a` commands to `say`.

### 4. Background Service Dies When Screen Is Locked
- **Symptom:** Console monitoring stops after the phone screen turns off for several minutes.
- **Resolution:** Grant the application **Unrestricted Battery Usage** in Android system settings and ensure the persistent notification remains active. The app automatically maintains a bounded partial WakeLock renewed every 10 minutes.

---

## Disclaimer & Terms of Service

This application is an independent third-party tool that automates interaction with the Aternos web console via DOM scripting. It is **not** affiliated with, endorsed by, or sponsored by **Aternos GmbH** or **Mojang Synergies AB / Microsoft Corporation**. 

Aternos does not provide an official public API; this software interacts with the standard web interface. Users are responsible for ensuring that their automated use complies with Aternos Terms of Service. Use responsibly and avoid setting excessively low rate limits.

---

## License

```
Copyright 2026 CoderBhoid

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
