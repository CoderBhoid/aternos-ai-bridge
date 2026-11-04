package com.aternos.bridge.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Custom WebViewClient that orchestrates Aternos login flow, post-login navigation,
 * server list auto-selection, automatic injection of console_hook.js, and anti-bot stealth spoofing.
 */
class AternosConsoleClient(
    private val context: Context,
    private val onConsolePageReached: () -> Unit = {}
) : WebViewClient() {

    private val tag = "AternosConsoleClient"

    companion object {
        /**
         * Standard real Chrome Mobile User Agent to bypass bot-detection restrictions.
         */
        const val CHROME_MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.102 Mobile Safari/537.36"

        /**
         * Complete stealth JavaScript injected into all HTML heads,
         * document start scripts, and page events.
         */
        val STEALTH_HOOK_JS = """
            (function() {
                try {
                    // 1. Conceal navigator.webdriver
                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                    
                    // 2. Emulate standard Chrome window object
                    if (!window.chrome) {
                        window.chrome = {
                            app: { isInstalled: false },
                            runtime: { PlatformOs: { ANDROID: "android" } },
                            loadTimes: function() {},
                            csi: function() {}
                        };
                    }

                    // 3. Define navigator.languages
                    Object.defineProperty(navigator, 'languages', {
                        get: () => ['en-US', 'en']
                    });

                    // 4. Emulate standard navigator.plugins
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => {
                            const dummyPlugins = [
                                { name: "Chrome PDF Plugin", filename: "internal-pdf-viewer", description: "Portable Document Format" },
                                { name: "Chrome PDF Viewer", filename: "mhjfbmdgcfjbbpaeojofohoefgiehjai", description: "" },
                                { name: "Native Client", filename: "internal-nacl-plugin", description: "" }
                            ];
                            dummyPlugins.item = (i) => dummyPlugins[i];
                            dummyPlugins.namedItem = (name) => dummyPlugins.find(p => p.name === name);
                            dummyPlugins.refresh = () => {};
                            return dummyPlugins;
                        }
                    });

                    // 5. Auto-dismiss error alert overlays and adblock replacement continue buttons
                    setInterval(function() {
                        try {
                            // Auto dismiss error / block overlays
                            document.querySelectorAll('.alert-danger, .modal-danger, .error-alert, .modal').forEach(function(el) {
                                if (el.textContent && (
                                    el.textContent.includes('451') || 
                                    el.textContent.includes('not authorized by Aternos') || 
                                    el.textContent.includes('Adblocker detected') || 
                                    el.textContent.includes('Adblock')
                                )) {
                                    el.style.display = 'none';
                                }
                            });

                            // Auto click adblock replacement continue buttons if present
                            const continueButtons = document.querySelectorAll('.btn-continue, .continue-button, [class*="continue"], .btn-action, .adblock-continue, .btn.btn-main');
                            continueButtons.forEach(function(btn) {
                                if (btn && btn.textContent && (btn.textContent.toLowerCase().includes('continue') || btn.textContent.toLowerCase().includes('proceed'))) {
                                    btn.click();
                                }
                            });
                        } catch(e) {}
                    }, 400);
                } catch (e) {
                    console.error("[AternosBridge] Stealth hook error:", e);
                }
            })();
        """.trimIndent()

        /**
         * Configures modern WebView settings with spoofed Chrome Mobile User-Agent,
         * multi-window support for OAuth/popups, and native suppression of the 'X-Requested-With' header.
         */
        @SuppressLint("SetJavaScriptEnabled")
        fun applyAntiBotWebSettings(webView: WebView) {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = CHROME_MOBILE_USER_AGENT
                cacheMode = WebSettings.LOAD_DEFAULT
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
                    Log.i("AternosConsoleClient", "X-Requested-With header successfully suppressed via WebSettingsCompat.")
                }
            } catch (e: Throwable) {
                Log.w("AternosConsoleClient", "Could not set requested with header allowlist: ${e.message}")
            }

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(webView, STEALTH_HOOK_JS, setOf("*"))
                    Log.i("AternosConsoleClient", "Document start stealth script attached successfully.")
                }
            } catch (e: Throwable) {
                Log.w("AternosConsoleClient", "Could not attach document start script: ${e.message}")
            }
        }
    }

    private val hookScript: String by lazy {
        try {
            val stream = context.assets.open("console_hook.js")
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load console_hook.js from assets", e)
            ""
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        // Normal navigation and AJAX requests must be handled by Chromium's native network stack
        // to guarantee proper TLS fingerprinting, HTTP/2 multiplexing, and Cloudflare Turnstile token validation.
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        Log.d(tag, "shouldOverrideUrlLoading: $url")
        BridgeEventManager.setCurrentUrl(url)

        // Check for Cloudflare challenge URL
        if (url.contains("challenges.cloudflare.com") || url.contains("challenge-platform")) {
            Log.w(tag, "Navigating to Cloudflare challenge page")
            BridgeEventManager.setCloudflareDetected(true)
        }

        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val current = url ?: ""
        Log.i(tag, "onPageStarted: $current")
        BridgeEventManager.setCurrentUrl(current)

        if (current.contains("challenges.cloudflare.com")) {
            BridgeEventManager.setCloudflareDetected(true)
        }

        // Inject stealth script early
        injectStealthJs(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val current = url ?: return
        Log.i(tag, "onPageFinished: $current")
        BridgeEventManager.setCurrentUrl(current)

        // Inject stealth script
        injectStealthJs(view)

        // 1. Post-login Servers List Handling (https://aternos.org/servers/)
        if (current.contains("aternos.org/servers")) {
            handleServersListPage(view)
        }

        // 2. Server Console Page Hook Injection (https://aternos.org/server/.../console or /console)
        if (current.contains("/console") || current.contains("server/")) {
            injectConsoleHook(view)
            onConsolePageReached()
        }
    }

    private fun injectStealthJs(view: WebView?) {
        view?.evaluateJavascript(STEALTH_HOOK_JS, null)
    }

    private fun handleServersListPage(view: WebView?) {
        val serverListScanner = """
            (function() {
                try {
                    const serverCards = document.querySelectorAll('.server-body, .server-card, .serverbox, .servers .server');
                    const servers = [];
                    serverCards.forEach((card, idx) => {
                        const id = card.getAttribute('data-id') || card.id || ('server_' + idx);
                        const nameEl = card.querySelector('.server-name, .name, .header');
                        const addrEl = card.querySelector('.server-ip, .ip, .address');
                        const statusEl = card.querySelector('.server-status, .status');
                        const softEl = card.querySelector('.server-software, .software');
                        
                        servers.push({
                            id: id,
                            name: nameEl ? nameEl.textContent.trim() : 'Minecraft Server',
                            address: addrEl ? addrEl.textContent.trim() : '',
                            status: statusEl ? statusEl.textContent.trim() : '',
                            software: softEl ? softEl.textContent.trim() : ''
                        });
                    });
                    
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onServersDiscovered(JSON.stringify(servers));
                    }
                    
                    // If exactly one server exists, automatically click or navigate to it
                    if (serverCards.length === 1) {
                        console.log("[AternosBridge] Exactly one server found, auto-navigating...");
                        const singleCard = serverCards[0];
                        const link = singleCard.querySelector('a') || singleCard;
                        if (link && link.click) {
                            link.click();
                        }
                    }
                } catch (e) {
                    console.error("[AternosBridge] Error scanning server list:", e);
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(serverListScanner, null)
    }

    private fun injectConsoleHook(view: WebView?) {
        if (hookScript.isBlank()) {
            Log.e(tag, "Hook script is empty, cannot inject.")
            return
        }

        Log.i(tag, "Injecting console_hook.js into WebView...")
        view?.evaluateJavascript(hookScript) { result ->
            Log.d(tag, "Hook injection result: $result")
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val desc = error?.description?.toString() ?: "Unknown error"
            Log.e(tag, "Main frame WebView error: $desc")
            BridgeEventManager.postDiagnostic("Network/WebView error: $desc")
        }
    }
}
