import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ApiService {
  // Your live server URL
  static const String baseUrl = 'https://api.abdullahsourcing.com/api/v1';
  static const _storage = FlutterSecureStorage();

  /// Register device with backend and store the API token
  static Future<bool> registerDevice({
    required String deviceName,
    required String deviceId,
    String? model,
    String? androidVersion,
    String? fcmToken,
  }) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/devices/register'),
        headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
        body: jsonEncode({
          'device_name': deviceName,
          'device_id': deviceId,
          'model': model,
          'android_version': androidVersion,
          'fcm_token': fcmToken,
        }),
      );

      if (response.statusCode == 201) {
        final data = jsonDecode(response.body);
        final apiToken = data['data']['api_token'];

        // Store token securely
        await _storage.write(key: 'api_token', value: apiToken);
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('api_token', apiToken);
        await prefs.setInt('device_db_id', data['data']['device_id']);

        return true;
      }
      return false;
    } catch (e) {
      print('Registration error: $e');
      return false;
    }
  }

  /// Get stored API token
  static Future<String?> getToken() async {
    return await _storage.read(key: 'api_token');
  }

  /// Upload a single screenshot (spacebar trigger)
  static Future<bool> uploadScreenshot({
    required File imageFile,
    required String triggerType,
    String? callSessionId,
    double? latitude,
    double? longitude,
  }) async {
    try {
      final token = await getToken();
      if (token == null) return false;

      final request = http.MultipartRequest(
        'POST',
        Uri.parse('$baseUrl/sync/screenshot'),
      );

      request.headers['Authorization'] = 'Bearer $token';
      request.headers['Accept'] = 'application/json';

      request.files.add(await http.MultipartFile.fromPath('file', imageFile.path));
      request.fields['trigger_type'] = triggerType;
      request.fields['captured_at'] = DateTime.now().toIso8601String();

      if (callSessionId != null) request.fields['call_session_id'] = callSessionId;
      if (latitude != null) request.fields['latitude'] = latitude.toString();
      if (longitude != null) request.fields['longitude'] = longitude.toString();

      final response = await request.send();
      return response.statusCode == 200;
    } catch (e) {
      print('Upload screenshot error: $e');
      return false;
    }
  }

  /// Upload batch of screenshots (post-call)
  static Future<bool> uploadBatchScreenshots({
    required List<File> files,
    required String triggerType,
    required String callSessionId,
    required List<DateTime> capturedAts,
  }) async {
    try {
      final token = await getToken();
      if (token == null) return false;

      // Upload one by one to avoid huge multipart payloads
      for (int i = 0; i < files.length; i++) {
        await uploadScreenshot(
          imageFile: files[i],
          triggerType: triggerType,
          callSessionId: callSessionId,
        );
      }
      return true;
    } catch (e) {
      print('Batch upload error: $e');
      return false;
    }
  }

  /// Upload call history (hourly sync)
  static Future<bool> uploadCallHistory(List<Map<String, dynamic>> calls) async {
    try {
      final token = await getToken();
      if (token == null) return false;

      final response = await http.post(
        Uri.parse('$baseUrl/sync/call-history'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode({'calls': calls}),
      );

      return response.statusCode == 200;
    } catch (e) {
      print('Call history upload error: $e');
      return false;
    }
  }

  /// Upload realtime payload (admin FCM trigger response)
  static Future<bool> uploadRealtimePayload({
    File? screenshot,
    double? latitude,
    double? longitude,
  }) async {
    try {
      final token = await getToken();
      if (token == null) return false;

      final request = http.MultipartRequest(
        'POST',
        Uri.parse('$baseUrl/sync/realtime-payload'),
      );

      request.headers['Authorization'] = 'Bearer $token';
      request.headers['Accept'] = 'application/json';

      if (screenshot != null) {
        request.files.add(await http.MultipartFile.fromPath('screenshot', screenshot.path));
      }
      if (latitude != null) request.fields['latitude'] = latitude.toString();
      if (longitude != null) request.fields['longitude'] = longitude.toString();

      final response = await request.send();
      return response.statusCode == 200;
    } catch (e) {
      print('Realtime payload error: $e');
      return false;
    }
  }

  /// Update FCM token on server
  static Future<bool> updateFcmToken(String fcmToken) async {
    try {
      final token = await getToken();
      if (token == null) return false;

      final response = await http.post(
        Uri.parse('$baseUrl/devices/update-fcm'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode({'fcm_token': fcmToken}),
      );

      return response.statusCode == 200;
    } catch (e) {
      print('FCM token update error: $e');
      return false;
    }
  }

  /// Send heartbeat
  static Future<void> sendHeartbeat({int? battery, double? lat, double? lng}) async {
    try {
      final token = await getToken();
      if (token == null) return;

      await http.post(
        Uri.parse('$baseUrl/devices/heartbeat'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode({
          'battery_level': battery,
          'latitude': lat,
          'longitude': lng,
        }),
      );
    } catch (e) {
      print('Heartbeat error: $e');
    }
  }
}
