package com.aternos.bridge.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages a bounded Partial WakeLock with periodic renewal to keep CPU active for background
 * console WebSocket monitoring without triggering Android battery abuse flags.
 */
class WakeLockManager(private val context: Context) {

    private val tag = "WakeLockManager"
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewalJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val lockDurationMs = 10 * 60 * 1000L // 10 minutes
    private val renewalIntervalMs = 8 * 60 * 1000L // Renew every 8 minutes

    @Synchronized
    fun start() {
        if (wakeLock != null) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AternosAI::ServiceWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(lockDurationMs)
        }
        Log.i(tag, "Bounded partial wake lock acquired (timeout: 10m).")

        // Start periodic bounded renewal loop
        renewalJob = scope.launch {
            while (isActive) {
                delay(renewalIntervalMs)
                synchronized(this@WakeLockManager) {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.acquire(lockDurationMs)
                        Log.d(tag, "Bounded wake lock renewed for another 10 minutes.")
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        renewalJob?.cancel()
        renewalJob = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(tag, "Partial wake lock released.")
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing wake lock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }
}
