# Build Prompt: Aternos AI Console Bridge (Android App)

> Copy everything below into your AI coding agent (Claude Code, Cursor, etc.) as the initial instruction. It is written so the agent can work phase-by-phase and self-check against the acceptance criteria at the end of each phase.

---

## 0. Role & Objective

You are a senior Android engineer. Build a **production-ready native Android application** called **Aternos AI Console Bridge**. It embeds an Android `WebView` pointed at a user's Aternos Minecraft server console, injects JavaScript to watch the live console log for a chat trigger (`!ai <prompt>`), sends the captured prompt to an AI model (Gemma 2 / Gemini via the Google GenAI SDK), and — if the model decides a server command is warranted — writes that command back into the console input box and submits it, subject to a hard-coded safety filter.

Work in the four phases defined in Section 7. After each phase, stop and report what was built, what was tested, and any deviations, before continuing to the next phase.

---

## 1. Target Environment

- **Platform:** Native Android, Kotlin, min SDK 31 (Android 12), target latest stable SDK.
- **UI toolkit:** Jetpack Compose, Material 3, dark "slate" theme as the only theme (no light mode required).
- **AI backend:** Google GenAI SDK, model `gemma-2-27b-it` (configurable to a 9B variant), with a fallback text-only path if function calling is unavailable.
- **Build system:** Gradle (Kotlin DSL), single-module app unless a feature genuinely warrants separation (e.g. `:core-bridge`, `:app`).
- **No backend server** — this is a fully on-device app; Aternos is accessed only through the embedded WebView, and the AI model is called directly from the device.

---

## 2. High-Level Pipeline

**Game → Native:**
1. A player types `!ai <prompt>` in Minecraft chat.
2. Aternos streams that line into its web console via WebSocket.
3. A `MutationObserver` injected into the console DOM detects the new log line, regex-matches the trigger, and calls `window.AndroidBridge.onPlayerPrompt(sender, prompt)`.
4. The Kotlin `@JavascriptInterface` layer receives this call and launches an async coroutine.

**Native → Game:**
5. The prompt is sent to the GenAI SDK with a function-calling tool schema (`execute_minecraft_command`).
6. The model returns either a plain chat reply or a structured command call.
7. Any proposed command is checked against a hard-coded blocklist (Section 8).
8. If approved, the app injects the command into the console's input field via `evaluateJavascript` and fires a synthetic `Enter` keystroke.

---

## 3. Screens & User Flow (Jetpack Compose)

Build exactly three top-level states, navigated automatically where noted:

| # | Screen | Key components | Behavior |
|---|--------|-----------------|----------|
| 1 | **Setup & Auth Config** | API key field, model selector (Gemma 2 27B / 9B), trigger-prefix field (default `!ai`), short Cloudflare guidance text | Validates the Google AI Studio key with a lightweight test call. Persists the key using `EncryptedSharedPreferences` (AndroidX Security Crypto) — never plain `SharedPreferences`. |
| 2 | **Aternos Handshake** | Embedded `WebView`, progress indicator, automatic post-login navigation | Loads `https://aternos.org/go/`. Supports normal login and Google OAuth inside the WebView. On reaching `https://aternos.org/servers/`, the `WebViewClient` intercepts navigation: if exactly one server is registered, auto-select it and navigate to `https://aternos.org/server/<server-id>/console`; if multiple servers exist, show a simple picker instead of guessing. |
| 3 | **Active HUD & Monitor** | Live console stream (parsed, scrollable), AI request history list, server status pill, manual command input, pause/resume toggle | Minimizes the WebView into the background `ForegroundService`; renders a parsed, human-readable feed of log lines and AI actions in Compose; supports a Picture-in-Picture style floating overlay mode. |

---

## 4. Core Components to Implement

### 4.1 `AternosBridgeService` — Foreground Service

- Type: `android:foregroundServiceType="dataSync"` (Android 14+ requirement) — declare in the manifest and start with the matching service type constant.
- Acquires a `PowerManager.PARTIAL_WAKE_LOCK` (tag `AternosAI::ServiceWakeLock`) with a bounded timeout (do **not** hold it indefinitely — renew it periodically instead of a single 24h acquire, to avoid battery-abuse flags and OS kills).
- Hosts a background `WebView` with:
  - `javaScriptEnabled = true`
  - `domStorageEnabled = true`
  - `databaseEnabled = true`
  - `mediaPlaybackRequiresUserGesture = false`
  - `setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, waivedWhenNotVisible = false)`
  - `addJavascriptInterface(AternosNativeBridge(this), "AndroidBridge")`
  - A custom `WebViewClient` (`AternosConsoleClient`) that performs the post-login routing from Section 3 and re-injects the console hook script (Section 4.2) after every page load at the console URL.
- Shows a persistent, low-priority foreground notification ("Aternos AI Bridge active — watching console") with a stop action.

Starter skeleton (extend, don't leave as-is):

```kotlin
class AternosBridgeService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        acquireWakeLock()
        initBackgroundWebView()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "AternosAI::ServiceWakeLock"
        ).apply { acquire(10 * 60 * 1000L) } // renew on a timer, do not hold indefinitely
    }

    private fun initBackgroundWebView() {
        webView = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
            addJavascriptInterface(AternosNativeBridge(this@AternosBridgeService), "AndroidBridge")
            webViewClient = AternosConsoleClient()
        }
    }
}
```

### 4.2 `console_hook.js` — DOM MutationObserver

Inject this after page load at the console URL. Selector list and regex should be **configurable constants**, not hard-coded, since Aternos may change its DOM:

```javascript
(function() {
  const LOG_CONTAINER_SELECTOR = '#terminal, .console, .terminal-container, .log-entries';
  let targetNode = document.querySelector(LOG_CONTAINER_SELECTOR);

  function attachObserver(node) {
    const observer = new MutationObserver((mutations) => {
      for (let mutation of mutations) {
        mutation.addedNodes.forEach(addedNode => {
          const rawText = addedNode.textContent || "";
          if (!rawText) return;
          // Intercept: [Server thread/INFO]: <PlayerName> !ai give me 5 diamonds
          const chatMatch = rawText.match(/<([a-zA-Z0-9_]+)>\s*!ai\s+(.+)/);
          if (chatMatch && window.AndroidBridge) {
            const sender = chatMatch[1];
            const prompt = chatMatch[2].trim();
            window.AndroidBridge.onPlayerPrompt(sender, prompt);
          }
        });
      }
    });
    observer.observe(node, { childList: true, subtree: true });
    if (window.AndroidBridge) window.AndroidBridge.onBridgeReady();
  }

  if (targetNode) {
    attachObserver(targetNode);
  } else {
    const docObserver = new MutationObserver(() => {
      targetNode = document.querySelector(LOG_CONTAINER_SELECTOR);
      if (targetNode) {
        docObserver.disconnect();
        attachObserver(targetNode);
      }
    });
    docObserver.observe(document.body, { childList: true, subtree: true });
  }
})();
```

The `AternosNativeBridge` Kotlin class exposes `onPlayerPrompt(sender: String, prompt: String)` and `onBridgeReady()` via `@JavascriptInterface`, and must:
- Sanitize/length-limit `sender` and `prompt` before use.
- Debounce duplicate triggers (Aternos may re-render log lines).
- Post results back to the HUD via a `SharedFlow`/`LiveData` the Compose UI collects.

### 4.3 `GemmaOrchestrator` — Model Call & Function Calling

```kotlin
class GemmaOrchestrator(private val apiKey: String) {
    private val client = GoogleGenAI(apiKey = apiKey)

    private val mcCommandTool = FunctionDeclaration(
        name = "execute_minecraft_command",
        description = "Runs an OP command on the Minecraft server. Do NOT include leading slashes.",
        parameters = Schema.objectSchema(
            properties = mapOf(
                "command" to Schema.string("Exact MC command (e.g. time set day, weather clear, give @p diamond 3)"),
                "explanation" to Schema.string("Short in-game broadcast message explaining action")
            ),
            required = listOf("command")
        )
    )

    suspend fun processPrompt(player: String, prompt: String): AgentResult = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a Minecraft assistant for player: $player. " +
            "If needed, invoke execute_minecraft_command. Limit replies to 120 chars."
        val response = client.models.generateContent(
            model = "gemma-2-27b-it",
            contents = "Player $player: $prompt",
            config = GenerateContentConfig(
                systemInstruction = Content.system(systemPrompt),
                tools = listOf(Tool(functionDeclarations = listOf(mcCommandTool)))
            )
        )
        val call = response.functionCalls?.firstOrNull()
        if (call != null && call.name == "execute_minecraft_command") {
            val command = call.args["command"].toString()
            val explanation = call.args["explanation"]?.toString()
            return@withContext AgentResult.ExecuteCommand(command, explanation)
        }
        AgentResult.ChatMessage(response.text ?: "No response generated.")
    }
}
```

Add:
- Timeout + retry-once on transient network failure.
- A per-player rate limit (e.g. 1 prompt per 10 seconds) to stop prompt-spam/cost blowouts.
- Truncation of the outbound `prompt` to a sane max length before it ever reaches the model.

### 4.4 Security Guardrail & Command Dispatcher

**Hard-block these command roots before any dispatch, no exceptions, no admin override toggle in v1:**

```
/stop, /restart, /reload
/op, /deop, /whitelist
/ban, /ban-ip, /kick, /pardon
/execute as ... run op
```

Implement the filter as an allowlist-leaning check, not just a blocklist string match — normalize the command (lowercase, strip leading slash, collapse whitespace) before comparing, and reject anything containing chained/composite execution (`;`, `&&`, embedded `/execute ... run <blocked>`) even if the outer command looks safe. Log every rejected command with the player and reason.

```kotlin
fun dispatchCommandToConsole(cmd: String) {
    val safeCmd = cmd.replace("'", "\\'")
    val script = """
        (function() {
          const input = document.querySelector('input.console-input') ||
                        document.querySelector('.console-input-wrapper input');
          if (input) {
            input.value = '$safeCmd';
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, which: 13, bubbles: true }));
          }
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}
```

Note: string-substituting `safeCmd` directly into a JS literal is fragile/injectable — prefer passing the value via `evaluateJavascript` with proper JSON-encoding (`JSONObject.quote(cmd)`) rather than manual replace, to close the escaping gap.

---

## 5. Manifest & Permissions

| Declaration | Purpose |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Required for the persistent background service on Android 14+. |
| `WAKE_LOCK` | For the bounded partial wake lock. |
| `INTERNET` | WebView + GenAI SDK network access. |
| `POST_NOTIFICATIONS` | Foreground service notification on Android 13+. |

State plainly in the generated `README.md` that `FOREGROUND_SERVICE_DATA_SYNC` and continuous background WebView use are the kind of behavior Play Store review scrutinizes closely — if Play Store distribution is a goal, flag this as a review-risk item rather than silently shipping it.

---

## 6. Edge Cases to Handle Explicitly

| Challenge | Failure mode | Required handling |
|---|---|---|
| Cloudflare Turnstile | Verification screen appears mid-auth | Detect a `challenges.cloudflare.com` iframe; vibrate + surface a high-priority notification/overlay so the user can solve it manually. Never attempt to auto-solve it. |
| WebSocket inactivity drop | Aternos pauses the log stream when the tab reports no focus | Dispatch a synthetic `focus` event (`window.dispatchEvent(new Event('focus'))`) on a ~45s timer from the Kotlin side. |
| Server offline / queued | Observer fires on queue messages or nothing at all | Poll the `.server-status` DOM node; only parse chat triggers when status text equals "Online". |
| Chat length limit | AI reply exceeds Minecraft's ~256-char display limit | Truncate to ~140 chars, or split into sequential `say` commands with a ~400ms delay between each. |
| Aternos DOM changes | Selectors silently stop matching | Log a "hook not attached" diagnostic to the HUD after a timeout with no observer attach, rather than failing silently. |

---

## 7. Phased Build Order (stop and report after each)

**Phase 1 — WebView Host & Compose UI**
Deliver: dark-theme Compose scaffold, Setup screen with encrypted key storage, Aternos login WebView container, basic navigation between the three screens.
Acceptance: app builds, launches to Setup, accepts and persists (encrypted) an API key, loads the Aternos login page in the WebView.

**Phase 2 — Foreground Service & DOM Hook**
Deliver: `AternosBridgeService` with WakeLock + renderer priority, JS injection of `console_hook.js`, `AternosNativeBridge` wired to a `SharedFlow` the HUD collects, post-login auto-routing logic.
Acceptance: with a real Aternos account, chatting `!ai hello` in-game produces a logged event in the app within a few seconds.

**Phase 3 — GenAI SDK & Tool Calling**
Deliver: `GemmaOrchestrator`, function-calling schema, guardrail filter, safe command dispatcher (JSON-encoded, not string-replace), rate limiting, per-player prompt history in the HUD.
Acceptance: a benign prompt ("what time is it in-game") produces a `time query` style command dispatched to console and reflected in the live log; a blocked-command prompt ("stop the server") is rejected with a logged reason and no dispatch occurs.

**Phase 4 — Edge Hardening & PiP HUD**
Deliver: Cloudflare challenge detection/overlay, focus-event keep-alive timer, server-status polling gate, chat-length truncation/splitting, Picture-in-Picture monitor mode.
Acceptance: simulate/test each edge case in Section 6 individually and confirm the documented handling triggers.

---

## 8. Deliverables Expected From the Agent

- Full Gradle project (Kotlin DSL), buildable from a clean checkout.
- Clear package structure separating: UI (`ui/`), service/bridge (`bridge/`), AI orchestration (`ai/`), security/guardrails (`security/`).
- A `README.md` covering setup, required API keys, the Play Store review caveat from Section 5, and known limitations (Aternos DOM fragility, no official API — this is screen/DOM automation of a third party's console and may be subject to their Terms of Service; the user is responsible for confirming their own use is compliant).
- Inline KDoc on the guardrail filter and the command dispatcher specifically, since those are the highest-risk code paths.
