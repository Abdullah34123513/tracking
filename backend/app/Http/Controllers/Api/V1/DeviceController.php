<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\Device;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class DeviceController extends Controller
{
    /**
     * Register a new device or re-register an existing one.
     */
    public function register(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'device_name' => 'required|string|max:255',
            'device_id' => 'required|string|max:255',
            'model' => 'nullable|string|max:255',
            'android_version' => 'nullable|string|max:50',
            'fcm_token' => 'nullable|string',
        ]);

        // Enforce that one username (device_name) can be registered only once
        $exists = Device::where('device_name', $validated['device_name'])->exists();
        if ($exists) {
            return response()->json([
                'success' => false,
                'message' => 'Username already taken. Please choose another.',
            ], 422);
        }

        $apiToken = Str::random(64);

        $device = Device::create([
            'device_name' => $validated['device_name'],
            'device_id' => $validated['device_id'],
            'model' => $validated['model'] ?? null,
            'android_version' => $validated['android_version'] ?? null,
            'fcm_token' => $validated['fcm_token'] ?? null,
            'api_token' => $apiToken,
            'status' => 'online',
            'last_seen_at' => now(),
        ]);

        return response()->json([
            'success' => true,
            'message' => 'Device registered successfully.',
            'data' => [
                'device_id' => $device->id,
                'api_token' => $apiToken,
            ],
        ], 201);
    }

    /**
     * Update FCM token for the device.
     */
    public function updateFcmToken(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'fcm_token' => 'required|string',
        ]);

        $request->device->update([
            'fcm_token' => $validated['fcm_token'],
        ]);

        return response()->json(['success' => true, 'message' => 'FCM token updated.']);
    }

    /**
     * Heartbeat - update device status, battery, location.
     */
    public function heartbeat(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'battery_level' => 'nullable|integer|min:0|max:100',
            'latitude' => 'nullable|numeric',
            'longitude' => 'nullable|numeric',
        ]);

        $request->device->update([
            'battery_level' => $validated['battery_level'] ?? $request->device->battery_level,
            'last_lat' => $validated['latitude'] ?? $request->device->last_lat,
            'last_lng' => $validated['longitude'] ?? $request->device->last_lng,
            'status' => 'online',
            'last_seen_at' => now(),
        ]);

        return response()->json(['success' => true, 'message' => 'Heartbeat received.']);
    }
}
