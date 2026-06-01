package com.meetingminutes.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.meetingminutes.app.R

class RecordingForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "会议录音", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> {
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, notification())
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground service alive when the user opens Recents while recording.
        super.onTaskRemoved(rootIntent)
    }

    private fun notification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("会议库正在录音")
            .setContentText("锁屏或切到其他应用也会继续录音。回到会议库可停止并生成纪要。")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeetingMinutes:Recording").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "meeting_recording"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.meetingminutes.app.STOP_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
