package com.aternos.bridge.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Singleton holder that manages the single canonical [WebView] instance across the entire application lifecycle.
 */
object WebViewHolder {

    private const val TAG = "WebViewHolder"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var webViewInstance: WebView? = null

    /**
     * Retrieves the existing [WebView] or initializes a new one on the Main Looper.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        context: Context,
        nativeBridge: AternosNativeBridge? = null,
        consoleClient: AternosConsoleClient? = null
    ): WebView {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var wv: WebView? = null
            mainHandler.post {
                wv = getOrCreateWebView(context, nativeBridge, consoleClient)
            }
            return webViewInstance ?: WebView(context.applicationContext)
        }

        webViewInstance?.let { existingWv ->
            nativeBridge?.let {
                existingWv.addJavascriptInterface(it, "AndroidBridge")
            }
            consoleClient?.let {
                existingWv.webViewClient = it
            }
            return existingWv
        }

        val appContext = context.applicationContext
        val newWv = WebView(appContext).apply {
            AternosConsoleClient.applyAntiBotWebSettings(this)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(true)
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            CookieManager.getInstance().let { cm ->
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
            }

            nativeBridge?.let {
                addJavascriptInterface(it, "AndroidBridge")
            }

            webViewClient = consoleClient ?: AternosConsoleClient(appContext) {
                Log.i(TAG, "Console page reached on canonical WebView!")
            }

            loadUrl("https://aternos.org/go/")
        }

        webViewInstance = newWv
        Log.i(TAG, "Canonical WebView created and initialized.")
        return newWv
    }

    fun getWebView(): WebView? = webViewInstance

    fun configure(nativeBridge: AternosNativeBridge, consoleClient: AternosConsoleClient) {
        mainHandler.post {
            webViewInstance?.let { wv ->
                wv.addJavascriptInterface(nativeBridge, "AndroidBridge")
                wv.webViewClient = consoleClient
            }
        }
    }

    fun attachToContainer(container: ViewGroup) {
        mainHandler.post {
            webViewInstance?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                container.addView(
                    wv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                Log.d(TAG, "Canonical WebView attached to container.")
            }
        }
    }

    fun detachFromParent() {
        mainHandler.post {
            webViewInstance?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                Log.d(TAG, "Canonical WebView detached from parent.")
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            webViewInstance?.let { wv ->
                try {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.removeJavascriptInterface("AndroidBridge")
                    wv.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying WebView: ${e.message}")
                }
            }
            webViewInstance = null
            Log.i(TAG, "Canonical WebView destroyed.")
        }
    }
}
