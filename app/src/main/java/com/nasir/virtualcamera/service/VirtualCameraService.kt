package com.nasir.virtualcamera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nasir.virtualcamera.MainActivity
import com.nasir.virtualcamera.R
import timber.log.Timber

class VirtualCameraService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: String? = null
    private val binder = LocalBinder()
    private var isRunning = false

    inner class LocalBinder : Binder() {
        fun getService(): VirtualCameraService = this@VirtualCameraService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("VirtualCameraService created")
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
                    ?: intent.getStringExtra(EXTRA_VIDEO_PATH)
                Timber.d("Starting virtual camera with video: $videoUri")
                startVirtualCamera()
                START_STICKY
            }
            ACTION_STOP -> {
                Timber.d("Stopping virtual camera service")
                stopVirtualCamera()
                stopSelf()
                START_STICKY
            }
            else -> START_STICKY
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Virtual Camera Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays virtual camera streaming status"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            Timber.d("Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Virtual Camera")
            .setContentText("Streaming video as camera feed...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(false)
            .build()
    }

    private fun startVirtualCamera() {
        try {
            if (isRunning) {
                Timber.w("Virtual camera already running")
                return
            }

            startForeground(NOTIFICATION_ID, createNotification())

            videoUri?.let { uri ->
                mediaPlayer = MediaPlayer().apply {
                    try {
                        setDataSource(this@VirtualCameraService, Uri.parse(uri))
                        setOnPreparedListener { mp ->
                            try {
                                mp.start()
                                mp.isLooping = true
                                isRunning = true
                                Timber.d("Virtual camera started, video looping enabled")
                            } catch (e: Exception) {
                                Timber.e(e, "Error starting playback")
                            }
                        }
                        setOnErrorListener { mp, what, extra ->
                            Timber.e("MediaPlayer error: what=$what, extra=$extra")
                            false
                        }
                        setOnCompletionListener { mp ->
                            Timber.d("Video completed")
                            if (!mp.isLooping) {
                                mp.seekTo(0)
                                mp.start()
                            }
                        }
                        prepareAsync()
                    } catch (e: Exception) {
                        Timber.e(e, "Error setting up MediaPlayer")
                        isRunning = false
                    }
                }
            } ?: run {
                Timber.e("Video URI is null")
                isRunning = false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting virtual camera")
            isRunning = false
        }
    }

    private fun stopVirtualCamera() {
        try {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                    release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing MediaPlayer")
                }
            }
            mediaPlayer = null
            isRunning = false
            Timber.d("Virtual camera stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping virtual camera")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVirtualCamera()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Timber.d("VirtualCameraService destroyed")
    }

    companion object {
        private const val TAG = "VirtualCameraService"
        private const val CHANNEL_ID = "virtual_camera_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.nasir.virtualcamera.ACTION_START"
        const val ACTION_STOP = "com.nasir.virtualcamera.ACTION_STOP"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_PATH = "video_path"
    }
}
