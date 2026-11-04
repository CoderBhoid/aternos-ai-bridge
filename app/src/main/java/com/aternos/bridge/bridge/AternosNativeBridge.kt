package com.aternos.bridge.bridge

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import com.aternos.bridge.ai.AgentResult
import com.aternos.bridge.ai.AiOrchestrator
import com.aternos.bridge.data.AiExecutionStatus
import com.aternos.bridge.data.AiHistoryEntry
import com.aternos.bridge.data.AternosServer
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.data.ServerStatus
import com.aternos.bridge.security.CommandSanitizer
import com.aternos.bridge.security.SecurityGuardrailFilter
import com.aternos.bridge.security.SecurityValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * JavaScript interface exposed to the embedded Aternos WebView as `window.AndroidBridge`.
 */
class AternosNativeBridge(
    private val context: Context,
    private val commandDispatcher: CommandDispatcher,
    private val aiOrchestrator: AiOrchestrator,
    private val configRepository: EncryptedConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val tag = "AternosNativeBridge"
    private val recentPromptsCache = ConcurrentHashMap<String, Long>()

    /**
     * Called by injected JavaScript when a player types `!ai <prompt>` in Minecraft chat.
     */
    @JavascriptInterface
    fun onPlayerPrompt(sender: String, prompt: String) {
        val cleanPlayer = CommandSanitizer.sanitizePlayerName(sender)
        val cleanPrompt = CommandSanitizer.sanitizeInboundPrompt(prompt)

        Log.i(tag, "Received onPlayerPrompt: player='$cleanPlayer', prompt='$cleanPrompt'")

        // 1. Debounce duplicate DOM events (Aternos may re-render log rows)
        val dedupeKey = "$cleanPlayer:$cleanPrompt"
        val now = System.currentTimeMillis()
        val lastSeen = recentPromptsCache[dedupeKey] ?: 0L
        if (now - lastSeen < 2500L) {
            Log.d(tag, "Debounced duplicate prompt: '$dedupeKey'")
            return
        }
        recentPromptsCache[dedupeKey] = now

        // 2. Check if bridge is paused
        if (BridgeEventManager.isBridgePaused.value) {
            Log.w(tag, "Bridge is currently paused by user; skipping prompt.")
            BridgeEventManager.addConsoleLog("[Bridge Paused] Ignored trigger from $cleanPlayer", level = "WARN", isSystemNotice = true)
            return
        }

        val config = configRepository.getConfig()

        // 3. Server status check
        if (config.serverStatusGateEnabled && BridgeEventManager.serverStatus.value != ServerStatus.ONLINE) {
            Log.w(tag, "Server is not online (${BridgeEventManager.serverStatus.value}); skipping prompt.")
            return
        }

        val historyId = java.util.UUID.randomUUID().toString()
        val historyEntry = AiHistoryEntry(
            id = historyId,
            player = cleanPlayer,
            prompt = cleanPrompt,
            providerUsed = config.providerType.displayName,
            status = AiExecutionStatus.PENDING
        )
        BridgeEventManager.addAiHistory(historyEntry)

        // 4. Launch async coroutine to process with AI backend
        scope.launch {
            try {
                val agentResult = aiOrchestrator.processPrompt(cleanPlayer, cleanPrompt, config)
                handleAgentResult(historyId, cleanPlayer, cleanPrompt, agentResult)
            } catch (e: Exception) {
                Log.e(tag, "Error processing prompt for $cleanPlayer", e)
                BridgeEventManager.updateAiHistory(
                    id = historyId,
                    status = AiExecutionStatus.ERROR,
                    responseText = "Error: ${e.message}"
                )
            }
        }
    }

    private fun handleAgentResult(
        historyId: String,
        player: String,
        prompt: String,
        result: AgentResult
    ) {
        val config = configRepository.getConfig()

        when (result) {
            is AgentResult.ExecuteCommand -> {
                val proposedCmd = result.command.trim()
                Log.i(tag, "AI proposed command: '$proposedCmd' with explanation: '${result.explanation}' for player '$player'")

                // Pass through high-security guardrail & operator permission filter!
                val validation = SecurityGuardrailFilter.validate(
                    rawCommand = proposedCmd,
                    player = player,
                    authorizedOperator = config.authorizedOperatorPlayer,
                    enforceOperatorOnly = config.enforceOperatorOnlyCommands
                )

                when (validation) {
                    is SecurityValidationResult.Allowed -> {
                        val safeCmd = validation.sanitizedCommand
                        BridgeEventManager.updateAiHistory(
                            id = historyId,
                            status = AiExecutionStatus.COMMAND_SUCCESS,
                            commandDispatched = safeCmd,
                            explanation = result.explanation
                        )

                        // 1. Dispatch safe command to console
                        commandDispatcher.dispatchCommand(safeCmd) { dispatchResult ->
                            if (dispatchResult.startsWith("DISPATCH_OK")) {
                                BridgeEventManager.addConsoleLog("> $safeCmd", level = "CMD", isAiAction = true)
                            } else {
                                BridgeEventManager.addConsoleLog("[FAILED TO TYPE '$safeCmd'] $dispatchResult", level = "ERROR", isSystemNotice = true)
                            }
                        }

                        // 2. Broadcast explanation in-game if provided
                        if (!result.explanation.isNullOrBlank()) {
                            commandDispatcher.dispatchPacedBroadcast(
                                fullText = result.explanation,
                                targetPlayer = player,
                                broadcastFormat = config.chatBroadcastFormat
                            )
                        }
                    }

                    is SecurityValidationResult.Blocked -> {
                        val reason = validation.reason
                        Log.w(tag, "GUARDRAIL BLOCKED: '$proposedCmd' - $reason")
                        BridgeEventManager.updateAiHistory(
                            id = historyId,
                            status = AiExecutionStatus.COMMAND_BLOCKED,
                            commandDispatched = proposedCmd,
                            blockedReason = reason
                        )
                        BridgeEventManager.addConsoleLog(
                            "[SECURITY GUARDRAIL] Blocked '$proposedCmd': $reason",
                            level = "ERROR",
                            isAiAction = true
                        )

                        // Notify in-game that command was rejected for security
                        val rejectionNotice = "Command '$proposedCmd' was blocked by security guardrails: $reason"
                        commandDispatcher.dispatchPacedBroadcast(
                            fullText = rejectionNotice,
                            targetPlayer = player,
                            broadcastFormat = config.chatBroadcastFormat
                        )
                    }
                }
            }

            is AgentResult.ChatMessage -> {
                val reply = result.replyText
                Log.i(tag, "AI replied with chat: '$reply'")
                BridgeEventManager.updateAiHistory(
                    id = historyId,
                    status = AiExecutionStatus.CHAT_REPLIED,
                    responseText = reply
                )
                commandDispatcher.dispatchPacedBroadcast(
                    fullText = reply,
                    targetPlayer = player,
                    broadcastFormat = config.chatBroadcastFormat
                )
            }

            is AgentResult.Error -> {
                Log.e(tag, "AI Provider returned error: ${result.errorMessage}")
                BridgeEventManager.updateAiHistory(
                    id = historyId,
                    status = AiExecutionStatus.ERROR,
                    responseText = result.errorMessage
                )
                BridgeEventManager.addConsoleLog("[AI Error] ${result.errorMessage}", level = "ERROR", isSystemNotice = true)
            }
        }
    }

    /**
     * Ingests live console log lines streamed from the web console.
     */
    @JavascriptInterface
    fun onConsoleLog(text: String, level: String) {
        BridgeEventManager.addConsoleLog(text, level = level)
    }

    /**
     * Ingests updated server status (Online / Offline / Queue).
     */
    @JavascriptInterface
    fun onServerStatusChange(statusText: String) {
        val status = ServerStatus.fromText(statusText)
        Log.i(tag, "Server status changed: $statusText -> $status")
        BridgeEventManager.setServerStatus(status)
    }

    /**
     * Called when the JS DOM hook successfully attaches to the log container.
     */
    @JavascriptInterface
    fun onBridgeReady() {
        Log.i(tag, "DOM hook is ready and attached.")
        BridgeEventManager.setHookAttached(true)
    }

    /**
     * Called when Cloudflare challenge is detected on the page.
     */
    @JavascriptInterface
    fun onCloudflareDetected() {
        Log.w(tag, "Cloudflare Turnstile challenge detected!")
        BridgeEventManager.setCloudflareDetected(true)
        triggerHapticAlert()
    }

    /**
     * Diagnostic message from the DOM hook script.
     */
    @JavascriptInterface
    fun onDiagnostic(message: String) {
        Log.w(tag, "Diagnostic from hook: $message")
        BridgeEventManager.postDiagnostic(message)
    }

    /**
     * Called on the server selection page when servers list is parsed.
     */
    @JavascriptInterface
    fun onServersDiscovered(serversJson: String) {
        try {
            val jsonArray = JSONArray(serversJson)
            val list = mutableListOf<AternosServer>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AternosServer(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        address = obj.optString("address"),
                        software = obj.optString("software"),
                        status = obj.optString("status")
                    )
                )
            }
            BridgeEventManager.setDiscoveredServers(list)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing discovered servers", e)
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Proxies HTTP fetch requests (e.g. login POST / AJAX calls) through native OkHttpClient
     * to completely strip X-Requested-With and bypass Cloudflare/Aternos app detection.
     */
    @JavascriptInterface
    fun proxyHttpRequest(url: String, method: String, contentType: String, bodyData: String, callbackId: String) {
        scope.launch {
            try {
                Log.i(tag, "Proxying HTTP request: $method $url")
                val activeUrl = BridgeEventManager.currentUrl.value
                val dynamicReferer = when {
                    activeUrl.isNotBlank() && activeUrl.contains("aternos.org") -> activeUrl
                    url.contains("login") || url.contains("lost") || url.contains("signup") -> "https://aternos.org/go/"
                    url.contains("access") || url.contains("servers") -> "https://aternos.org/servers/"
                    else -> "https://aternos.org/server/"
                }

                val okReqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", AternosConsoleClient.CHROME_MOBILE_USER_AGENT)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Origin", "https://aternos.org")
                    .header("Referer", dynamicReferer)
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")

                if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) {
                    val mediaType = (if (contentType.isNotBlank()) contentType else "application/x-www-form-urlencoded; charset=UTF-8").toMediaType()
                    okReqBuilder.post(bodyData.toRequestBody(mediaType))
                } else {
                    okReqBuilder.get()
                }

                // Sync cookies from CookieManager
                val cookie = CookieManager.getInstance().getCookie(url) ?: CookieManager.getInstance().getCookie("https://aternos.org")
                if (!cookie.isNullOrBlank()) {
                    okReqBuilder.header("Cookie", cookie)
                }

                val response = httpClient.newCall(okReqBuilder.build()).execute()

                // Sync Set-Cookie back to CookieManager
                val responseHeaders = response.headers
                for (name in responseHeaders.names()) {
                    if (name.equals("Set-Cookie", ignoreCase = true)) {
                        for (value in responseHeaders.values(name)) {
                            CookieManager.getInstance().setCookie(url, value)
                            CookieManager.getInstance().setCookie("https://aternos.org", value)
                        }
                    }
                }
                CookieManager.getInstance().flush()

                val respBody = response.body?.string() ?: ""
                val headersJson = JSONObject()
                for (i in 0 until responseHeaders.size) {
                    headersJson.put(responseHeaders.name(i), responseHeaders.value(i))
                }

                val resultJson = JSONObject().apply {
                    put("status", response.code)
                    put("statusText", response.message)
                    put("headers", headersJson)
                    put("body", respBody)
                }

                val resultQuoted = JSONObject.quote(resultJson.toString())
                val callbackScript = "(function() { if (window['$callbackId']) { window['$callbackId']($resultQuoted); } })();"

                withContext(Dispatchers.Main) {
                    commandDispatcher.evaluateJavascript(callbackScript)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error proxying HTTP request to $url", e)
                val errJson = JSONObject().apply {
                    put("status", 500)
                    put("statusText", e.message ?: "Network Error")
                    put("headers", JSONObject())
                    put("body", JSONObject().put("success", false).put("error", e.message).toString())
                }
                val errQuoted = JSONObject.quote(errJson.toString())
                val callbackScript = "(function() { if (window['$callbackId']) { window['$callbackId']($errQuoted); } })();"
                withContext(Dispatchers.Main) {
                    commandDispatcher.evaluateJavascript(callbackScript)
                }
            }
        }
    }

    private fun triggerHapticAlert() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
                val effect = VibrationEffect.createWaveform(pattern, -1)
                it.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration failed: ${e.message}")
        }
    }
}
