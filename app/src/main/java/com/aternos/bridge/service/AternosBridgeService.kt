package com.aternos.bridge.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.WebView
import androidx.core.app.ServiceCompat
import com.aternos.bridge.ai.AiOrchestrator
import com.aternos.bridge.bridge.AternosConsoleClient
import com.aternos.bridge.bridge.AternosNativeBridge
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.bridge.CommandDispatcher
import com.aternos.bridge.bridge.WebViewHolder
import com.aternos.bridge.data.EncryptedConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service hosting the background Aternos WebView and WebSocket keep-alive engine.
 */
class AternosBridgeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var notificationHelper: ServiceNotificationHelper
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var configRepository: EncryptedConfigRepository
    private lateinit var commandDispatcher: CommandDispatcher
    private lateinit var aiOrchestrator: AiOrchestrator
    private lateinit var nativeBridge: AternosNativeBridge

    val webView: WebView?
        get() = WebViewHolder.getWebView()

    private var keepAliveJob: Job? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AternosBridgeService = this@AternosBridgeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating AternosBridgeService...")

        notificationHelper = ServiceNotificationHelper(this)
        wakeLockManager = WakeLockManager(this)
        configRepository = EncryptedConfigRepository.getInstance(this)
        aiOrchestrator = AiOrchestrator()
        commandDispatcher = CommandDispatcher(webViewProvider = { WebViewHolder.getWebView() }, scope = serviceScope)
        nativeBridge = AternosNativeBridge(this, commandDispatcher, aiOrchestrator, configRepository, serviceScope)

        startForegroundServiceWithNotification()
        wakeLockManager.start()
        initBackgroundWebView()
        startKeepAliveLoop()
        observeBridgeState()
    }

    private fun startForegroundServiceWithNotification() {
        val notification = notificationHelper.buildForegroundNotification(
            isPaused = BridgeEventManager.isBridgePaused.value
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationHelper.NOTIFICATION_SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ServiceNotificationHelper.NOTIFICATION_SERVICE_ID, notification)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initBackgroundWebView() {
        try {
            val consoleClient = AternosConsoleClient(applicationContext) {
                Log.i(TAG, "Console page reached, background bridge active!")
            }
            WebViewHolder.getOrCreateWebView(this, nativeBridge, consoleClient)
            Log.i(TAG, "Canonical WebView initialized and attached to service.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing canonical WebView in service", e)
        }
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                val intervalSec = configRepository.getConfig().keepAliveIntervalSeconds.coerceAtLeast(15)
                delay(intervalSec * 1000L)

                // Dispatch synthetic focus and visibility events to keep Aternos WebSocket stream active
                withContext(Dispatchers.Main) {
                    WebViewHolder.getWebView()?.let { wv ->
                        val script = """
                            (function() {
                                try {
                                    window.dispatchEvent(new Event('focus'));
                                    document.dispatchEvent(new Event('visibilitychange'));
                                    return "KEEP_ALIVE_OK";
                                } catch (e) {
                                    return "ERR: " + e.message;
                                }
                            })();
                        """.trimIndent()
                        wv.evaluateJavascript(script, null)
                    }
                }
            }
        }
    }

    private fun observeBridgeState() {
        serviceScope.launch {
            BridgeEventManager.isBridgePaused.collectLatest { isPaused ->
                val notification = notificationHelper.buildForegroundNotification(isPaused)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(ServiceNotificationHelper.NOTIFICATION_SERVICE_ID, notification)
            }
        }

        serviceScope.launch {
            BridgeEventManager.isCloudflareDetected.collectLatest { detected ->
                if (detected) {
                    notificationHelper.showCloudflareAlert()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stopping AternosBridgeService via intent action...")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                val current = BridgeEventManager.isBridgePaused.value
                BridgeEventManager.setBridgePaused(!current)
                Log.i(TAG, "Bridge pause toggled to ${!current}")
            }
            ACTION_DISPATCH_MANUAL_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND)
                if (!cmd.isNullOrBlank()) {
                    commandDispatcher.dispatchCommand(cmd) { result ->
                        if (result.startsWith("DISPATCH_OK")) {
                            BridgeEventManager.addConsoleLog("> $cmd", level = "USER", isAiAction = false)
                        } else {
                            BridgeEventManager.addConsoleLog("[FAILED TO TYPE '$cmd'] $result", level = "ERROR", isSystemNotice = true)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroying AternosBridgeService...")
        keepAliveJob?.cancel()
        serviceScope.cancel()
        wakeLockManager.stop()
        WebViewHolder.destroy()

        BridgeEventManager.setHookAttached(false)
        BridgeEventManager.setServerStatus(com.aternos.bridge.data.ServerStatus.OFFLINE)
    }

    companion object {
        private const val TAG = "AternosBridgeService"
        const val ACTION_START_SERVICE = "com.aternos.bridge.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.aternos.bridge.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.aternos.bridge.ACTION_TOGGLE_PAUSE"
        const val ACTION_DISPATCH_MANUAL_COMMAND = "com.aternos.bridge.ACTION_DISPATCH_COMMAND"
        const val EXTRA_COMMAND = "extra_command"

        fun start(context: Context) {
            val intent = Intent(context, AternosBridgeService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AternosBridgeService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
