package com.tracker.app.jobs;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.tracker.app.net.ApiClient;
import com.tracker.app.services.UploadQueue;

import java.util.List;

/**
 * JobScheduler service that periodically drains the UploadQueue
 * and uploads pending screenshots to the server.
 * Runs every 15 minutes, but also triggered manually when items are queued.
 */
public class UploadJobService extends JobService {

    private static final String TAG = "UploadJob";
    private static final int JOB_ID = 2003;

    /**
     * Schedule periodic upload processing.
     */
    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, UploadJobService.class))
                .setPeriodic(15 * 60 * 1000) // 15 minutes
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();

        scheduler.schedule(jobInfo);
        Log.d(TAG, "Upload job scheduled (every 15 min)");
    }

    /**
     * Trigger an immediate upload run (non-periodic).
     */
    public static void triggerNow(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        // Use a different job ID for one-shot immediate upload
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID + 100, new ComponentName(context, UploadJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(1000) // Run within 1 second
                .build();

        scheduler.schedule(jobInfo);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                processQueue();
            } catch (Exception e) {
                Log.e(TAG, "Upload processing error", e);
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void processQueue() {
        SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
        String apiToken = prefs.getString("api_token", null);
        if (apiToken == null) return;

        List<UploadQueue.UploadItem> items = UploadQueue.drain();
        if (items.isEmpty()) return;

        Log.d(TAG, "Processing " + items.size() + " queued uploads");

        for (UploadQueue.UploadItem item : items) {
            if (!item.file.exists()) continue;

            boolean success = ApiClient.uploadScreenshot(
                    apiToken,
                    item.file,
                    item.triggerType,
                    item.callSessionId,
                    0, 0 // Location not tracked per-screenshot
            );

            if (success) {
                // Clean up temp file after successful upload
                item.file.delete();
                Log.d(TAG, "Uploaded and deleted: " + item.file.getName());
            } else {
                // Re-queue for retry
                Log.w(TAG, "Upload failed, will retry: " + item.file.getName());
                UploadQueue.requeue(item);
            }
        }
    }
}
