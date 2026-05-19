package com.tracker.app.jobs;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.util.Log;

import com.tracker.app.net.ApiClient;

/**
 * JobScheduler service for periodic heartbeat (every 15 minutes).
 * Reports battery level and current GPS location to the server.
 */
public class HeartbeatJobService extends JobService {

    private static final String TAG = "HeartbeatJob";
    private static final int JOB_ID = 2002;

    /**
     * Schedule heartbeat every 15 minutes.
     */
    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, HeartbeatJobService.class))
                .setPeriodic(15 * 60 * 1000) // 15 minutes
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();

        scheduler.schedule(jobInfo);
        Log.d(TAG, "Heartbeat job scheduled (every 15 min)");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat error", e);
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @SuppressWarnings("MissingPermission")
    private void sendHeartbeat() {
        SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
        String apiToken = prefs.getString("api_token", null);
        if (apiToken == null) return;

        // Get battery level
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        // Get location
        double lat = 0, lng = 0;
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
            }
        } catch (Exception e) {
            Log.e(TAG, "Location error: " + e.getMessage());
        }

        ApiClient.sendHeartbeat(apiToken, battery, lat, lng);
        Log.d(TAG, "Heartbeat sent. Battery: " + battery + "%, Location: " + lat + "," + lng);
    }
}
