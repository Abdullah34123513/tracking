import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'api_service.dart';

/// Periodically drains the native UploadQueue and uploads screenshots to the server.
class UploadProcessor {
  static const platform = MethodChannel('com.tracker.app/services');
  static Timer? _timer;

  /// Start polling the native queue every 10 seconds
  static void start() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 10), (_) => _processQueue());
  }

  static void stop() {
    _timer?.cancel();
    _timer = null;
  }

  static Future<void> _processQueue() async {
    try {
      final result = await platform.invokeMethod('getPendingUploads');
      if (result == null || result is! List || result.isEmpty) return;

      for (final item in result) {
        final map = Map<String, dynamic>.from(item as Map);
        final filePath = map['filePath'] as String;
        final triggerType = map['triggerType'] as String;
        final callSessionId = map['callSessionId'] as String?;

        final file = File(filePath);
        if (!await file.exists()) continue;

        await ApiService.uploadScreenshot(
          imageFile: file,
          triggerType: triggerType,
          callSessionId: callSessionId,
        );

        // Clean up temp file after upload
        try { await file.delete(); } catch (_) {}
      }
    } catch (e) {
      print('Upload processor error: $e');
    }
  }
}
