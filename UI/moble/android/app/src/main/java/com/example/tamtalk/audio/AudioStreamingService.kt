package com.example.tamtalk.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.tamtalk.R

class AudioStreamingService : Service() {
    companion object {
        private const val channelId = "tamtalk_stream"
        private const val channelName = "TamTalk Streaming"
        private const val notificationId = 1001

        const val actionStart = "com.example.tamtalk.action.START_STREAM"
        const val actionStop = "com.example.tamtalk.action.STOP_STREAM"
        const val actionUpdateVolume = "com.example.tamtalk.action.UPDATE_VOLUME"
        const val actionUpdateTransmitMode = "com.example.tamtalk.action.UPDATE_TRANSMIT_MODE"

        const val extraHost = "extra_host"
        const val extraPort = "extra_port"
        const val extraClientId = "extra_client_id"
        const val extraKeepAliveWhileScreenOff = "extra_keep_alive_while_screen_off"
        const val extraVolumeGain = "extra_volume_gain"
        const val extraAlwaysActive = "extra_always_active"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var sender: AudioUdpSender? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            actionStart -> startStream(intent)
            actionStop -> stopStreamAndService()
            actionUpdateVolume -> updateVolume(intent)
            actionUpdateTransmitMode -> updateTransmitMode(intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSender()
        super.onDestroy()
    }

    private fun startStream(intent: Intent) {
        val host = intent.getStringExtra(extraHost)?.trim().orEmpty()
        val port = intent.getIntExtra(extraPort, 0)
        val clientId = intent.getStringExtra(extraClientId)?.trim().orEmpty()
        val keepAliveWhileScreenOff = intent.getBooleanExtra(extraKeepAliveWhileScreenOff, false)
        val volumeGain = intent.getFloatExtra(extraVolumeGain, 1.0f).coerceIn(0.0f, 2.0f)
        val alwaysActive = intent.getBooleanExtra(extraAlwaysActive, true)

        if (host.isEmpty() || clientId.isEmpty() || port !in 1..65535) {
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(notificationId, buildNotification(host, port, clientId))

        stopSender()
        if (keepAliveWhileScreenOff)
            acquireWakeLock()

        val active = AudioUdpSender(
            hostIp = host,
            port = port,
            clientId = clientId,
            inputGain = volumeGain,
            initialTransmitEnabled = alwaysActive
        )
        sender = active
        active.start {
            stopStreamAndService()
        }
        isRunning = true
    }

    private fun stopStreamAndService() {
        stopSender()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSender() {
        sender?.stop()
        sender = null
        releaseWakeLock()
        isRunning = false
    }

    private fun updateVolume(intent: Intent) {
        val volumeGain = intent.getFloatExtra(extraVolumeGain, 1.0f).coerceIn(0.0f, 2.0f)
        sender?.setInputGain(volumeGain)
    }

    private fun updateTransmitMode(intent: Intent) {
        val alwaysActive = intent.getBooleanExtra(extraAlwaysActive, true)
        sender?.setTransmitEnabled(alwaysActive)
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(PowerManager::class.java)
        val existing = wakeLock
        if (existing != null && existing.isHeld)
            return

        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TamTalk:StreamingWakelock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        if (lock != null && lock.isHeld)
            lock.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) {
            return
        }

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(host: String, port: Int, clientId: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.tamtalk_icon)
            .setContentTitle("TamTalk active")
            .setContentText("Streaming $clientId to $host:$port")
            .setOngoing(true)
            .build()
    }
}
