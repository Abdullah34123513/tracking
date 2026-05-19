package com.tracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.tracker.app.services.MonitoringForegroundService;
import com.tracker.app.jobs.CallHistorySyncJobService;
import com.tracker.app.jobs.HeartbeatJobService;
import com.tracker.app.jobs.UploadJobService;

/**
 * Automatically restarts monitoring services after device reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed - restarting services");

            // Check if device is registered
            String token = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
                    .getString("api_token", null);
            if (token == null) return;

            // Start foreground service
            Intent serviceIntent = new Intent(context, MonitoringForegroundService.class);
            context.startForegroundService(serviceIntent);

            // Reschedule jobs
            CallHistorySyncJobService.schedule(context);
            HeartbeatJobService.schedule(context);
            UploadJobService.schedule(context);
        }
    }
}
