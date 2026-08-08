package com.shellbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.shellbox.MainActivity
import com.shellbox.ssh.SshManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Optional foreground service that keeps ShellBox's SSH connections (and their
 * port forwards) alive while the app is backgrounded or the screen is off.
 *
 * This is opt-in: the user must explicitly enable "后台保活" in Settings before
 * this service is ever started (see TerminalSettingsStore.keepAliveServiceEnabled
 * and its wiring in SettingsScreen / MainActivity). Without it, Android is free to
 * tear down the process's network sockets shortly after the app leaves the
 * foreground, which is the standard, expected behavior for a background app —
 * we only override it when the user asks us to, and we're transparent about it
 * via a persistent, mandatory notification (required by Android for foreground
 * services and not something ShellBox can hide).
 *
 * Holds a partial wake lock while at least one SSH session is active so the CPU
 * doesn't sleep mid-transfer / mid-keepalive; releases it as soon as the last
 * session disconnects, and stops itself entirely once there's nothing left to keep alive.
 */
@AndroidEntryPoint
class SshKeepAliveService : Service() {

    @Inject lateinit var sshManager: SshManager

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sshManager.disconnectAll()
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(sessionCount = sshManager.sessions.value.size))

        // Track active session count to (a) keep the notification text accurate and
        // (b) release everything and stop the service once the last session closes,
        // rather than lingering as an empty foreground service.
        sshManager.sessions
            .onEach { sessions ->
                if (sessions.isEmpty()) {
                    stopSelfCleanly()
                } else {
                    acquireWakeLockIfNeeded()
                    updateNotification(sessions.size)
                }
            }
            .launchIn(scope)

        return START_STICKY
    }

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShellBox:SshKeepAlive").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS) // safety timeout — renewed implicitly on next sessions emission
        }
    }

    private fun stopSelfCleanly() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(sessionCount: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SshKeepAliveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShellBox 连接保持中")
            .setContentText(sessionText(sessionCount))
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "断开全部并停止", stopIntent)
            .build()
    }

    private fun updateNotification(sessionCount: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(sessionCount))
    }

    private fun sessionText(count: Int) = "当前保持 $count 个 SSH 连接在后台运行"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "SSH 后台保活", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 ShellBox 的 SSH 连接在应用切到后台后继续运行"
        }
        nm.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away from Recents. Respect that as an implicit
        // "I'm done" signal for the underlying connections rather than silently
        // continuing to hold them — this matches what users expect and avoids
        // stranding sockets/wake locks indefinitely.
        sshManager.disconnectAll()
        stopSelfCleanly()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL_ID = "ssh_keep_alive"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 min safety cap, renewed on each sessions update
        const val ACTION_STOP = "com.shellbox.action.STOP_KEEP_ALIVE"

        fun start(context: Context) {
            val intent = Intent(context, SshKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshKeepAliveService::class.java))
        }
    }
}
