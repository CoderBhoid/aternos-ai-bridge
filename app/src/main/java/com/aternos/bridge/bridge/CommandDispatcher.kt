package com.aternos.bridge.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.aternos.bridge.security.CommandSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * High-Integrity Console Command Dispatcher.
 */
class CommandDispatcher(
    private val webViewProvider: () -> WebView? = { WebViewHolder.getWebView() },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val tag = "CommandDispatcher"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Dispatches a single server command directly to the Aternos web console input.
     */
    fun dispatchCommand(command: String, onResult: ((String) -> Unit)? = null) {
        val cleanCmd = command.trim().removePrefix("/")
        if (cleanCmd.isBlank()) return

        val jsonQuotedCmd = JSONObject.quote(cleanCmd)

        val script = """
            (function() {
                try {
                    const inputSelectors = [
                        '#console-input',
                        'input#console-input',
                        'input.console-input',
                        '.console-input',
                        '#terminal-input',
                        'input.terminal-input',
                        'input[type="text"].terminal-input',
                        'input[type="text"].console-input',
                        '.console-input-wrapper input',
                        '.console-input-wrapper textarea',
                        '.console-body input',
                        '.console-body textarea',
                        '.console-content input',
                        '#terminal input',
                        '#terminal textarea',
                        '.terminal input',
                        '.terminal textarea',
                        'input[name="command"]',
                        'input[name="cmd"]',
                        'input[name="console-input"]',
                        'textarea.console-input',
                        'textarea#console-input',
                        'input[placeholder*="command" i]',
                        'input[placeholder*="type" i]',
                        'input[placeholder*="enter" i]',
                        'input[placeholder*="console" i]'
                    ];
                    
                    let input = null;
                    for (let sel of inputSelectors) {
                        const el = document.querySelector(sel);
                        if (el && (el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0 || el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
                            input = el;
                            break;
                        }
                    }

                    if (!input) {
                        const consoleContainers = document.querySelectorAll('#terminal, .console, .terminal-container, .server-body, .log-entries');
                        for (let container of consoleContainers) {
                            const found = container.querySelector('input, textarea');
                            if (found) {
                                input = found;
                                break;
                            }
                        }
                    }
                    
                    if (!input) {
                        console.warn("[AternosBridge] Console input element not found in DOM.");
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onDiagnostic("Console input field not found on current page. URL: " + window.location.href);
                        }
                        return "INPUT_NOT_FOUND: URL=" + window.location.pathname;
                    }

                    const cmdToType = $jsonQuotedCmd;
                    input.focus();

                    // 1. React / Vue / Angular native value setter bypass
                    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set ||
                                                   Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set;
                    if (nativeInputValueSetter) {
                        nativeInputValueSetter.call(input, cmdToType);
                    } else {
                        input.value = cmdToType;
                    }

                    // 2. Dispatch synthetic input and change events
                    input.dispatchEvent(new Event('input', { bubbles: true, cancelable: true, composed: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true, cancelable: true, composed: true }));

                    // 3. Trigger jQuery if present
                    try {
                        if (window.jQuery || window.$) {
                            const jq = window.jQuery || window.$;
                            jq(input).val(cmdToType).trigger('input').trigger('change');
                        }
                    } catch(jqErr) {}

                    // 4. Sequential keyboard events: keydown -> keypress -> keyup
                    const eventInit = {
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13,
                        charCode: 13,
                        bubbles: true,
                        cancelable: true,
                        composed: true
                    };
                    input.dispatchEvent(new KeyboardEvent('keydown', eventInit));
                    input.dispatchEvent(new KeyboardEvent('keypress', eventInit));
                    input.dispatchEvent(new KeyboardEvent('keyup', eventInit));

                    // 5. Look for and click Send/Submit button
                    const sendBtnSelectors = [
                        '#console-send',
                        '.console-send',
                        '.console-send-btn',
                        '.btn-send',
                        '.console-input-button',
                        '.console-input-btn',
                        '.console-input-wrapper button',
                        '.console-input-wrapper [role="button"]',
                        '.console-input-wrapper div[class*="button"]',
                        '.console-input-wrapper div[class*="send"]',
                        '.console-input-wrapper span[class*="button"]',
                        '.console-input-wrapper span[class*="send"]',
                        '.console-form button',
                        '.console-form [type="submit"]',
                        '.console-form .btn',
                        '.console-form div[class*="send"]',
                        '#terminal-send',
                        '.terminal-send',
                        '.terminal-send-btn',
                        'button.btn-send',
                        'button.send',
                        'button.console-input-btn',
                        'button.console-input-button',
                        'button[title*="Send" i]',
                        'button[aria-label*="Send" i]',
                        'button[type="submit"]'
                    ];

                    let sendBtn = null;
                    for (let bSel of sendBtnSelectors) {
                        const btn = document.querySelector(bSel);
                        if (btn) {
                            sendBtn = btn;
                            break;
                        }
                    }

                    if (sendBtn) {
                        sendBtn.click();
                    } else if (input.form) {
                        if (typeof input.form.requestSubmit === 'function') {
                            input.form.requestSubmit();
                        } else {
                            input.form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                        }
                    }

                    // 6. jQuery keydown trigger fallback
                    try {
                        if (window.jQuery || window.$) {
                            const jq = window.jQuery || window.$;
                            const jqEnter = jq.Event('keydown', { which: 13, keyCode: 13, key: 'Enter', code: 'Enter' });
                            jq(input).trigger(jqEnter);
                        }
                    } catch(jqErr2) {}

                    // 7. Verify if input was cleared after 80ms; if not, re-trigger click and enter
                    setTimeout(function() {
                        try {
                            if (input && input.value && input.value.trim().length > 0) {
                                if (sendBtn) {
                                    sendBtn.click();
                                } else if (input.form && typeof input.form.requestSubmit === 'function') {
                                    input.form.requestSubmit();
                                }
                                input.dispatchEvent(new KeyboardEvent('keydown', eventInit));
                                input.dispatchEvent(new KeyboardEvent('keyup', eventInit));
                            }
                        } catch(verifyErr) {}
                    }, 80);

                    return "DISPATCH_OK";
                } catch (e) {
                    console.error("[AternosBridge] Dispatch error:", e);
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            val webView = webViewProvider()
            if (webView != null) {
                webView.evaluateJavascript(script) { resultRaw ->
                    val result = resultRaw?.removeSurrounding("\"") ?: ""
                    Log.d(tag, "Dispatched '$cleanCmd' -> result: $result")
                    if (result.startsWith("INPUT_NOT_FOUND") || result.startsWith("ERROR")) {
                        Log.w(tag, "Command dispatch failed for '$cleanCmd': $result")
                        BridgeEventManager.postDiagnostic("Failed to type command '$cleanCmd' into Aternos console: $result. Make sure the server console page is loaded.")
                    }
                    onResult?.invoke(result)
                }
            } else {
                Log.w(tag, "WebView is null, cannot dispatch '$cleanCmd'")
                BridgeEventManager.postDiagnostic("Cannot dispatch command '$cleanCmd': WebView is not initialized.")
                onResult?.invoke("WEBVIEW_NULL")
            }
        }
    }

    fun evaluateJavascript(script: String, callback: ((String) -> Unit)? = null) {
        mainHandler.post {
            val webView = webViewProvider()
            if (webView != null) {
                webView.evaluateJavascript(script) { result ->
                    callback?.invoke(result)
                }
            } else {
                Log.w(tag, "WebView is null, cannot evaluate javascript.")
            }
        }
    }

    /**
     * Broadcasts a multi-line message to Minecraft players using the configured format template,
     * splitting long text into sub-140 char chunks and pacing transmissions ~400ms apart.
     */
    fun dispatchPacedBroadcast(
        fullText: String,
        targetPlayer: String,
        broadcastFormat: String = "say [AI] {message}"
    ) {
        val lines = CommandSanitizer.splitChatLines(fullText, maxLineLength = 130)
        if (lines.isEmpty()) return

        scope.launch {
            for ((index, line) in lines.withIndex()) {
                val formattedCmd = CommandSanitizer.formatInGameBroadcast(line, targetPlayer, broadcastFormat)
                dispatchCommand(formattedCmd) { dispatchResult ->
                    if (dispatchResult.startsWith("DISPATCH_OK")) {
                        BridgeEventManager.addConsoleLog("> $formattedCmd", level = "AI", isAiAction = true)
                    } else {
                        Log.e(tag, "Broadcast dispatch failed: $formattedCmd ($dispatchResult)")
                        BridgeEventManager.addConsoleLog("[FAILED TO BROADCAST] $formattedCmd: $dispatchResult", level = "ERROR", isSystemNotice = true)
                    }
                }
                if (index < lines.size - 1) {
                    delay(400)
                }
            }
        }
    }
}
