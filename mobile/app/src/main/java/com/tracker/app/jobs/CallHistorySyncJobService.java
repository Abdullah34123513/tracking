package com.tracker.app.jobs;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;

import com.tracker.app.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * JobScheduler service for hourly call history synchronization.
 * Reads native call logs from the last hour and uploads to the server.
 */
public class CallHistorySyncJobService extends JobService {

    private static final String TAG = "CallHistorySync";
    private static final int JOB_ID = 2001;

    /**
     * Schedule hourly call history sync.
     */
    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, CallHistorySyncJobService.class))
                .setPeriodic(60 * 60 * 1000) // 1 hour
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();

        scheduler.schedule(jobInfo);
        Log.d(TAG, "Call history sync job scheduled (hourly)");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                syncCallHistory();
            } catch (Exception e) {
                Log.e(TAG, "Call history sync error", e);
            }
            jobFinished(params, false);
        }).start();
        return true; // Work is being done on a background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // Reschedule if stopped prematurely
    }

    private void syncCallHistory() {
        SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
        String apiToken = prefs.getString("api_token", null);
        if (apiToken == null) return;

        try {
            JSONArray calls = new JSONArray();
            long oneHourAgo = System.currentTimeMillis() - 3600000;

            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.CACHED_NAME,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.DURATION,
                            CallLog.Calls.DATE
                    },
                    CallLog.Calls.DATE + " > ?",
                    new String[]{String.valueOf(oneHourAgo)},
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

                while (cursor.moveToNext()) {
                    String type;
                    switch (cursor.getInt(2)) {
                        case CallLog.Calls.INCOMING_TYPE: type = "incoming"; break;
                        case CallLog.Calls.OUTGOING_TYPE: type = "outgoing"; break;
                        case CallLog.Calls.MISSED_TYPE: type = "missed"; break;
                        default: type = "unknown"; break;
                    }

                    JSONObject call = new JSONObject();
                    call.put("phone_number", cursor.getString(0));
                    call.put("contact_name", cursor.getString(1));
                    call.put("call_type", type);
                    call.put("source", "phone");
                    call.put("duration", cursor.getInt(3));
                    call.put("call_date", sdf.format(new Date(cursor.getLong(4))));
                    calls.put(call);
                }
                cursor.close();
            }

            if (calls.length() > 0) {
                ApiClient.uploadCallHistory(apiToken, calls);
                Log.d(TAG, "Synced " + calls.length() + " call logs");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading call logs", e);
        }
    }
}
