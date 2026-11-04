package com.aternos.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aternos.bridge.MainActivity
import com.aternos.bridge.R

/**
 * Helper for creating notification channels and building foreground service & alert notifications.
 */
class ServiceNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                context.getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.service_notification_channel_desc)
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                context.getString(R.string.alert_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alert_notification_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildForegroundNotification(isPaused: Boolean): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AternosBridgeService::class.java).apply {
            action = AternosBridgeService.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePauseIntent = Intent(context, AternosBridgeService::class.java).apply {
            action = AternosBridgeService.ACTION_TOGGLE_PAUSE
        }
        val togglePausePendingIntent = PendingIntent.getService(
            context,
            2,
            togglePauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) "Bridge Paused" else "Active — Watching console"
        val toggleText = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("Aternos AI Bridge")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                toggleText,
                togglePausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    fun showCloudflareAlert() {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setContentTitle("⚠️ Cloudflare Verification Required")
            .setContentText("Aternos needs verification. Tap to complete the challenge.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ALERT_ID, notification)
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "aternos_bridge_channel"
        const val CHANNEL_ALERT_ID = "aternos_alerts_channel"
        const val NOTIFICATION_SERVICE_ID = 1001
        const val NOTIFICATION_ALERT_ID = 1002
    }
}
