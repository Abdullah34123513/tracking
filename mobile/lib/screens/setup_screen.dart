import 'package:flutter/material.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/api_service.dart';

class SetupScreen extends StatefulWidget {
  final VoidCallback onSetupComplete;

  const SetupScreen({super.key, required this.onSetupComplete});

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> {
  bool _isLoading = false;
  String _status = 'Ready to setup';
  int _step = 0;
  final int _totalSteps = 4;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Colors.grey[900]!, Colors.black],
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(32.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.shield,
                  size: 72,
                  color: Colors.blue[400],
                ),
                const SizedBox(height: 24),
                const Text(
                  'Device Setup',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'One-time configuration',
                  style: TextStyle(fontSize: 16, color: Colors.grey[500]),
                ),
                const SizedBox(height: 48),

                // Progress
                if (_isLoading || _step > 0) ...[
                  LinearProgressIndicator(
                    value: _step / _totalSteps,
                    backgroundColor: Colors.grey[800],
                    color: Colors.blue[400],
                  ),
                  const SizedBox(height: 16),
                ],

                // Status
                Text(
                  _status,
                  style: TextStyle(fontSize: 14, color: Colors.grey[400]),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),

                // Setup button
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _startSetup,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blue[600],
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            width: 24,
                            height: 24,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text(
                            'Start Setup',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                          ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _startSetup() async {
    setState(() {
      _isLoading = true;
      _step = 0;
    });

    try {
      // Step 1: Request permissions
      setState(() {
        _step = 1;
        _status = 'Requesting permissions...';
      });
      await _requestPermissions();

      // Step 2: Get device info
      setState(() {
        _step = 2;
        _status = 'Gathering device info...';
      });
      final deviceInfo = await _getDeviceInfo();

      // Step 3: Get FCM token
      setState(() {
        _step = 3;
        _status = 'Setting up push notifications...';
      });
      final fcmToken = await FirebaseMessaging.instance.getToken();

      // Step 4: Register with backend
      setState(() {
        _step = 4;
        _status = 'Registering device with server...';
      });
      final success = await ApiService.registerDevice(
        deviceName: deviceInfo['name'] ?? 'Android Device',
        deviceId: deviceInfo['id'] ?? 'unknown',
        model: deviceInfo['model'],
        androidVersion: deviceInfo['version'],
        fcmToken: fcmToken,
      );

      if (success) {
        setState(() => _status = 'Setup complete! Starting services...');
        await Future.delayed(const Duration(seconds: 1));
        widget.onSetupComplete();
      } else {
        setState(() {
          _status = 'Registration failed. Check server URL and try again.';
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _status = 'Error: ${e.toString()}';
        _isLoading = false;
      });
    }
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.phone,
      Permission.location,
      Permission.storage,
      Permission.notification,
    ].request();
  }

  Future<Map<String, String>> _getDeviceInfo() async {
    final deviceInfo = DeviceInfoPlugin();
    final androidInfo = await deviceInfo.androidInfo;
    return {
      'name': '${androidInfo.brand} ${androidInfo.model}',
      'id': androidInfo.id,
      'model': androidInfo.model,
      'version': androidInfo.version.release,
    };
  }
}
