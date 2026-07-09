package com.tvremote.app.data.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun

/**
 * Keeps the TV remote TLS session alive while the app is in the background.
 * Notification reflects live connection state (connected / reconnecting / pairing).
 */
class RemoteConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                SafeRun.run(TAG) {
                    (application as? TvRemoteApp)?.container?.tvRemoteRepository?.disconnectUser()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                SafeRun.run(TAG) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        SafeRun.run(TAG) {
            val payload = intent?.toPayload() ?: latestPayload ?: fallbackPayload()
            latestPayload = payload
            try {
                if (intent?.action == ACTION_START || !isForeground) {
                    startForegroundCompat(payload)
                } else {
                    updateNotification(payload)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Foreground notification failed — stopping service", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@run
            }
            if (intent?.action == ACTION_START) {
                (application as? TvRemoteApp)?.container?.tvRemoteRepository?.maintainBackgroundConnection()
            }
        }
        return START_STICKY
    }

    private var isForeground = false

    private fun startForegroundCompat(payload: RemoteConnectionNotification) {
        val notification = buildNotification(payload)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun updateNotification(payload: RemoteConnectionNotification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(payload))
    }

    private fun buildNotification(payload: RemoteConnectionNotification): Notification {
        createChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainLauncherIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = payload.label()
        val title = when (payload.status) {
            RemoteConnectionStatus.CONNECTED -> getString(R.string.remote_notification_connected, label)
            RemoteConnectionStatus.RECONNECTING -> getString(R.string.remote_notification_reconnecting_to, label)
            RemoteConnectionStatus.PAIRING -> getString(R.string.remote_notification_pairing_with, label)
            RemoteConnectionStatus.DISCONNECTED -> getString(R.string.remote_notification_disconnected_from, label)
        }
        val text = when (payload.status) {
            RemoteConnectionStatus.CONNECTED -> getString(R.string.remote_notification_text)
            RemoteConnectionStatus.RECONNECTING -> getString(R.string.remote_notification_reconnecting_text)
            RemoteConnectionStatus.PAIRING -> getString(R.string.remote_notification_pairing_text)
            RemoteConnectionStatus.DISCONNECTED -> getString(R.string.remote_notification_disconnected_text)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentIntent(pendingIntent)
            .setOngoing(payload.status != RemoteConnectionStatus.DISCONNECTED)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (payload.status != RemoteConnectionStatus.DISCONNECTED) {
            builder.addAction(
                R.drawable.ic_cross,
                getString(R.string.remote_notification_disconnect),
                disconnectPendingIntent(),
            )
        }
        return builder.build()
    }

    private fun disconnectPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            REQUEST_DISCONNECT,
            Intent(this, RemoteDisconnectReceiver::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun fallbackPayload(): RemoteConnectionNotification {
        return RemoteConnectionNotification(
            active = true,
            host = "tv",
            displayName = getString(R.string.connected_tv_default),
            status = RemoteConnectionStatus.RECONNECTING,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.remote_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.remote_notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun Intent.toPayload(): RemoteConnectionNotification? {
        val host = getStringExtra(EXTRA_TV_HOST).orEmpty()
        val name = getStringExtra(EXTRA_TV_NAME).orEmpty()
        val statusName = getStringExtra(EXTRA_STATUS).orEmpty()
        val status = runCatching {
            RemoteConnectionStatus.valueOf(statusName)
        }.getOrDefault(RemoteConnectionStatus.RECONNECTING)
        val active = getBooleanExtra(EXTRA_ACTIVE, true)
        if (host.isBlank() && name.isBlank()) return null
        return RemoteConnectionNotification(
            active = active,
            host = host,
            displayName = name.ifBlank { host },
            status = status,
        )
    }

    private fun mainLauncherIntent(): Intent {
        return packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setClassName(packageName, MAIN_ACTIVITY_CLASS)
    }

    companion object {
        private const val TAG = "RemoteConnectionService"
        const val ACTION_START = "com.tvremote.app.action.START_REMOTE_KEEPALIVE"
        const val ACTION_STOP = "com.tvremote.app.action.STOP_REMOTE_KEEPALIVE"
        const val ACTION_UPDATE = "com.tvremote.app.action.UPDATE_REMOTE_KEEPALIVE"
        const val ACTION_DISCONNECT = "com.tvremote.app.action.DISCONNECT_REMOTE"
        const val EXTRA_TV_HOST = "tv_host"
        const val EXTRA_TV_NAME = "tv_name"
        const val EXTRA_STATUS = "status"
        const val EXTRA_ACTIVE = "active"
        private const val CHANNEL_ID = "tv_remote_connection"
        private const val NOTIFICATION_ID = 1002
        private const val REQUEST_DISCONNECT = 1003
        private const val MAIN_ACTIVITY_CLASS = "com.tvremote.app.ui.main.MainActivity"

        @Volatile
        private var latestPayload: RemoteConnectionNotification? = null

        fun sync(context: Context, payload: RemoteConnectionNotification) {
            SafeRun.run(TAG) {
                latestPayload = payload
                if (!payload.active || payload.status == RemoteConnectionStatus.DISCONNECTED) {
                    stop(context)
                    return@run
                }
                val intent = Intent(context, RemoteConnectionService::class.java).apply {
                    action = if (isRunning(context)) ACTION_UPDATE else ACTION_START
                    putExtra(EXTRA_TV_HOST, payload.host)
                    putExtra(EXTRA_TV_NAME, payload.displayName)
                    putExtra(EXTRA_STATUS, payload.status.name)
                    putExtra(EXTRA_ACTIVE, payload.active)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Could not start foreground service — remote stays in-app only", e)
                }
            }
        }

        fun stop(context: Context) {
            SafeRun.run(TAG) {
                latestPayload = null
                context.startService(
                    Intent(context, RemoteConnectionService::class.java).apply {
                        action = ACTION_STOP
                    },
                )
            }
        }

        private fun isRunning(context: Context): Boolean = latestPayload?.active == true
    }
}
